package com.anjoguarda.storage;

public interface BlobStore {

    void write(String storageKey, byte[] bytes);

    byte[] read(String storageKey);

    void delete(String storageKey);
}
