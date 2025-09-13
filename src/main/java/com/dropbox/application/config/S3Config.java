package com.dropbox.application.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * AWS S3 Configuration
 * Creates S3 client with secure configurations
 * Only activated when storage type is set to S3
 */
@Configuration
@ConditionalOnProperty(name = "dropbox.storage.type", havingValue = "s3")
public class S3Config {
    
    @Autowired
    private S3Properties s3Properties;
    
    /**
     * Creates S3 Client with secure credentials
     * Note: In production, use IAM roles instead of access keys
     */
    @Bean
    public S3Client s3Client() {
        // Build credentials - prefer IAM roles in production
        AwsBasicCredentials awsCreds = AwsBasicCredentials.create(
            s3Properties.getAccessKey(),
            s3Properties.getSecretKey()
        );
        
        return S3Client.builder()
                .region(Region.of(s3Properties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                .build();
    }
}