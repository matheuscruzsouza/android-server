package com.github.matheuscruzsouza.androidserver.dto.response;

public class IamUserResponse {
    private Long id;
    private String name;
    private String accessKey;
    private String maskedSecretKey;
    private String policy;
    private boolean enabled;
    private String createdAt;

    public IamUserResponse() {
    }

    public IamUserResponse(Long id, String name, String accessKey, String maskedSecretKey, String policy, boolean enabled, String createdAt) {
        this.id = id;
        this.name = name;
        this.accessKey = accessKey;
        this.maskedSecretKey = maskedSecretKey;
        this.policy = policy;
        this.enabled = enabled;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
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

    public String getMaskedSecretKey() {
        return maskedSecretKey;
    }

    public void setMaskedSecretKey(String maskedSecretKey) {
        this.maskedSecretKey = maskedSecretKey;
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
