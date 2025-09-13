package com.dropbox.application;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import jakarta.annotation.PostConstruct;
import java.util.logging.Logger;

@Service
public class S3StorageService {

    private static final Logger logger = Logger.getLogger(S3StorageService.class.getName());

    @Autowired
    S3Client s3Client; // Package-private for testing

    @Value("${aws.s3.bucket.name:dropbox-secure-bucket}")
    private String bucketName;

    @PostConstruct
    public void initializeBucket() {
        try {
            createSecureBucket();
        } catch (Exception e) {
            logger.severe("Failed to initialize secure bucket: " + e.getMessage());
        }
    }

    private void createSecureBucket() {
        // Check if bucket exists
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            logger.info("Bucket " + bucketName + " already exists");
        } catch (NoSuchBucketException e) {
            // Create bucket if it doesn't exist
            createBucket();
        }

        // Apply security configurations
        configureSecureBucket();
    }

    private void createBucket() {
        try {
            CreateBucketRequest createBucketRequest = CreateBucketRequest.builder()
                    .bucket(bucketName)
                    .createBucketConfiguration(
                            CreateBucketConfiguration.builder()
                                    .locationConstraint(BucketLocationConstraint.US_EAST_2)
                                    .build())
                    .build();

            s3Client.createBucket(createBucketRequest);
            logger.info("Created bucket: " + bucketName);
        } catch (Exception e) {
            logger.severe("Failed to create bucket: " + e.getMessage());
            throw new RuntimeException("Failed to create secure bucket", e);
        }
    }

    private void configureSecureBucket() {
        try {
            // Block all public access - CRITICAL SECURITY CONFIGURATION
            blockPublicAccess();
            
            // Set secure bucket policy
            setSecureBucketPolicy();
            
            logger.info("Applied security configurations to bucket: " + bucketName);
        } catch (Exception e) {
            logger.severe("Failed to configure bucket security: " + e.getMessage());
            throw new RuntimeException("Failed to configure bucket security", e);
        }
    }

    private void blockPublicAccess() {
        // This is the critical security fix for the issue
        PublicAccessBlockConfiguration publicAccessBlock = PublicAccessBlockConfiguration.builder()
                .blockPublicAcls(true)          // Block public ACLs
                .ignorePublicAcls(true)         // Ignore existing public ACLs
                .blockPublicPolicy(true)        // Block public bucket policies
                .restrictPublicBuckets(true)    // Restrict public bucket policies
                .build();

        PutPublicAccessBlockRequest request = PutPublicAccessBlockRequest.builder()
                .bucket(bucketName)
                .publicAccessBlockConfiguration(publicAccessBlock)
                .build();

        s3Client.putPublicAccessBlock(request);
        logger.info("Blocked public access for bucket: " + bucketName);
    }

    private void setSecureBucketPolicy() {
        // Create a secure bucket policy that denies public write access
        String bucketPolicy = """
            {
                "Version": "2012-10-17",
                "Statement": [
                    {
                        "Sid": "DenyPublicWrite",
                        "Effect": "Deny",
                        "Principal": "*",
                        "Action": [
                            "s3:PutObject",
                            "s3:PutObjectAcl",
                            "s3:DeleteObject"
                        ],
                        "Resource": [
                            "arn:aws:s3:::%s/*"
                        ],
                        "Condition": {
                            "Bool": {
                                "aws:PrincipalIsAWSService": "false"
                            }
                        }
                    }
                ]
            }
            """.formatted(bucketName);

        PutBucketPolicyRequest policyRequest = PutBucketPolicyRequest.builder()
                .bucket(bucketName)
                .policy(bucketPolicy)
                .build();

        s3Client.putBucketPolicy(policyRequest);
        logger.info("Set secure bucket policy for: " + bucketName);
    }

    public void uploadFile(String key, byte[] content, String contentType) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(content));
            logger.info("Uploaded file: " + key);
        } catch (Exception e) {
            logger.severe("Failed to upload file: " + e.getMessage());
            throw new RuntimeException("Failed to upload file to S3", e);
        }
    }

    public byte[] downloadFile(String key) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            return s3Client.getObject(getObjectRequest).readAllBytes();
        } catch (NoSuchKeyException e) {
            throw new RuntimeException("File not found: " + key, e);
        } catch (Exception e) {
            logger.severe("Failed to download file: " + e.getMessage());
            throw new RuntimeException("Failed to download file from S3", e);
        }
    }

    public void deleteFile(String key) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            logger.info("Deleted file: " + key);
        } catch (Exception e) {
            logger.severe("Failed to delete file: " + e.getMessage());
            throw new RuntimeException("Failed to delete file from S3", e);
        }
    }

    public boolean fileExists(String key) {
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.headObject(headObjectRequest);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            logger.severe("Failed to check file existence: " + e.getMessage());
            return false;
        }
    }

    public String getBucketName() {
        return bucketName;
    }
}