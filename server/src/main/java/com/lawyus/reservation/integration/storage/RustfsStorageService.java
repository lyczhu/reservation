package com.lawyus.reservation.integration.storage;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;

import com.lawyus.reservation.common.api.ErrorCode;
import com.lawyus.reservation.common.config.AppProperties;
import com.lawyus.reservation.common.exception.BusinessException;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * RustFS 上传：首次使用时创建桶并设置公共读策略，返回可直接访问的 URL。
 */
@Slf4j
@Service
public class RustfsStorageService {

    private final S3Client s3Client;
    private final AppProperties properties;
    private final AtomicBoolean bucketReady = new AtomicBoolean(false);

    public RustfsStorageService(S3Client s3Client, AppProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    public String upload(byte[] bytes, String contentType, String extension) {
        String bucket = properties.getRustfs().getBucket();
        ensureBucket(bucket);
        String key = "upload/" + LocalDate.now() + "/" + UUID.randomUUID().toString().replace("-", "")
                + "." + extension;
        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(bytes));
        } catch (S3Exception e) {
            log.error("对象上传失败", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件上传失败");
        }
        return properties.getRustfs().getEndpoint() + "/" + bucket + "/" + key;
    }

    private void ensureBucket(String bucket) {
        if (bucketReady.get()) {
            return;
        }
        try {
            if (!exists(bucket)) {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                s3Client.putBucketPolicy(PutBucketPolicyRequest.builder()
                        .bucket(bucket)
                        .policy(publicReadPolicy(bucket))
                        .build());
            }
            bucketReady.set(true);
        } catch (Exception e) {
            log.error("存储桶初始化失败", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "存储初始化失败");
        }
    }

    private boolean exists(String bucket) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return true;
        } catch (NoSuchBucketException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    private String publicReadPolicy(String bucket) {
        return """
                {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},\
                "Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/*"]}]}\
                """.formatted(bucket);
    }
}
