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

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

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

    // S3: ListBuckets (GET /s3)
    @GetMethod("")
    public Response listBuckets() {
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

        return NanoHTTPD.newFixedLengthResponse(Status.OK, "application/xml", xml.toString());
    }

    // S3: ListObjectsV2 (GET /s3/:bucket)
    @GetMethod("/:bucket")
    public Response listObjects(@PathVariable("bucket") String bucket,
                                @RequestParam("prefix") String prefix) {
        File bucketDir = storageManager.getBucketDir(bucket);
        File[] files = bucketDir.listFiles();

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<ListBucketResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">");
        xml.append("<Name>").append(bucket).append("</Name>");
        xml.append("<Prefix>").append(prefix != null ? prefix : "").append("</Prefix>");
        xml.append("<IsTruncated>false</IsTruncated>");

        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    if (prefix != null && !file.getName().startsWith(prefix)) continue;
                    xml.append("<Contents>");
                    xml.append("<Key>").append(file.getName()).append("</Key>");
                    xml.append("<LastModified>").append(FileUtils.formatIsoDate(file.lastModified())).append("</LastModified>");
                    xml.append("<ETag>\"").append(FileUtils.calculateMD5(file)).append("\"</ETag>");
                    xml.append("<Size>").append(file.length()).append("</Size>");
                    xml.append("<StorageClass>STANDARD</StorageClass>");
                    xml.append("</Contents>");
                }
            }
        }
        xml.append("</ListBucketResult>");

        return NanoHTTPD.newFixedLengthResponse(Status.OK, "application/xml", xml.toString());
    }

    // S3: PutObject (PUT /s3/:bucket/:key)
    @PutMethod("/:bucket/:key")
    public Response putObject(@PathVariable("bucket") String bucket,
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
            Response res = NanoHTTPD.newFixedLengthResponse(Status.OK, "text/plain", "OK");
            res.addHeader("ETag", "\"" + etag + "\"");
            return res;
        } catch (Exception e) {
            return NanoHTTPD.newFixedLengthResponse(Status.INTERNAL_ERROR, "application/xml",
                    "<Error><Code>InternalError</Code><Message>" + e.getMessage() + "</Message></Error>");
        }
    }

    // S3: GetObject (GET /s3/:bucket/:key)
    @GetMethod("/:bucket/:key")
    public Response getObject(@PathVariable("bucket") String bucket,
                              @PathVariable("key") String pathKey,
                              IHTTPSession session) {
        try {
            String key = extractKey(bucket, session, pathKey);
            File file = storageManager.resolveFile(bucket, key);
            if (!file.exists() || !file.isFile()) {
                return NanoHTTPD.newFixedLengthResponse(Status.NOT_FOUND, "application/xml",
                        "<Error><Code>NoSuchKey</Code><Message>Arquivo não encontrado</Message></Error>");
            }

            String mime = FileUtils.getMimeType(file.getName());
            FileInputStream fis = new FileInputStream(file);
            Response res = NanoHTTPD.newFixedLengthResponse(Status.OK, mime, fis, file.length());
            res.addHeader("ETag", "\"" + FileUtils.calculateMD5(file) + "\"");
            res.addHeader("Content-Disposition", "inline; filename=\"" + file.getName() + "\"");
            return res;
        } catch (Exception e) {
            return NanoHTTPD.newFixedLengthResponse(Status.INTERNAL_ERROR, "application/xml",
                    "<Error><Code>InternalError</Code><Message>" + e.getMessage() + "</Message></Error>");
        }
    }

    // S3: DeleteObject (DELETE /s3/:bucket/:key)
    @DeleteMethod("/:bucket/:key")
    public Response deleteObject(@PathVariable("bucket") String bucket,
                                 @PathVariable("key") String pathKey,
                                 IHTTPSession session) {
        try {
            String key = extractKey(bucket, session, pathKey);
            File file = storageManager.resolveFile(bucket, key);
            if (file.exists()) {
                file.delete();
            }
            return NanoHTTPD.newFixedLengthResponse(Status.NO_CONTENT, "text/plain", "");
        } catch (Exception e) {
            return NanoHTTPD.newFixedLengthResponse(Status.INTERNAL_ERROR, "application/xml",
                    "<Error><Code>InternalError</Code><Message>" + e.getMessage() + "</Message></Error>");
        }
    }
}
