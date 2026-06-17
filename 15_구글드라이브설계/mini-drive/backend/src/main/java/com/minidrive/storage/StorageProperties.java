package com.minidrive.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage")
public class StorageProperties {
    private String endpoint;
    private String region = "us-east-1";
    private String accessKey;
    private String secretKey;
    private String bucket;
    private boolean pathStyleAccess = true;
    /**
     * TTL for the internal-only presign used by the gateway download (X-Accel-Redirect).
     * v1.6: ultra short-lived (10~30s, default 30) — never exposed to the browser; nginx
     * consumes the presigned path+query immediately to stream from MinIO. The presign is
     * not a security boundary (authorization already happened in the controller); it is
     * only the access mechanism for nginx -> MinIO. (storage-layout.md v1.6)
     */
    private long internalPresignTtlSeconds = 30;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
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

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public boolean isPathStyleAccess() {
        return pathStyleAccess;
    }

    public void setPathStyleAccess(boolean pathStyleAccess) {
        this.pathStyleAccess = pathStyleAccess;
    }

    public long getInternalPresignTtlSeconds() {
        return internalPresignTtlSeconds;
    }

    public void setInternalPresignTtlSeconds(long internalPresignTtlSeconds) {
        this.internalPresignTtlSeconds = internalPresignTtlSeconds;
    }
}
