package com.lawyus.reservation.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * S3 客户端（RustFS 兼容 S3 协议），连接参数见 app.rustfs。
 * RustFS 仅支持路径式寻址，且不处理按需校验和之外的完整性头，因此显式固定这两项。
 */
@Configuration
public class RustfsConfig {

    @Bean
    public S3Client s3Client(AppProperties properties) {
        AppProperties.Rustfs rustfs = properties.getRustfs();
        return S3Client.builder()
                .endpointOverride(URI.create(rustfs.getEndpoint()))
                .region(Region.of(rustfs.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(rustfs.getAccessKey(), rustfs.getSecretKey())))
                .forcePathStyle(true)
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }
}
