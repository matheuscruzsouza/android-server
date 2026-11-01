package com.github.matheuscruzsouza.androidserver.controller;

import com.github.matheuscruzsouza.androidserver.storage.FileUtils;
import com.github.matheuscruzsouza.androidserver.storage.StorageManager;
import com.github.matheuscruzsouza.nanospring.annotation.Autowired;
import com.github.matheuscruzsouza.nanospring.annotation.DeleteMethod;
import com.github.matheuscruzsouza.nanospring.annotation.GetMethod;
import com.github.matheuscruzsouza.nanospring.annotation.PathVariable;
import com.github.matheuscruzsouza.nanospring.annotation.PutMethod;
import com.github.matheuscruzsouza.nanospring.annotation.RequestParam;
import com.github.matheuscruzsouza.nanospring.annotation.UploadedFile;
import com.github.matheuscruzsouza.nanospring.annotation.RestController;

import com.github.matheuscruzsouza.nanospring.openapi.annotation.Operation;
import com.github.matheuscruzsouza.nanospring.openapi.annotation.Parameter;
import com.github.matheuscruzsouza.nanospring.openapi.annotation.Tag;
import com.github.matheuscruzsouza.nanospring.storage.ByteRangeHandler;
import com.github.matheuscruzsouza.nanospring.http.HttpStatus;
import com.github.matheuscruzsouza.nanospring.http.ResponseEntity;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

@Tag(name = "Amazon S3 API", description = "Endpoints compatíveis com a especificação AWS S3 para gerenciamento de buckets e objetos")
@RestController("/s3")
public class S3ApiController {

    @Autowired
    private StorageManager storageManager;

    public S3ApiController() {
    }

    public S3ApiController(StorageManager storageManager) {
        this.storageManager = storageManager;
    }

    private String extractKey(String bucket, IHTTPSession session, String pathKey) {
        if (session != null) {
            String uri = session.getUri();
            String prefix = "/s3/" + bucket + "/";
            if (uri.startsWith(prefix) && uri.length() > prefix.length()) {
                return uri.substring(prefix.length());
            }
        }
        return pathKey;
    }

