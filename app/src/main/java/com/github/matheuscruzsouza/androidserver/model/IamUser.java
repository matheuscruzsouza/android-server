package com.github.matheuscruzsouza.androidserver.model;

public class IamUser {

    private long id;
    private String name;
    private String accessKey;
    private String secretKey;
    private String policy;
    private boolean enabled;
    private String createdAt;

    public IamUser() {
    }

    public IamUser(String name, String accessKey, String secretKey, String policy, boolean enabled) {
        this.name = name;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.policy = policy;
        this.enabled = enabled;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getPolicy() {
        return policy;
    }

    public void setPolicy(String policy) {
        this.policy = policy;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
