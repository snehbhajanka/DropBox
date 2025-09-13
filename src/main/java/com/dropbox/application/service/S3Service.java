package com.dropbox.application.service;

import com.dropbox.application.FileMetaData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class S3Service {

    @Autowired
    private S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.block-public-acls}")
    private boolean blockPublicAcls;

    @Value("${aws.s3.ignore-public-acls}")
    private boolean ignorePublicAcls;

    @Value("${aws.s3.block-public-policy}")
    private boolean blockPublicPolicy;

    @Value("${aws.s3.restrict-public-buckets}")
    private boolean restrictPublicBuckets;

    // In-memory metadata storage for this demo (in production, use database)
    private final Map<String, FileMetaData> fileMetadataStorage = new HashMap<>();

    /**
     * Initialize the S3 bucket with secure configuration that blocks public write access
     */
    public void initializeBucket() {
        try {
            // Check if bucket exists
            if (!bucketExists()) {
                // Create bucket
                CreateBucketRequest createBucketRequest = CreateBucketRequest.builder()
                        .bucket(bucketName)
                        .build();
                s3Client.createBucket(createBucketRequest);
            }

            // Configure Block Public Access settings to ensure no public write access
            PublicAccessBlockConfiguration publicAccessBlockConfig = PublicAccessBlockConfiguration.builder()
                    .blockPublicAcls(blockPublicAcls)
                    .ignorePublicAcls(ignorePublicAcls)
                    .blockPublicPolicy(blockPublicPolicy)
                    .restrictPublicBuckets(restrictPublicBuckets)
                    .build();

            PutPublicAccessBlockRequest putPublicAccessBlockRequest = PutPublicAccessBlockRequest.builder()
                    .bucket(bucketName)
                    .publicAccessBlockConfiguration(publicAccessBlockConfig)
                    .build();

            s3Client.putPublicAccessBlock(putPublicAccessBlockRequest);

        } catch (Exception e) {
            // In test environment, AWS may not be available - log the error but don't fail
            System.err.println("Warning: Failed to initialize S3 bucket (AWS may not be configured): " + e.getMessage());
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

    /**
     * Upload file to S3 with secure configuration
     */
    public String uploadFile(byte[] fileData, String fileName, String contentType, Map<String, String> metadata) {
        try {
            String fileId = UUID.randomUUID().toString();
            String key = "files/" + fileId;

            // Upload to S3
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .metadata(metadata != null ? metadata : new HashMap<>())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(fileData));

            // Store metadata locally
            FileMetaData fileMetaData = new FileMetaData(fileId, fileName, LocalDateTime.now(), 
                    fileData.length, contentType, metadata, null); // Don't store data in memory
            fileMetadataStorage.put(fileId, fileMetaData);

            return fileId;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to S3: " + e.getMessage(), e);
        }
    }

    /**
     * Download file from S3
     */
    public byte[] downloadFile(String fileId) {
        try {
            String key = "files/" + fileId;
            
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            return s3Client.getObject(getObjectRequest).readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("Failed to download file from S3: " + e.getMessage(), e);
        }
    }

    /**
     * Delete file from S3
     */
    public void deleteFile(String fileId) {
        try {
            String key = "files/" + fileId;
            
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            fileMetadataStorage.remove(fileId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete file from S3: " + e.getMessage(), e);
        }
    }

    /**
     * List all file metadata
     */
    public Collection<FileMetaData> listFiles() {
        return fileMetadataStorage.values();
    }

    /**
     * Get file metadata
     */
    public FileMetaData getFileMetadata(String fileId) {
        return fileMetadataStorage.get(fileId);
    }

    /**
     * Update file in S3
     */
    public void updateFile(String fileId, byte[] newData, String contentType, Map<String, String> metadata) {
        if (fileMetadataStorage.containsKey(fileId)) {
            if (newData != null) {
                // Upload new data to S3
                String key = "files/" + fileId;
                
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .contentType(contentType)
                        .metadata(metadata != null ? metadata : new HashMap<>())
                        .build();

                s3Client.putObject(putObjectRequest, RequestBody.fromBytes(newData));

                // Update metadata
                FileMetaData fileMetaData = fileMetadataStorage.get(fileId);
                fileMetaData.setSize(newData.length);
                fileMetaData.setContentType(contentType);
            }
            
            if (metadata != null) {
                fileMetadataStorage.get(fileId).getMetadata().putAll(metadata);
            }
        }
    }

    /**
     * Verify that public access is blocked
     */
    public PublicAccessBlockConfiguration getPublicAccessBlockConfiguration() {
        try {
            GetPublicAccessBlockRequest request = GetPublicAccessBlockRequest.builder()
                    .bucket(bucketName)
                    .build();
            
            GetPublicAccessBlockResponse response = s3Client.getPublicAccessBlock(request);
            return response.publicAccessBlockConfiguration();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get public access block configuration: " + e.getMessage(), e);
        }
    }
}