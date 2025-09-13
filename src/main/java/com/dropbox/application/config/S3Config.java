package com.dropbox.application.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * AWS S3 Configuration with security best practices
 * Ensures S3 client is configured for secure bucket operations
 */
@Configuration
public class S3Config {

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    /**
     * Creates S3 client with default credentials provider
     * Uses IAM roles/instance profiles for secure authentication
     */
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}