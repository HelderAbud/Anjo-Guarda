package com.anjoguarda.storage;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

final class S3RemoteBytes implements RemoteBytes {

    private final S3Client client;

    S3RemoteBytes(S3Client client) {
        this.client = client;
    }

    @Override
    public void put(String bucket, String key, byte[] body) {
        client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(), RequestBody.fromBytes(body));
    }

    @Override
    public byte[] get(String bucket, String key) {
        try {
            return client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build())
                    .asByteArray();
        } catch (NoSuchKeyException error) {
            throw new MissingObjectException();
        } catch (S3Exception error) {
            if (error.statusCode() == 404) {
                throw new MissingObjectException();
            }
            throw error;
        }
    }

    @Override
    public void remove(String bucket, String key) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
