package com.anjoguarda.storage;

public interface RemoteBytes {

    void put(String bucket, String key, byte[] body);

    byte[] get(String bucket, String key);

    void remove(String bucket, String key);
}
