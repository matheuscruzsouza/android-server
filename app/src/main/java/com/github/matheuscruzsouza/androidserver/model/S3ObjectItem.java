package com.github.matheuscruzsouza.androidserver.model;

import java.io.File;

public class S3ObjectItem {

    private String bucket;
    private String key;
    private long size;
    private long lastModified;
    private String absolutePath;

    public S3ObjectItem() {
    }

    public S3ObjectItem(String bucket, String key, long size, long lastModified, String absolutePath) {
        this.bucket = bucket;
        this.key = key;
        this.size = size;
        this.lastModified = lastModified;
        this.absolutePath = absolutePath;
    }

    public static S3ObjectItem fromFile(String bucket, String key, File file) {
        return new S3ObjectItem(
                bucket,
                key,
                file.exists() ? file.length() : 0,
                file.exists() ? file.lastModified() : System.currentTimeMillis(),
                file.getAbsolutePath()
        );
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public String getAbsolutePath() {
        return absolutePath;
    }

    public void setAbsolutePath(String absolutePath) {
        this.absolutePath = absolutePath;
    }
}
