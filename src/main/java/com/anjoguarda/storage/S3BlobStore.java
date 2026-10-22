package com.anjoguarda.storage;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class S3BlobStore implements BlobStore {

    private final RemoteBytes remote;
    private final String bucket;
    private final String prefix;
    private final String missingMessage;

    public S3BlobStore(RemoteBytes remote, String bucket, String prefix, String missingMessage) {
        this.remote = remote;
        this.bucket = bucket;
        this.prefix = prefix;
        this.missingMessage = missingMessage;
    }

    @Override
    public void write(String storageKey, byte[] bytes) {
        String key = objectKey(storageKey);
        try {
            remote.put(bucket, key, bytes);
        } catch (RuntimeException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo inválido");
        }
    }

    @Override
    public byte[] read(String storageKey) {
        try {
            return remote.get(bucket, objectKey(storageKey));
        } catch (MissingObjectException error) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, missingMessage);
        }
    }

    @Override
    public void delete(String storageKey) {
        String key = objectKey(storageKey);
        try {
            remote.remove(bucket, key);
        } catch (RuntimeException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo inválido");
        }
    }

    private String objectKey(String storageKey) {
        if (storageKey == null
                || storageKey.isBlank()
                || storageKey.indexOf('\\') >= 0
                || storageKey.startsWith("/")
                || storageKey.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo inválido");
        }
        return prefix + "/" + storageKey;
    }
}
