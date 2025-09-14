package com.dropbox.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

@Service
public class S3StorageService {

    private final S3Client s3Client;
    private final String bucketName;

    public S3StorageService(S3Client s3Client, @Value("${aws.s3.bucket-name}") String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        initializeBucket();
    }

    /**
     * Initialize S3 bucket with secure configurations that block public write access
     */
    private void initializeBucket() {
        try {
            // Check if bucket exists
            if (!bucketExists()) {
                createSecureBucket();
            }
            // Always apply security configurations
            applySecurityConfigurations();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize secure S3 bucket: " + e.getMessage(), e);
        }
    }

    private boolean bucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            return true;
        } catch (NoSuchBucketException e) {
            return false;
        }
    }

    private void createSecureBucket() {
        try {
            s3Client.createBucket(CreateBucketRequest.builder()
                    .bucket(bucketName)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to create S3 bucket: " + e.getMessage(), e);
        }
    }

    /**
     * Apply security configurations to block public write access
     * This is the critical security fix for the reported vulnerability
     */
    private void applySecurityConfigurations() {
        try {
            // Block all public access (including write access)
            blockPublicAccess();
            
            // Apply bucket policy that denies public write access
            applySecureBucketPolicy();
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply security configurations: " + e.getMessage(), e);
        }
    }

    /**
     * Block all public access using S3 Block Public Access feature
     */
    private void blockPublicAccess() {
        PublicAccessBlockConfiguration blockConfig = PublicAccessBlockConfiguration.builder()
                .blockPublicAcls(true)          // Block public ACLs
                .ignorePublicAcls(true)         // Ignore existing public ACLs
                .blockPublicPolicy(true)        // Block public bucket policies
                .restrictPublicBuckets(true)    // Restrict public bucket access
                .build();

        s3Client.putPublicAccessBlock(PutPublicAccessBlockRequest.builder()
                .bucket(bucketName)
                .publicAccessBlockConfiguration(blockConfig)
                .build());
    }

    /**
     * Apply a bucket policy that explicitly denies public write access
     */
    private void applySecureBucketPolicy() {
        String bucketPolicy = String.format("""
            {
                "Version": "2012-10-17",
                "Statement": [
                    {
                        "Sid": "DenyPublicWriteAccess",
                        "Effect": "Deny",
                        "Principal": "*",
                        "Action": [
                            "s3:PutObject",
                            "s3:PutObjectAcl",
                            "s3:DeleteObject",
                            "s3:PutBucketAcl",
                            "s3:PutBucketPolicy"
                        ],
                        "Resource": [
                            "arn:aws:s3:::%s",
                            "arn:aws:s3:::%s/*"
                        ],
                        "Condition": {
                            "StringNotEquals": {
                                "aws:PrincipalServiceName": [
                                    "ec2.amazonaws.com",
                                    "lambda.amazonaws.com"
                                ]
                            }
                        }
                    }
                ]
            }
            """, bucketName, bucketName);

        s3Client.putBucketPolicy(PutBucketPolicyRequest.builder()
                .bucket(bucketName)
                .policy(bucketPolicy)
                .build());
    }

    /**
     * Upload file to S3 with secure access controls
     */
    public void uploadFile(String key, byte[] data, String contentType, Map<String, String> metadata) {
        try {
            PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .acl(ObjectCannedACL.PRIVATE); // Ensure private access

            if (metadata != null) {
                requestBuilder.metadata(metadata);
            }

            s3Client.putObject(requestBuilder.build(), RequestBody.fromBytes(data));
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to S3: " + e.getMessage(), e);
        }
    }

    /**
     * Download file from S3
     */
    public byte[] downloadFile(String key) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            s3Client.getObject(getObjectRequest).transferTo(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to download file from S3: " + e.getMessage(), e);
        }
    }

    /**
     * Delete file from S3
     */
    public void deleteFile(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete file from S3: " + e.getMessage(), e);
        }
    }

    /**
     * Check if file exists in S3
     */
    public boolean fileExists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            throw new RuntimeException("Failed to check file existence: " + e.getMessage(), e);
        }
    }

    /**
     * List all object keys in the bucket (for listing files)
     */
    public List<String> listFiles() {
        try {
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .build();

            return s3Client.listObjectsV2(request).contents().stream()
                    .map(S3Object::key)
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException("Failed to list files: " + e.getMessage(), e);
        }
    }

    /**
     * Get file metadata from S3
     */
    public Map<String, String> getFileMetadata(String key) {
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            HeadObjectResponse response = s3Client.headObject(headObjectRequest);
            return response.metadata();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get file metadata: " + e.getMessage(), e);
        }
    }

    /**
     * Verify bucket security configuration - useful for compliance checks
     */
    public boolean verifySecurityConfiguration() {
        try {
            // Check public access block configuration
            GetPublicAccessBlockResponse publicAccessBlock = s3Client.getPublicAccessBlock(
                GetPublicAccessBlockRequest.builder().bucket(bucketName).build());
            
            PublicAccessBlockConfiguration config = publicAccessBlock.publicAccessBlockConfiguration();
            
            return config.blockPublicAcls() && 
                   config.ignorePublicAcls() && 
                   config.blockPublicPolicy() && 
                   config.restrictPublicBuckets();
        } catch (Exception e) {
            return false;
        }
    }
}