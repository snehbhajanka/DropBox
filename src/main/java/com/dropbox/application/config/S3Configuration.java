package com.dropbox.application.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import jakarta.annotation.PostConstruct;
import java.util.logging.Logger;

@Configuration
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class S3Configuration {

    private static final Logger LOGGER = Logger.getLogger(S3Configuration.class.getName());

    @Autowired
    private S3Properties s3Properties;

    @Bean
    public S3Client s3Client() {
        // Validate that credentials are provided
        if (s3Properties.getAccessKeyId() == null || s3Properties.getAccessKeyId().isEmpty() ||
            s3Properties.getSecretAccessKey() == null || s3Properties.getSecretAccessKey().isEmpty()) {
            LOGGER.warning("AWS credentials not provided. S3 client will use default credential chain.");
            return S3Client.builder()
                    .region(Region.of(s3Properties.getRegion()))
                    .build();
        }

        return S3Client.builder()
                .region(Region.of(s3Properties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                s3Properties.getAccessKeyId(),
                                s3Properties.getSecretAccessKey())))
                .build();
    }

    @PostConstruct
    public void ensureSecureBucketConfiguration() {
        if (!s3Properties.isBlockPublicAccess()) {
            throw new IllegalStateException(
                    "SECURITY VIOLATION: S3 bucket must be configured to block public access. " +
                    "Set aws.s3.block-public-access=true to fix this critical security issue.");
        }

        LOGGER.info("S3 Configuration validated: Public access blocked = " + s3Properties.isBlockPublicAccess());
        LOGGER.info("S3 Configuration validated: Encryption enabled = " + s3Properties.isEncryptionEnabled());
    }

    /**
     * Creates and configures an S3 bucket with secure settings that block public write access.
     * This method implements the critical security requirement to prevent public write access.
     */
    public void createSecureBucket(S3Client s3Client, String bucketName) {
        try {
            // Check if bucket already exists
            try {
                s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
                LOGGER.info("Bucket " + bucketName + " already exists. Applying security configuration...");
            } catch (NoSuchBucketException e) {
                // Create bucket if it doesn't exist
                CreateBucketRequest.Builder createBucketRequestBuilder = CreateBucketRequest.builder()
                        .bucket(bucketName);

                // For regions other than us-east-1, specify the location constraint
                if (!s3Properties.getRegion().equals("us-east-1")) {
                    createBucketRequestBuilder.createBucketConfiguration(
                            CreateBucketConfiguration.builder()
                                    .locationConstraint(BucketLocationConstraint.fromValue(s3Properties.getRegion()))
                                    .build());
                }

                s3Client.createBucket(createBucketRequestBuilder.build());
                LOGGER.info("Created S3 bucket: " + bucketName);
            }

            // CRITICAL SECURITY: Block all public access to prevent unauthorized writes
            applySecureBucketPolicy(s3Client, bucketName);
            blockPublicAccess(s3Client, bucketName);
            
            if (s3Properties.isEncryptionEnabled()) {
                enableBucketEncryption(s3Client, bucketName);
            }

        } catch (Exception e) {
            LOGGER.severe("Failed to create or configure secure S3 bucket: " + e.getMessage());
            throw new RuntimeException("S3 bucket security configuration failed", e);
        }
    }

    /**
     * Applies bucket policy that explicitly denies public write access.
     * This is a critical security control to prevent unauthorized file uploads.
     */
    private void applySecureBucketPolicy(S3Client s3Client, String bucketName) {
        String bucketPolicy = "{\n" +
                "  \"Version\": \"2012-10-17\",\n" +
                "  \"Statement\": [\n" +
                "    {\n" +
                "      \"Sid\": \"DenyPublicWriteAccess\",\n" +
                "      \"Effect\": \"Deny\",\n" +
                "      \"Principal\": \"*\",\n" +
                "      \"Action\": [\n" +
                "        \"s3:PutObject\",\n" +
                "        \"s3:PutObjectAcl\",\n" +
                "        \"s3:DeleteObject\"\n" +
                "      ],\n" +
                "      \"Resource\": \"arn:aws:s3:::" + bucketName + "/*\",\n" +
                "      \"Condition\": {\n" +
                "        \"StringNotEquals\": {\n" +
                "          \"aws:PrincipalType\": \"AssumedRole\"\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        PutBucketPolicyRequest policyRequest = PutBucketPolicyRequest.builder()
                .bucket(bucketName)
                .policy(bucketPolicy)
                .build();

        s3Client.putBucketPolicy(policyRequest);
        LOGGER.info("Applied secure bucket policy to deny public write access for bucket: " + bucketName);
    }

    /**
     * Enables S3 Block Public Access settings to prevent public access at the bucket level.
     * This is the primary defense against public write access.
     */
    private void blockPublicAccess(S3Client s3Client, String bucketName) {
        PublicAccessBlockConfiguration blockConfig = PublicAccessBlockConfiguration.builder()
                .blockPublicAcls(true)          // Block public ACLs
                .ignorePublicAcls(true)         // Ignore existing public ACLs
                .blockPublicPolicy(true)        // Block public bucket policies
                .restrictPublicBuckets(true)    // Restrict public bucket access
                .build();

        PutPublicAccessBlockRequest blockRequest = PutPublicAccessBlockRequest.builder()
                .bucket(bucketName)
                .publicAccessBlockConfiguration(blockConfig)
                .build();

        s3Client.putPublicAccessBlock(blockRequest);
        LOGGER.info("Enabled Block Public Access for bucket: " + bucketName);
    }

    /**
     * Enables server-side encryption for the S3 bucket.
     */
    private void enableBucketEncryption(S3Client s3Client, String bucketName) {
        ServerSideEncryptionRule encryptionRule = ServerSideEncryptionRule.builder()
                .applyServerSideEncryptionByDefault(ServerSideEncryptionByDefault.builder()
                        .sseAlgorithm(ServerSideEncryption.AES256)
                        .build())
                .build();

        ServerSideEncryptionConfiguration encryptionConfig = ServerSideEncryptionConfiguration.builder()
                .rules(encryptionRule)
                .build();

        PutBucketEncryptionRequest encryptionRequest = PutBucketEncryptionRequest.builder()
                .bucket(bucketName)
                .serverSideEncryptionConfiguration(encryptionConfig)
                .build();

        s3Client.putBucketEncryption(encryptionRequest);
        LOGGER.info("Enabled server-side encryption for bucket: " + bucketName);
    }
}