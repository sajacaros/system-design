package com.minidrive.storage;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;

@Service
public class S3StorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    private final S3Client s3;
    private final S3Presigner presigner;
    private final StorageProperties props;

    public S3StorageService(S3Client s3, S3Presigner presigner, StorageProperties props) {
        this.s3 = s3;
        this.presigner = presigner;
        this.props = props;
    }

    /** Create the bucket on startup if missing (storage-layout.md). Tolerant of MinIO being down. */
    @PostConstruct
    void ensureBucket() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(props.getBucket()).build());
        } catch (NoSuchBucketException e) {
            try {
                s3.createBucket(CreateBucketRequest.builder().bucket(props.getBucket()).build());
                log.info("Created bucket {}", props.getBucket());
            } catch (Exception ce) {
                log.warn("Could not create bucket {}: {}", props.getBucket(), ce.getMessage());
            }
        } catch (Exception e) {
            log.warn("Storage (MinIO) not reachable at startup: {}. Bucket check skipped.", e.getMessage());
        }
    }

    @Override
    public void put(String key, InputStream content, long contentLength, String contentType) {
        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(props.getBucket())
                .key(key)
                .contentType(contentType)
                .build();
        s3.putObject(req, RequestBody.fromInputStream(content, contentLength));
    }

    @Override
    public void copy(String sourceKey, String destKey) {
        CopyObjectRequest req = CopyObjectRequest.builder()
                .sourceBucket(props.getBucket())
                .sourceKey(sourceKey)
                .destinationBucket(props.getBucket())
                .destinationKey(destKey)
                .build();
        s3.copyObject(req);
    }

    @Override
    public InputStream get(String key) {
        return s3.getObject(GetObjectRequest.builder()
                .bucket(props.getBucket())
                .key(key)
                .build());
    }

    @Override
    public String presignGet(String key, Duration ttl, String responseContentType) {
        GetObjectRequest.Builder getReqBuilder = GetObjectRequest.builder()
                .bucket(props.getBucket())
                .key(key);
        if (responseContentType != null && !responseContentType.isBlank()) {
            // Force the Content-Type on the presigned download response so browsers honor
            // UTF-8 (e.g. Korean) even under X-Content-Type-Options: nosniff, and so that
            // already-stored objects with a charset-less Content-Type are corrected.
            getReqBuilder.responseContentType(responseContentType);
        }
        GetObjectRequest getReq = getReqBuilder.build();
        GetObjectPresignRequest presignReq = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(getReq)
                .build();
        return presigner.presignGetObject(presignReq).url().toString();
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder()
                .bucket(props.getBucket())
                .key(key)
                .build());
    }

    @Override
    public void deletePrefix(String prefix) {
        ListObjectsV2Response listed = s3.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(props.getBucket())
                .prefix(prefix)
                .build());
        List<ObjectIdentifier> ids = listed.contents().stream()
                .map(o -> ObjectIdentifier.builder().key(o.key()).build())
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        s3.deleteObjects(DeleteObjectsRequest.builder()
                .bucket(props.getBucket())
                .delete(Delete.builder().objects(ids).build())
                .build());
    }

    @Override
    public boolean exists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(props.getBucket()).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }
}
