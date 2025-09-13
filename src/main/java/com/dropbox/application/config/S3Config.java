package com.dropbox.application.config;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import jakarta.annotation.PostConstruct;

@Configuration
@Profile("!test")
public class S3Config {

    @Value("${aws.s3.bucket.name:dropbox-secure-bucket}")
    private String bucketName;

    @Value("${aws.s3.region:us-east-2}")
    private String region;

    @Bean
    public AmazonS3 amazonS3Client() {
        return AmazonS3ClientBuilder.standard()
                .withRegion(region)
                .withCredentials(DefaultAWSCredentialsProviderChain.getInstance())
                .build();
    }

    @PostConstruct
    public void configureBucketSecurity() {
        AmazonS3 s3Client = amazonS3Client();
        
        // Create bucket if it doesn't exist
        if (!s3Client.doesBucketExistV2(bucketName)) {
            s3Client.createBucket(new CreateBucketRequest(bucketName, region));
        }
        
        // Block all public access to prevent security misconfiguration
        blockPublicAccess(s3Client);
        
        // Set bucket policy to deny public write access
        setBucketPolicyToBlockPublicWrites(s3Client);
        
        // Configure bucket encryption
        configureBucketEncryption(s3Client);
    }

    private void blockPublicAccess(AmazonS3 s3Client) {
        try {
            // Block all public access settings
            PublicAccessBlockConfiguration publicAccessBlock = new PublicAccessBlockConfiguration()
                    .withBlockPublicAcls(true)
                    .withIgnorePublicAcls(true)
                    .withBlockPublicPolicy(true)
                    .withRestrictPublicBuckets(true);

            SetPublicAccessBlockRequest request = new SetPublicAccessBlockRequest()
                    .withBucketName(bucketName)
                    .withPublicAccessBlockConfiguration(publicAccessBlock);

            s3Client.setPublicAccessBlock(request);
        } catch (Exception e) {
            throw new RuntimeException("Failed to configure public access block for bucket: " + bucketName, e);
        }
    }

    private void setBucketPolicyToBlockPublicWrites(AmazonS3 s3Client) {
        try {
            // Policy to explicitly deny public write access
            String policyText = "{\n" +
                "  \"Version\": \"2012-10-17\",\n" +
                "  \"Statement\": [\n" +
                "    {\n" +
                "      \"Sid\": \"DenyPublicWriteAccess\",\n" +
                "      \"Effect\": \"Deny\",\n" +
                "      \"Principal\": \"*\",\n" +
                "      \"Action\": [\n" +
                "        \"s3:PutObject\",\n" +
                "        \"s3:PutObjectAcl\",\n" +
                "        \"s3:DeleteObject\",\n" +
                "        \"s3:DeleteObjectVersion\"\n" +
                "      ],\n" +
                "      \"Resource\": \"arn:aws:s3:::" + bucketName + "/*\",\n" +
                "      \"Condition\": {\n" +
                "        \"StringNotEquals\": {\n" +
                "          \"aws:PrincipalServiceName\": [\n" +
                "            \"ec2.amazonaws.com\",\n" +
                "            \"lambda.amazonaws.com\"\n" +
                "          ]\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}";

            s3Client.setBucketPolicy(bucketName, policyText);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set bucket policy for: " + bucketName, e);
        }
    }

    private void configureBucketEncryption(AmazonS3 s3Client) {
        try {
            // Enable server-side encryption
            SetBucketEncryptionRequest request = new SetBucketEncryptionRequest()
                    .withBucketName(bucketName)
                    .withServerSideEncryptionConfiguration(
                            new ServerSideEncryptionConfiguration()
                                    .withRules(new ServerSideEncryptionRule()
                                            .withApplyServerSideEncryptionByDefault(
                                                    new ServerSideEncryptionByDefault()
                                                            .withSSEAlgorithm(SSEAlgorithm.AES256)
                                            )
                                            .withBucketKeyEnabled(true)
                                    )
                    );

            s3Client.setBucketEncryption(request);
        } catch (Exception e) {
            throw new RuntimeException("Failed to configure encryption for bucket: " + bucketName, e);
        }
    }

    public String getBucketName() {
        return bucketName;
    }
}