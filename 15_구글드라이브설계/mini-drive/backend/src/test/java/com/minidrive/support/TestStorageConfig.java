package com.minidrive.support;

import com.minidrive.storage.StorageService;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Replaces MinIO-backed storage with an in-memory fake, and stubs the S3 client beans
 * so the application context loads without a running MinIO.
 */
@TestConfiguration
public class TestStorageConfig {

    @Bean
    @Primary
    public StorageService fakeStorageService() {
        return new FakeStorageService();
    }

    @Bean
    @Primary
    public S3Client testS3Client() {
        S3Client mock = Mockito.mock(S3Client.class);
        Mockito.when(mock.headBucket(Mockito.any(software.amazon.awssdk.services.s3.model.HeadBucketRequest.class)))
                .thenReturn(HeadBucketResponse.builder().build());
        return mock;
    }

    @Bean
    @Primary
    public S3Presigner testS3Presigner() {
        return Mockito.mock(S3Presigner.class);
    }
}