    private ResponseEntity<String> errorResponse(HttpStatus status, String code, String message, String resource) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<Error>");
        xml.append("<Code>").append(code != null ? code : "InternalError").append("</Code>");
        xml.append("<Message>").append(message != null ? message : "An error occurred").append("</Message>");
        if (resource != null) {
            xml.append("<Resource>").append(resource).append("</Resource>");
        }
        xml.append("</Error>");
        return ResponseEntity.status(status).header("Content-Type", "application/xml").body(xml.toString());
    }

    // S3: HeadBucket (HEAD /s3/:bucket)
    @Operation(summary = "Verificar existência do bucket (HEAD)", description = "Verifica se o bucket especificado existe e se o cliente tem permissões de acesso")
    @GetMethod("/:bucket/head-status")
    public Object headBucketDocs(@PathVariable("bucket") String bucket) {
        if (!storageManager.bucketExists(bucket)) {
            return errorResponse(HttpStatus.NOT_FOUND, "NoSuchBucket", "The specified bucket does not exist.", "/" + bucket);
        }
        return ResponseEntity.ok().header("x-amz-bucket-region", "us-east-1").body("");
    }

    // S3: CreateBucket (PUT /s3/:bucket)
    @Operation(summary = "Criar bucket S3", description = "Cria um novo bucket de armazenamento no servidor S4")
    @PutMethod("/:bucket")
    public Object createBucket(@PathVariable("bucket") String bucket) {
        if (bucket == null || bucket.trim().isEmpty()) {
            return errorResponse(HttpStatus.BAD_REQUEST, "InvalidBucketName", "The specified bucket is not valid.", bucket);
        }
        if (storageManager.bucketExists(bucket)) {
            return errorResponse(HttpStatus.CONFLICT, "BucketAlreadyOwnedByYou", "Your previous request to create the named bucket succeeded and you already own it.", "/" + bucket);
        }
        boolean created = storageManager.createBucket(bucket);
        if (created) {
            return ResponseEntity.ok().header("Location", "/" + bucket).body("");
        } else {
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "InternalError", "Could not create bucket.", "/" + bucket);
        }
    }

    // S3: DeleteBucket (DELETE /s3/:bucket)
    @Operation(summary = "Excluir bucket S3", description = "Exclui um bucket vazio do armazenamento S4")
    @DeleteMethod("/:bucket")
    public Object deleteBucket(@PathVariable("bucket") String bucket) {
        if (!storageManager.bucketExists(bucket)) {
            return errorResponse(HttpStatus.NOT_FOUND, "NoSuchBucket", "The specified bucket does not exist.", "/" + bucket);
        }
        File bucketDir = storageManager.getBucketDir(bucket);
        File[] files = bucketDir.listFiles();
        if (files != null && files.length > 0) {
            return errorResponse(HttpStatus.CONFLICT, "BucketNotEmpty", "The bucket you tried to delete is not empty.", "/" + bucket);
        }
        boolean deleted = storageManager.deleteBucket(bucket);
        if (deleted) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body("");
        } else {
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "InternalError", "Could not delete bucket.", "/" + bucket);
        }
    }
    @Operation(summary = "Listar buckets S3", description = "Retorna lista em XML de todos os buckets do servidor (ListAllMyBuckets)")
    @GetMethod("")
    public Object listBuckets() {
        File[] buckets = storageManager.getRootDir().listFiles(File::isDirectory);
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<ListAllMyBucketsResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">");
        xml.append("<Buckets>");
        if (buckets != null) {
            for (File b : buckets) {
                xml.append("<Bucket>");
                xml.append("<Name>").append(b.getName()).append("</Name>");
                xml.append("<CreationDate>").append(FileUtils.formatIsoDate(b.lastModified())).append("</CreationDate>");
                xml.append("</Bucket>");
            }
        }
        xml.append("</Buckets>");
        xml.append("</ListAllMyBucketsResult>");

        return ResponseEntity.ok().header("Content-Type", "application/xml").body(xml.toString());
    }

    // S3: ListObjects / ListObjectsV2 (GET /s3/:bucket)
    @Operation(summary = "Listar objetos do bucket (ListObjectsV2)", description = "Lista arquivos e prefixos de um bucket S3 com suporte a paginação por cursor (continuation-token) e delimitadores")
    @GetMethod("/:bucket")
    public Object listObjects(@PathVariable("bucket") String bucket,
                                @Parameter(name = "prefix", description = "Filtro de prefixo para chaves de objetos")
                                @RequestParam("prefix") String prefix,
                                @Parameter(name = "delimiter", description = "Delimitador para agrupamento de pastas (geralmente '/')")
                                @RequestParam("delimiter") String delimiter,
                                @Parameter(name = "max-keys", description = "Número máximo de chaves retornadas (padrão 1000)")
                                @RequestParam("max-keys") String maxKeysParam,
                                @Parameter(name = "list-type", description = "Versão do protocolo de listagem ('2' para ListObjectsV2)")
                                @RequestParam("list-type") String listType,
                                @Parameter(name = "continuation-token", description = "Token opaco para paginação continuada")
                                @RequestParam("continuation-token") String continuationToken,
                                @Parameter(name = "start-after", description = "Inicia a listagem após a chave especificada")
                                @RequestParam("start-after") String startAfter,
                                IHTTPSession session) {
        if (!storageManager.bucketExists(bucket)) {
            return errorResponse(HttpStatus.NOT_FOUND, "NoSuchBucket", "The specified bucket does not exist.", "/" + bucket);
        }

        File bucketDir = storageManager.getBucketDir(bucket);
        boolean isV2 = "2".equals(listType);
        String pfx = prefix != null ? prefix : "";
        String delim = delimiter != null ? delimiter : "";
        int maxKeys = 1000;
        if (maxKeysParam != null && !maxKeysParam.trim().isEmpty()) {
            try {
                maxKeys = Math.max(1, Math.min(1000, Integer.parseInt(maxKeysParam.trim())));
            } catch (Exception ignored) {}
        }

        String resumeCursor = null;
        if (continuationToken != null && !continuationToken.trim().isEmpty()) {
            try {
                resumeCursor = new String(java.util.Base64.getDecoder().decode(continuationToken.trim()), java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                resumeCursor = continuationToken.trim();
            }
        } else if (startAfter != null && !startAfter.trim().isEmpty()) {
            resumeCursor = startAfter.trim();
        }

        // Listagem recursiva relativa ao bucket
        java.util.List<File> allFiles = new java.util.ArrayList<>();
        collectAllFiles(bucketDir, allFiles);

        java.util.TreeSet<String> commonPrefixes = new java.util.TreeSet<>();
        java.util.List<File> matchingFiles = new java.util.ArrayList<>();

        String bucketPath = bucketDir.getAbsolutePath();
        for (File f : allFiles) {
            String rel = f.getAbsolutePath().substring(bucketPath.length());
            if (rel.startsWith("/")) rel = rel.substring(1);

            if (!pfx.isEmpty() && !rel.startsWith(pfx)) {
                continue;
            }

            if (!delim.isEmpty()) {
                String sub = rel.substring(pfx.length());
                int idx = sub.indexOf(delim);
                if (idx >= 0) {
                    String prefixGroup = pfx + sub.substring(0, idx + delim.length());
                    commonPrefixes.add(prefixGroup);
                    continue;
                }
            }

            matchingFiles.add(f);
        }

        // Ordenação por chave relativa
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

        int totalCount = matchingFiles.size() + commonPrefixes.size();
        boolean isTruncated = totalCount > maxKeys;

        String nextContinuationToken = null;
        if (isTruncated) {
            if (matchingFiles.size() >= maxKeys) {
                File lastFile = matchingFiles.get(maxKeys - 1);
                String lastKey = lastFile.getAbsolutePath().substring(bucketPath.length());
                if (lastKey.startsWith("/")) lastKey = lastKey.substring(1);
                nextContinuationToken = java.util.Base64.getEncoder().encodeToString(lastKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                matchingFiles = matchingFiles.subList(0, maxKeys);
            } else {
                matchingFiles = matchingFiles.subList(0, Math.min(matchingFiles.size(), maxKeys));
            }
        }

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<ListBucketResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">");
        xml.append("<Name>").append(escapeXml(bucket)).append("</Name>");
        xml.append("<Prefix>").append(escapeXml(pfx)).append("</Prefix>");
        xml.append("<MaxKeys>").append(maxKeys).append("</MaxKeys>");
        if (!delim.isEmpty()) {
            xml.append("<Delimiter>").append(escapeXml(delim)).append("</Delimiter>");
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

        return ResponseEntity.ok().header("Content-Type", "application/xml").body(xml.toString());
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
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

    // S3: PutObject (PUT /s3/:bucket/:key)
    @Operation(summary = "Enviar arquivo / objeto S3", description = "Faz upload ou substituição de um objeto no bucket especificado")
    @PutMethod("/:bucket/:key")
    public Object putObject(@PathVariable("bucket") String bucket,
                            @PathVariable("key") String pathKey,
                            @UploadedFile("content") File uploadedTempFile,
                            IHTTPSession session) {
        try {
            String key = extractKey(bucket, session, pathKey);
            File dest = storageManager.resolveFile(bucket, key);
            File parent = dest.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            if (uploadedTempFile != null && uploadedTempFile.exists()) {
                try (InputStream in = new FileInputStream(uploadedTempFile);
                     OutputStream out = new FileOutputStream(dest)) {
                    FileUtils.copyStream(in, out, Long.MAX_VALUE);
                }
            } else {
                Map<String, String> headers = session.getHeaders();
                long contentLength = 0;
                if (headers != null && headers.containsKey("content-length")) {
                    contentLength = Long.parseLong(headers.get("content-length"));
                }

                try (InputStream in = session.getInputStream();
                     OutputStream out = new FileOutputStream(dest)) {
                    FileUtils.copyStream(in, out, contentLength);
                }
            }

            String etag = FileUtils.calculateMD5(dest);
            return ResponseEntity.ok()
                    .header("ETag", "\"" + etag + "\"")
                    .body("OK");
        } catch (Exception e) {
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "InternalError", e.getMessage(), "/" + bucket + "/" + pathKey);
        }
    }

    // S3: GetObject (GET /s3/:bucket/:key)
    @Operation(summary = "Obter / Baixar objeto S3", description = "Faz o download do objeto pelo bucket e chave (key)")
    @GetMethod("/:bucket/:key")
    public Object getObject(@PathVariable("bucket") String bucket,
                            @PathVariable("key") String pathKey,
                            IHTTPSession session) {
        try {
            String key = extractKey(bucket, session, pathKey);
            File file = storageManager.resolveFile(bucket, key);
            if (!file.exists() || !file.isFile()) {
                return errorResponse(HttpStatus.NOT_FOUND, "NoSuchKey", "The specified key does not exist.", "/" + bucket + "/" + key);
            }

            String mime = FileUtils.getMimeType(file.getName());
            Response res = ByteRangeHandler.serveFile(session, file, mime);
            res.addHeader("ETag", "\"" + FileUtils.calculateMD5(file) + "\"");
            res.addHeader("Content-Disposition", "inline; filename=\"" + file.getName() + "\"");
            return res;
        } catch (Exception e) {
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "InternalError", e.getMessage(), "/" + bucket + "/" + pathKey);
        }
    }

    // S3: DeleteObject (DELETE /s3/:bucket/:key)
    @Operation(summary = "Excluir objeto S3", description = "Remove o objeto do bucket especificado")
    @DeleteMethod("/:bucket/:key")
    public Object deleteObject(@PathVariable("bucket") String bucket,
                                @PathVariable("key") String pathKey,
                                IHTTPSession session) {
        try {
            String key = extractKey(bucket, session, pathKey);
            File file = storageManager.resolveFile(bucket, key);
            if (file.exists()) {
                file.delete();
            }
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body("");
        } catch (Exception e) {
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "InternalError", e.getMessage(), "/" + bucket + "/" + pathKey);
        }
    }
}
