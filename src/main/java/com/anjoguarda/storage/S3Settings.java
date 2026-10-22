package com.anjoguarda.storage;

public record S3Settings(String endpoint, String bucket, String region, String accessKey, String secretKey) {

    public S3Settings {
        if (blank(endpoint) || blank(bucket) || blank(region) || blank(accessKey) || blank(secretKey)) {
            throw new IllegalStateException("Storage S3 incompleto");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
