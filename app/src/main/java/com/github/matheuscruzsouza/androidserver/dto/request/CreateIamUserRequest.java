package com.github.matheuscruzsouza.androidserver.dto.request;

public class CreateIamUserRequest {
    private String name;
    private String accessKey;
    private String secretKey;
    private String policy;

    public CreateIamUserRequest() {
    }

    public CreateIamUserRequest(String name, String accessKey, String secretKey, String policy) {
        this.name = name;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.policy = policy;
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
}
