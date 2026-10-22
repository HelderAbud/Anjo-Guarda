package com.anjoguarda.storage;

import java.net.URI;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
public class StorageConfig {

    @Bean
    @ConditionalOnProperty(name = "anjo.storage", havingValue = "s3")
    S3Settings s3Settings(
            @Value("${anjo.s3.endpoint:}") String endpoint,
            @Value("${anjo.s3.bucket:}") String bucket,
            @Value("${anjo.s3.region:}") String region,
            @Value("${anjo.s3.access-key:}") String accessKey,
            @Value("${anjo.s3.secret-key:}") String secretKey) {
        return new S3Settings(endpoint, bucket, region, accessKey, secretKey);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "anjo.storage", havingValue = "s3")
    S3Client s3Client(S3Settings settings) {
        return S3Client.builder()
                .endpointOverride(URI.create(settings.endpoint()))
                .region(Region.of(settings.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(settings.accessKey(), settings.secretKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @Bean
    @Qualifier("documentStore")
    @ConditionalOnProperty(name = "anjo.storage", havingValue = "s3")
    BlobStore s3DocumentStore(S3Client client, S3Settings settings) {
        return new S3BlobStore(new S3RemoteBytes(client), settings.bucket(), "documentos", "Documento não encontrado");
    }

    @Bean
    @Qualifier("importStore")
    @ConditionalOnProperty(name = "anjo.storage", havingValue = "s3")
    BlobStore s3ImportStore(S3Client client, S3Settings settings) {
        return new S3BlobStore(new S3RemoteBytes(client), settings.bucket(), "imports-originais", "Arquivo inválido");
    }
}
