package com.github.matheuscruzsouza.androidserver.s3;

import com.github.matheuscruzsouza.androidserver.model.IamUser;
import com.github.matheuscruzsouza.androidserver.service.IamService;
import com.github.matheuscruzsouza.androidserver.storage.FileUtils;
import com.github.matheuscruzsouza.androidserver.storage.StorageManager;
import com.github.matheuscruzsouza.nanospring.http.HttpStatus;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Despachante central para requisições S3 do S4 Server.
 * Gerencia roteamento nativo na raiz (/ e /{bucket}/{key}) e preserva o alias legado (/s3/*).
 * Garante proteção por padrão (buckets privados exigem autenticação) e suporte a HEAD.
 */
public class S3RequestDispatcher {

    private static final Set<String> RESERVED_PREFIXES = new HashSet<>(Arrays.asList(
            "api", "web", "static", "health", "auth", "admin", "v3", "swagger-ui.html", "favicon.ico"
    ));

    private final StorageManager storageManager;
    private final IamService iamService;
    private final MultipartUploadManager multipartManager;

    public S3RequestDispatcher(StorageManager storageManager, IamService iamService) {
        this.storageManager = storageManager;
        this.iamService = iamService;
        this.multipartManager = storageManager != null ? new MultipartUploadManager(storageManager.getRootDir()) : null;
    }

    /**
     * Determina se o caminho da requisição pertence ao protocolo S3.
     */
    public boolean isS3Request(IHTTPSession session) {
        if (session == null || session.getUri() == null) {
            return false;
        }
        String uri = session.getUri();

        // 1. Alias legado /s3 e /s3/*
        if (uri.equals("/s3") || uri.startsWith("/s3/")) {
            return true;
        }

        // 2. Raiz pura "/" pertence ao IndexController (UI web), a menos que tenha query S3 ou método HEAD
        if (uri.equals("/")) {
            return false;
        }

        // 3. Extrai o primeiro segmento do caminho
        String path = uri.startsWith("/") ? uri.substring(1) : uri;
        int firstSlash = path.indexOf('/');
        String firstSegment = firstSlash >= 0 ? path.substring(0, firstSlash) : path;

        // Se for um prefixo reservado da aplicação S4, não é S3 nativo
        if (RESERVED_PREFIXES.contains(firstSegment.toLowerCase())) {
            return false;
        }

        return true;
    }

    /**
     * Despacha e processa a requisição S3.
     */
    public Response dispatch(IHTTPSession session) {
        String uri = session.getUri();
        NanoHTTPD.Method method = session.getMethod();

        // Normalização do caminho (remove alias legado /s3 se presente)
        String s3Path = uri;
        if (s3Path.startsWith("/s3")) {
            s3Path = s3Path.substring(3);
            if (!s3Path.startsWith("/")) {
                s3Path = "/" + s3Path;
            }
        }

        if (s3Path.startsWith("/")) {
            s3Path = s3Path.substring(1);
        }

        // Trata requisições OPTIONS (CORS preflight S3)
        if (method == NanoHTTPD.Method.OPTIONS) {
            Response corsResponse = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/plain", "");
            applyCorsHeaders(corsResponse, session);
            return corsResponse;
        }

        // Trata requisições HEAD
        if (method == NanoHTTPD.Method.HEAD) {
            Response headRes = handleHead(session, s3Path);
            if (headRes != null) applyCorsHeaders(headRes, session);
            return headRes;
        }

        // Operações em nível de Bucket ou Objeto
        int slashIdx = s3Path.indexOf('/');
        boolean isBucketOnly = slashIdx < 0;
        String bucket = isBucketOnly ? s3Path : s3Path.substring(0, slashIdx);
        String key = !isBucketOnly && slashIdx + 1 < s3Path.length() ? s3Path.substring(slashIdx + 1) : null;

        Map<String, String> parms = session.getParms() != null ? session.getParms() : new HashMap<>();

        // Trata requisições POST
        if (method == NanoHTTPD.Method.POST) {
            if (isBucketOnly && parms.containsKey("delete")) {
                Response res = handleDeleteObjects(session, bucket);
                if (res != null) applyCorsHeaders(res, session);
                return res;
            } else if (key != null && parms.containsKey("uploads")) {
                Response res = handleInitiateMultipartUpload(session, bucket, key);
                if (res != null) applyCorsHeaders(res, session);
                return res;
            } else if (key != null && parms.containsKey("uploadId")) {
                Response res = handleCompleteMultipartUpload(session, bucket, key, parms.get("uploadId"));
                if (res != null) applyCorsHeaders(res, session);
                return res;
            }
        }

        // Trata requisições GET
        if (method == NanoHTTPD.Method.GET) {
            if (isBucketOnly) {
                Response res = handleListObjects(session, bucket);
                if (res != null) applyCorsHeaders(res, session);
                return res;
            } else if (key != null) {
                Response res = handleGetObject(session, bucket, key);
                if (res != null) applyCorsHeaders(res, session);
                return res;
            }
        }

        // Trata requisições PUT
        if (method == NanoHTTPD.Method.PUT) {
            if (isBucketOnly) {
                Response res = handleCreateBucket(session, bucket);
                if (res != null) applyCorsHeaders(res, session);
                return res;
            } else if (key != null) {
                if (parms.containsKey("uploadId") && parms.containsKey("partNumber")) {
                    Response res = handleUploadPart(session, bucket, key, parms.get("uploadId"), parms.get("partNumber"));
                    if (res != null) applyCorsHeaders(res, session);
                    return res;
                }
                Response res = handlePutObject(session, bucket, key);
                if (res != null) applyCorsHeaders(res, session);
                return res;
            }
        }

        // Trata requisições DELETE
        if (method == NanoHTTPD.Method.DELETE) {
            if (isBucketOnly) {
                Response res = handleDeleteBucket(session, bucket);
                if (res != null) applyCorsHeaders(res, session);
                return res;
            } else if (key != null) {
                if (parms.containsKey("uploadId")) {
                    Response res = handleAbortMultipartUpload(session, bucket, key, parms.get("uploadId"));
                    if (res != null) applyCorsHeaders(res, session);
                    return res;
                }
                Response res = handleDeleteObject(session, bucket, key);
                if (res != null) applyCorsHeaders(res, session);
                return res;
            }
        }

        return null;
    }

    /**
     * POST /{bucket}?delete (DeleteObjects em lote).
     */
    public Response handleDeleteObjects(IHTTPSession session, String bucket) {
        IamUser user = iamService != null ? iamService.authenticate(session) : null;
        if (user == null) {
            return createErrorResponse(HttpStatus.FORBIDDEN, "AccessDenied", "Access Denied - Authentication required.", "/" + bucket);
        }

        String userDefaultBucket = storageManager != null ? storageManager.getUserDefaultBucket(user) : null;
        if (!iamService.canPerformInBucket(user, "s3:DeleteObject", bucket, userDefaultBucket)) {
            return createErrorResponse(HttpStatus.FORBIDDEN, "AccessDenied", "Access Denied", "/" + bucket);
        }

        if (storageManager == null || !storageManager.bucketExists(bucket)) {
            return createErrorResponse(HttpStatus.NOT_FOUND, "NoSuchBucket", "The specified bucket does not exist.", "/" + bucket);
        }

        try {
            Map<String, String> bodyFiles = new HashMap<>();
            session.parseBody(bodyFiles);
            String postData = bodyFiles.get("postData");
            if (postData == null && bodyFiles.containsKey("content")) {
                postData = bodyFiles.get("content");
            }

            List<String> deletedKeys = new ArrayList<>();
            if (postData != null) {
                Pattern p = Pattern.compile("<Key>(.*?)</Key>");
                Matcher m = p.matcher(postData);
                while (m.find()) {
                    String k = m.group(1).trim();
                    File f = storageManager.resolveFile(bucket, k);
                    if (f != null && f.exists()) {
                        f.delete();
                    }
                    deletedKeys.add(k);
                }
            }

            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            xml.append("<DeleteResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">");
            for (String k : deletedKeys) {
                xml.append("<Deleted><Key>").append(escapeXml(k)).append("</Key></Deleted>");
            }
            xml.append("</DeleteResult>");

            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/xml", xml.toString());
        } catch (Exception e) {
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "InternalError", e.getMessage(), "/" + bucket);
        }
    }

    /**
     * POST /{bucket}/{key}?uploads (CreateMultipartUpload).
     */
    public Response handleInitiateMultipartUpload(IHTTPSession session, String bucket, String key) {
        IamUser user = iamService != null ? iamService.authenticate(session) : null;
        if (user == null) {
            return createErrorResponse(HttpStatus.FORBIDDEN, "AccessDenied", "Access Denied - Authentication required.", "/" + bucket + "/" + key);
        }

        String userDefaultBucket = storageManager != null ? storageManager.getUserDefaultBucket(user) : null;
        if (!iamService.canPerformInBucket(user, "s3:PutObject", bucket, userDefaultBucket)) {
            return createErrorResponse(HttpStatus.FORBIDDEN, "AccessDenied", "Access Denied", "/" + bucket + "/" + key);
        }

        if (storageManager == null || !storageManager.bucketExists(bucket)) {
            return createErrorResponse(HttpStatus.NOT_FOUND, "NoSuchBucket", "The specified bucket does not exist.", "/" + bucket);
        }

        String uploadId = multipartManager.initiateMultipartUpload(bucket, key);

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<InitiateMultipartUploadResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">");
        xml.append("<Bucket>").append(escapeXml(bucket)).append("</Bucket>");
        xml.append("<Key>").append(escapeXml(key)).append("</Key>");
        xml.append("<UploadId>").append(escapeXml(uploadId)).append("</UploadId>");
        xml.append("</InitiateMultipartUploadResult>");

        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/xml", xml.toString());
    }

    /**
     * PUT /{bucket}/{key}?uploadId=...&partNumber=... (UploadPart).
     */
    public Response handleUploadPart(IHTTPSession session, String bucket, String key, String uploadId, String partNumberStr) {
        IamUser user = iamService != null ? iamService.authenticate(session) : null;
        if (user == null) {
            return createErrorResponse(HttpStatus.FORBIDDEN, "AccessDenied", "Access Denied - Authentication required.", "/" + bucket + "/" + key);
        }

        String userDefaultBucket = storageManager != null ? storageManager.getUserDefaultBucket(user) : null;
        if (!iamService.canPerformInBucket(user, "s3:PutObject", bucket, userDefaultBucket)) {
            return createErrorResponse(HttpStatus.FORBIDDEN, "AccessDenied", "Access Denied", "/" + bucket + "/" + key);
        }

        int partNumber;
        try {
            partNumber = Integer.parseInt(partNumberStr);
        } catch (Exception e) {
            return createErrorResponse(HttpStatus.BAD_REQUEST, "InvalidArgument", "Invalid partNumber.", "/" + bucket + "/" + key);
        }

        Map<String, String> headers = session.getHeaders();
        long contentLength = 0;
        if (headers != null && headers.containsKey("content-length")) {
            try {
                contentLength = Long.parseLong(headers.get("content-length"));
            } catch (Exception ignored) {}
        }

        try {
            String etag = multipartManager.savePart(bucket, uploadId, partNumber, session.getInputStream(), contentLength);
            Response res = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/plain", "");
            res.addHeader("ETag", "\"" + etag + "\"");
            return res;
        } catch (Exception e) {
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "InternalError", e.getMessage(), "/" + bucket + "/" + key);
        }
    }

    /**
     * POST /{bucket}/{key}?uploadId=... (CompleteMultipartUpload).
     */
    public Response handleCompleteMultipartUpload(IHTTPSession session, String bucket, String key, String uploadId) {
        IamUser user = iamService != null ? iamService.authenticate(session) : null;
        if (user == null) {
            return createErrorResponse(HttpStatus.FORBIDDEN, "AccessDenied", "Access Denied - Authentication required.", "/" + bucket + "/" + key);
        }

        String userDefaultBucket = storageManager != null ? storageManager.getUserDefaultBucket(user) : null;
        if (!iamService.canPerformInBucket(user, "s3:PutObject", bucket, userDefaultBucket)) {
            return createErrorResponse(HttpStatus.FORBIDDEN, "AccessDenied", "Access Denied", "/" + bucket + "/" + key);
        }

        try {
            File dest = storageManager.resolveFile(bucket, key);
            String finalEtag = multipartManager.completeMultipartUpload(bucket, key, uploadId, dest);

            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            xml.append("<CompleteMultipartUploadResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">");
            xml.append("<Location>").append("/").append(escapeXml(bucket)).append("/").append(escapeXml(key)).append("</Location>");
            xml.append("<Bucket>").append(escapeXml(bucket)).append("</Bucket>");
            xml.append("<Key>").append(escapeXml(key)).append("</Key>");
            xml.append("<ETag>\"").append(finalEtag).append("\"</ETag>");
            xml.append("</CompleteMultipartUploadResult>");

            Response res = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/xml", xml.toString());
            res.addHeader("ETag", "\"" + finalEtag + "\"");
            return res;
        } catch (Exception e) {
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "InternalError", e.getMessage(), "/" + bucket + "/" + key);
        }
    }

    /**
     * DELETE /{bucket}/{key}?uploadId=... (AbortMultipartUpload).
     */
    public Response handleAbortMultipartUpload(IHTTPSession session, String bucket, String key, String uploadId) {
        IamUser user = iamService != null ? iamService.authenticate(session) : null;
        if (user == null) {
            return createErrorResponse(HttpStatus.FORBIDDEN, "AccessDenied", "Access Denied - Authentication required.", "/" + bucket + "/" + key);
        }

        String userDefaultBucket = storageManager != null ? storageManager.getUserDefaultBucket(user) : null;
        if (!iamService.canPerformInBucket(user, "s3:PutObject", bucket, userDefaultBucket)) {
            return createErrorResponse(HttpStatus.FORBIDDEN, "AccessDenied", "Access Denied", "/" + bucket + "/" + key);
        }

        multipartManager.abortMultipartUpload(bucket, uploadId);
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NO_CONTENT, "text/plain", "");
    }

    /**
     * Trata PUT /{bucket} (CreateBucket).
     */
    public Response handleCreateBucket(IHTTPSession session, String bucket) {
        if (bucket == null || bucket.trim().isEmpty()) {
            return createErrorResponse(HttpStatus.BAD_REQUEST, "InvalidBucketName", "The specified bucket is not valid.", "/");
        }

        IamUser user = iamService != null ? iamService.authenticate(session) : null;
        if (user == null) {
            return createErrorResponse(HttpStatus.FORBIDDEN, "AccessDenied", "Access Denied - Authentication required.", "/" + bucket);
        }

        String userDefaultBucket = storageManager != null ? storageManager.getUserDefaultBucket(user) : null;
        if (!iamService.canPerformInBucket(user, "s3:CreateBucket", bucket, userDefaultBucket)) {
            return createErrorResponse(HttpStatus.FORBIDDEN, "AccessDenied", "Access Denied", "/" + bucket);
        }

        if (storageManager == null) {
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "InternalError", "Storage manager unavailable.", "/" + bucket);
        }

        if (storageManager.bucketExists(bucket)) {
            return createErrorResponse(HttpStatus.CONFLICT, "BucketAlreadyOwnedByYou", "Your previous request to create the named bucket succeeded and you already own it.", "/" + bucket);
        }

        boolean created = storageManager.createBucket(bucket);
        if (created) {
            Response res = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/plain", "");
            res.addHeader("Location", "/" + bucket);
            return res;
        } else {
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "InternalError", "Could not create bucket.", "/" + bucket);
        }
    }

    /**
     * Trata DELETE /{bucket} (DeleteBucket).
     */
    public Response handleDeleteBucket(IHTTPSession session, String bucket) {
        IamUser user = iamService != null ? iamService.authenticate(session) : null;
        if (user == null) {
            return createErrorResponse(HttpStatus.FORBIDDEN, "AccessDenied", "Access Denied - Authentication required.", "/" + bucket);
        }

        String userDefaultBucket = storageManager != null ? storageManager.getUserDefaultBucket(user) : null;
        if (!iamService.canPerformInBucket(user, "s3:DeleteBucket", bucket, userDefaultBucket)) {
            return createErrorResponse(HttpStatus.FORBIDDEN, "AccessDenied", "Access Denied", "/" + bucket);
        }

        if (storageManager == null || !storageManager.bucketExists(bucket)) {
            return createErrorResponse(HttpStatus.NOT_FOUND, "NoSuchBucket", "The specified bucket does not exist.", "/" + bucket);
        }

        File bucketDir = storageManager.getBucketDir(bucket);
        File[] files = bucketDir.listFiles();
        if (files != null && files.length > 0) {
            return createErrorResponse(HttpStatus.CONFLICT, "BucketNotEmpty", "The bucket you tried to delete is not empty.", "/" + bucket);
        }

        boolean deleted = storageManager.deleteBucket(bucket);
        if (deleted) {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NO_CONTENT, "text/plain", "");
        } else {
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "InternalError", "Could not delete bucket.", "/" + bucket);
        }
    }

    /**
     * Trata GET /{bucket}/{key} (GetObject) com suporte a streaming e ByteRangeHandler.
     */
    public Response handleGetObject(IHTTPSession session, String bucket, String key) {
        IamUser user = iamService != null ? iamService.authenticate(session) : null;
        if (user == null) {
            return createErrorResponse(HttpStatus.FORBIDDEN, "AccessDenied", "Access Denied - Authentication required.", "/" + bucket + "/" + key);
        }

        String userDefaultBucket = storageManager != null ? storageManager.getUserDefaultBucket(user) : null;
        if (!iamService.canPerformInBucket(user, "s3:GetObject", bucket, userDefaultBucket)) {
            return createErrorResponse(HttpStatus.FORBIDDEN, "AccessDenied", "Access Denied", "/" + bucket + "/" + key);
        }

        if (storageManager == null || !storageManager.bucketExists(bucket)) {
            return createErrorResponse(HttpStatus.NOT_FOUND, "NoSuchBucket", "The specified bucket does not exist.", "/" + bucket);
        }

        File file = storageManager.resolveFile(bucket, key);
        if (file == null || !file.exists() || !file.isFile()) {
            return createErrorResponse(HttpStatus.NOT_FOUND, "NoSuchKey", "The specified key does not exist.", "/" + bucket + "/" + key);
        }

        String mime = FileUtils.getMimeType(file.getName());
        Response res = com.github.matheuscruzsouza.nanospring.storage.ByteRangeHandler.serveFile(session, file, mime);
        res.addHeader("ETag", "\"" + FileUtils.calculateMD5(file) + "\"");
        res.addHeader("Content-Disposition", "inline; filename=\"" + file.getName() + "\"");
        return res;
    }

    /**
     * Trata PUT /{bucket}/{key} (PutObject) com streaming contínuo sem buffer de memória.
     */
    public Response handlePutObject(IHTTPSession session, String bucket, String key) {
        IamUser user = iamService != null ? iamService.authenticate(session) : null;
        if (user == null) {
            return createErrorResponse(HttpStatus.FORBIDDEN, "AccessDenied", "Access Denied - Authentication required.", "/" + bucket + "/" + key);
        }

        String userDefaultBucket = storageManager != null ? storageManager.getUserDefaultBucket(user) : null;
        if (!iamService.canPerformInBucket(user, "s3:PutObject", bucket, userDefaultBucket)) {
            return createErrorResponse(HttpStatus.FORBIDDEN, "AccessDenied", "Access Denied", "/" + bucket + "/" + key);
        }

        if (storageManager == null) {
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "InternalError", "Storage manager unavailable.", "/" + bucket);
        }

        if (!storageManager.bucketExists(bucket)) {
            return createErrorResponse(HttpStatus.NOT_FOUND, "NoSuchBucket", "The specified bucket does not exist.", "/" + bucket);
        }

        try {
            File dest = storageManager.resolveFile(bucket, key);
            File parent = dest.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            java.util.Map<String, String> headers = session.getHeaders();
            long contentLength = 0;
            if (headers != null && headers.containsKey("content-length")) {
                contentLength = Long.parseLong(headers.get("content-length"));
            }

            try (java.io.InputStream in = session.getInputStream();
                 java.io.OutputStream out = new java.io.FileOutputStream(dest)) {
                if (in != null) {
                    FileUtils.copyStream(in, out, contentLength > 0 ? contentLength : Long.MAX_VALUE);
                }
            }

            String etag = FileUtils.calculateMD5(dest);
            Response res = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/plain", "");
            res.addHeader("ETag", "\"" + etag + "\"");
            return res;
        } catch (Exception e) {
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "InternalError", e.getMessage(), "/" + bucket + "/" + key);
        }
    }

    /**
     * Trata DELETE /{bucket}/{key} (DeleteObject).
     */
    public Response handleDeleteObject(IHTTPSession session, String bucket, String key) {
        IamUser user = iamService != null ? iamService.authenticate(session) : null;
        if (user == null) {
            return createErrorResponse(HttpStatus.FORBIDDEN, "AccessDenied", "Access Denied - Authentication required.", "/" + bucket + "/" + key);
        }

        String userDefaultBucket = storageManager != null ? storageManager.getUserDefaultBucket(user) : null;
        if (!iamService.canPerformInBucket(user, "s3:DeleteObject", bucket, userDefaultBucket)) {
            return createErrorResponse(HttpStatus.FORBIDDEN, "AccessDenied", "Access Denied", "/" + bucket + "/" + key);
        }

        if (storageManager == null || !storageManager.bucketExists(bucket)) {
            return createErrorResponse(HttpStatus.NOT_FOUND, "NoSuchBucket", "The specified bucket does not exist.", "/" + bucket);
        }

        File file = storageManager.resolveFile(bucket, key);
        if (file != null && file.exists()) {
            file.delete();
        }

        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NO_CONTENT, "text/plain", "");
    }

    /**
     * Trata GET /{bucket} (ListObjects / ListObjectsV2) com cursor de continuação e streaming de resultados.
     */
    public Response handleListObjects(IHTTPSession session, String bucket) {
        // 1. Verificação de permissões do bucket
        IamUser user = iamService != null ? iamService.authenticate(session) : null;
        if (user == null) {
            return createErrorResponse(HttpStatus.FORBIDDEN, "AccessDenied", "Access Denied - Authentication required.", "/" + bucket);
        }

        String userDefaultBucket = storageManager != null ? storageManager.getUserDefaultBucket(user) : null;
        if (!iamService.canPerformInBucket(user, "s3:ListBucket", bucket, userDefaultBucket)) {
            return createErrorResponse(HttpStatus.FORBIDDEN, "AccessDenied", "Access Denied", "/" + bucket);
        }

        if (storageManager == null || !storageManager.bucketExists(bucket)) {
            return createErrorResponse(HttpStatus.NOT_FOUND, "NoSuchBucket", "The specified bucket does not exist.", "/" + bucket);
        }

        java.util.Map<String, String> parms = session.getParms();
        if (parms == null) parms = new java.util.HashMap<>();

        boolean isV2 = "2".equals(parms.get("list-type"));
        String prefix = parms.containsKey("prefix") ? parms.get("prefix") : "";
        String delimiter = parms.containsKey("delimiter") ? parms.get("delimiter") : "";
        String continuationToken = parms.get("continuation-token");
        String startAfter = isV2 ? parms.get("start-after") : parms.get("marker");

        int maxKeys = 1000;
        if (parms.containsKey("max-keys")) {
            try {
                maxKeys = Math.max(1, Math.min(1000, Integer.parseInt(parms.get("max-keys").trim())));
            } catch (Exception ignored) {}
        }

        // Cursor de início: continua a partir do token ou do startAfter
        String resumeCursor = null;
        if (continuationToken != null && !continuationToken.trim().isEmpty()) {
            resumeCursor = decodeContinuationToken(continuationToken.trim());
        } else if (startAfter != null && !startAfter.trim().isEmpty()) {
            resumeCursor = startAfter.trim();
        }

        File bucketDir = storageManager.getBucketDir(bucket);
        java.util.List<File> allFiles = new java.util.ArrayList<>();
        collectAllFiles(bucketDir, allFiles);

        String bucketPath = bucketDir.getAbsolutePath();
        java.util.TreeSet<String> commonPrefixes = new java.util.TreeSet<>();
        java.util.List<File> matchingFiles = new java.util.ArrayList<>();

        for (File f : allFiles) {
            String rel = f.getAbsolutePath().substring(bucketPath.length());
            if (rel.startsWith("/")) rel = rel.substring(1);

            if (!prefix.isEmpty() && !rel.startsWith(prefix)) {
                continue;
            }

            if (!delimiter.isEmpty()) {
                String sub = rel.substring(prefix.length());
                int idx = sub.indexOf(delimiter);
                if (idx >= 0) {
                    String prefixGroup = prefix + sub.substring(0, idx + delimiter.length());
                    commonPrefixes.add(prefixGroup);
                    continue;
                }
            }

            matchingFiles.add(f);
        }

        // Ordenação alfabética por chave S3
        matchingFiles.sort((f1, f2) -> {
            String r1 = f1.getAbsolutePath().substring(bucketPath.length());
            String r2 = f2.getAbsolutePath().substring(bucketPath.length());
            return r1.compareTo(r2);
        });

        // Aplicação do cursor se especificado
        if (resumeCursor != null && !resumeCursor.isEmpty()) {
            final String finalCursor = resumeCursor;
            matchingFiles.removeIf(f -> {
                String k = f.getAbsolutePath().substring(bucketPath.length());
                if (k.startsWith("/")) k = k.substring(1);
                return k.compareTo(finalCursor) <= 0;
            });
            commonPrefixes.removeIf(cp -> cp.compareTo(finalCursor) <= 0);
        }

        int totalAvailable = matchingFiles.size() + commonPrefixes.size();
        boolean isTruncated = totalAvailable > maxKeys;

        String nextContinuationToken = null;
        if (isTruncated) {
            if (matchingFiles.size() >= maxKeys) {
                File lastFile = matchingFiles.get(maxKeys - 1);
                String lastKey = lastFile.getAbsolutePath().substring(bucketPath.length());
                if (lastKey.startsWith("/")) lastKey = lastKey.substring(1);
                nextContinuationToken = encodeContinuationToken(lastKey);
                matchingFiles = matchingFiles.subList(0, maxKeys);
            } else {
                matchingFiles = matchingFiles.subList(0, Math.min(matchingFiles.size(), maxKeys));
            }
        }

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<ListBucketResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">");
        xml.append("<Name>").append(escapeXml(bucket)).append("</Name>");
        xml.append("<Prefix>").append(escapeXml(prefix)).append("</Prefix>");
        xml.append("<MaxKeys>").append(maxKeys).append("</MaxKeys>");
        if (!delimiter.isEmpty()) {
            xml.append("<Delimiter>").append(escapeXml(delimiter)).append("</Delimiter>");
        }
        xml.append("<IsTruncated>").append(isTruncated).append("</IsTruncated>");

        if (isV2) {
            xml.append("<KeyCount>").append(matchingFiles.size() + commonPrefixes.size()).append("</KeyCount>");
            if (continuationToken != null) {
                xml.append("<ContinuationToken>").append(escapeXml(continuationToken)).append("</ContinuationToken>");
            }
            if (nextContinuationToken != null) {
                xml.append("<NextContinuationToken>").append(escapeXml(nextContinuationToken)).append("</NextContinuationToken>");
            }
            if (startAfter != null && !startAfter.isEmpty()) {
                xml.append("<StartAfter>").append(escapeXml(startAfter)).append("</StartAfter>");
            }
        }

        for (File file : matchingFiles) {
            String key = file.getAbsolutePath().substring(bucketPath.length());
            if (key.startsWith("/")) key = key.substring(1);

            xml.append("<Contents>");
            xml.append("<Key>").append(escapeXml(key)).append("</Key>");
            xml.append("<LastModified>").append(FileUtils.formatIsoDate(file.lastModified())).append("</LastModified>");
            xml.append("<ETag>\"").append(FileUtils.calculateMD5(file)).append("\"</ETag>");
            xml.append("<Size>").append(file.length()).append("</Size>");
            xml.append("<StorageClass>STANDARD</StorageClass>");
            xml.append("</Contents>");
        }

        for (String cp : commonPrefixes) {
            xml.append("<CommonPrefixes>");
            xml.append("<Prefix>").append(escapeXml(cp)).append("</Prefix>");
            xml.append("</CommonPrefixes>");
        }

        xml.append("</ListBucketResult>");

        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/xml", xml.toString());
    }

    private void collectAllFiles(File dir, java.util.List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File c : children) {
            if (c.isDirectory()) {
                collectAllFiles(c, out);
            } else if (c.isFile()) {
                out.add(c);
            }
        }
    }

    private String encodeContinuationToken(String key) {
        try {
            return java.util.Base64.getEncoder().encodeToString(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            return key;
        }
    }

    private String decodeContinuationToken(String token) {
        try {
            return new String(java.util.Base64.getDecoder().decode(token), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return token;
        }
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * Trata HEAD /{bucket} e HEAD /{bucket}/{key}.
     */
    public Response handleHead(IHTTPSession session, String s3Path) {
        if (s3Path == null || s3Path.trim().isEmpty()) {
            return createErrorResponse(HttpStatus.BAD_REQUEST, "InvalidBucketName", "The specified bucket is not valid.", "/");
        }

        int slashIdx = s3Path.indexOf('/');
        String bucket = slashIdx >= 0 ? s3Path.substring(0, slashIdx) : s3Path;
        String key = slashIdx >= 0 && slashIdx + 1 < s3Path.length() ? s3Path.substring(slashIdx + 1) : null;

        // 1. Verificação de Autenticação / Autorização (Proteção por padrão)
        IamUser user = iamService != null ? iamService.authenticate(session) : null;
        String action = (key == null || key.isEmpty()) ? "s3:ListBucket" : "s3:GetObject";

        // Buckets são privados por padrão. Exigem autenticação válida.
        if (user == null) {
            return createErrorResponse(HttpStatus.FORBIDDEN, "AccessDenied", "Access Denied - Authentication required.", "/" + bucket);
        }

        String userDefaultBucket = storageManager != null ? storageManager.getUserDefaultBucket(user) : null;
        if (!iamService.canPerformInBucket(user, action, bucket, userDefaultBucket)) {
            return createErrorResponse(HttpStatus.FORBIDDEN, "AccessDenied", "Access Denied", "/" + bucket);
        }

        // 2. HEAD Bucket
        if (key == null || key.isEmpty()) {
            if (storageManager == null || !storageManager.bucketExists(bucket)) {
                return createErrorResponse(HttpStatus.NOT_FOUND, "NoSuchBucket", "The specified bucket does not exist.", "/" + bucket);
            }
            Response response = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/xml", "");
            response.addHeader("x-amz-bucket-region", "us-east-1");
            return response;
        }

        // 3. HEAD Object
        File file = storageManager != null ? storageManager.resolveFile(bucket, key) : null;
        if (file == null || !file.exists() || !file.isFile()) {
            return createErrorResponse(HttpStatus.NOT_FOUND, "NoSuchKey", "The specified key does not exist.", "/" + bucket + "/" + key);
        }

        String mime = FileUtils.getMimeType(file.getName());
        String etag = "\"" + FileUtils.calculateMD5(file) + "\"";
        String lastModified = FileUtils.formatIsoDate(file.lastModified());

        Response response = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, mime, "");
        response.addHeader("Content-Length", String.valueOf(file.length()));
        response.addHeader("ETag", etag);
        response.addHeader("Last-Modified", lastModified);
        response.addHeader("Accept-Ranges", "bytes");
        return response;
    }

    /**
     * Constrói resposta de erro padrão S3 em XML.
     */
    public Response createErrorResponse(HttpStatus status, String code, String message, String resource) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<Error>");
        xml.append("<Code>").append(code != null ? code : "InternalError").append("</Code>");
        xml.append("<Message>").append(message != null ? message : "An error occurred").append("</Message>");
        if (resource != null) {
            xml.append("<Resource>").append(resource).append("</Resource>");
        }
        xml.append("</Error>");

        NanoHTTPD.Response.IStatus nanoStatus = mapHttpStatus(status);
        return NanoHTTPD.newFixedLengthResponse(nanoStatus, "application/xml", xml.toString());
    }

    private NanoHTTPD.Response.IStatus mapHttpStatus(HttpStatus status) {
        if (status == HttpStatus.NOT_FOUND) {
            return NanoHTTPD.Response.Status.NOT_FOUND;
        } else if (status == HttpStatus.FORBIDDEN) {
            return NanoHTTPD.Response.Status.FORBIDDEN;
        } else if (status == HttpStatus.UNAUTHORIZED) {
            return NanoHTTPD.Response.Status.UNAUTHORIZED;
        } else if (status == HttpStatus.BAD_REQUEST) {
            return NanoHTTPD.Response.Status.BAD_REQUEST;
        } else if (status == HttpStatus.CONFLICT) {
            return NanoHTTPD.Response.Status.CONFLICT;
        } else if (status == HttpStatus.NO_CONTENT) {
            return NanoHTTPD.Response.Status.NO_CONTENT;
        }
        return NanoHTTPD.Response.Status.INTERNAL_ERROR;
    }

    private void applyCorsHeaders(Response response, IHTTPSession session) {
        if (response == null) return;
        Map<String, String> headers = session != null ? session.getHeaders() : null;
        String origin = headers != null ? headers.get("origin") : null;
        if (origin == null && headers != null) origin = headers.get("Origin");

        response.addHeader("Access-Control-Allow-Origin", origin != null && !origin.isEmpty() ? origin : "*");
        response.addHeader("Access-Control-Allow-Methods", "GET, PUT, POST, DELETE, HEAD, OPTIONS");
        response.addHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Content-Length, ETag, Range, X-Amz-Date, X-Amz-Content-Sha256, X-Amz-Security-Token, X-Amz-User-Agent, x-amz-meta-*");
        response.addHeader("Access-Control-Expose-Headers", "ETag, Content-Length, Content-Range, Accept-Ranges, x-amz-bucket-region, x-amz-request-id");
        response.addHeader("Access-Control-Max-Age", "86400");
    }
}
