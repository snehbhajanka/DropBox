package com.dropbox.application.service;

import com.dropbox.application.FileMetaData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class S3Service {

    @Autowired
    private S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.region}")
    private String region;

    @Value("${aws.s3.block-public-access:true}")
    private boolean blockPublicAccess;

    @Value("${aws.s3.test-mode:false}")
    private boolean testMode;

    // Test mode storage
    private final Map<String, TestFileData> testStorage = new ConcurrentHashMap<>();

    private static class TestFileData {
        byte[] data;
        String fileName;
        String contentType;
        Map<String, String> metadata;
        LocalDateTime createdAt;

        TestFileData(byte[] data, String fileName, String contentType, Map<String, String> metadata) {
            this.data = data;
            this.fileName = fileName;
            this.contentType = contentType;
            this.metadata = metadata;
            this.createdAt = LocalDateTime.now();
        }
    }

    @PostConstruct
    public void initialize() {
        if (!testMode) {
            createBucketIfNotExists();
            configureSecureBucket();
        }
    }

    private void createBucketIfNotExists() {
        try {
            HeadBucketRequest headBucketRequest = HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
            s3Client.headBucket(headBucketRequest);
        } catch (NoSuchBucketException e) {
            CreateBucketRequest.Builder createBucketRequestBuilder = CreateBucketRequest.builder()
                    .bucket(bucketName);

            // Add region constraint if not us-east-1
            if (!"us-east-1".equals(region)) {
                BucketLocationConstraint constraint = BucketLocationConstraint.fromValue(region);
                CreateBucketConfiguration bucketConfiguration = CreateBucketConfiguration.builder()
                        .locationConstraint(constraint)
                        .build();
                createBucketRequestBuilder.createBucketConfiguration(bucketConfiguration);
            }

            s3Client.createBucket(createBucketRequestBuilder.build());
        }
    }

    private void configureSecureBucket() {
        if (blockPublicAccess) {
            // Block all public access to prevent security vulnerabilities
            PublicAccessBlockConfiguration publicAccessBlock = PublicAccessBlockConfiguration.builder()
                    .blockPublicAcls(true)
                    .ignorePublicAcls(true)
                    .blockPublicPolicy(true)
                    .restrictPublicBuckets(true)
                    .build();

            PutPublicAccessBlockRequest putPublicAccessBlockRequest = PutPublicAccessBlockRequest.builder()
                    .bucket(bucketName)
                    .publicAccessBlockConfiguration(publicAccessBlock)
                    .build();

            s3Client.putPublicAccessBlock(putPublicAccessBlockRequest);

            // Set server-side encryption for additional security
            ServerSideEncryptionConfiguration encryptionConfig = ServerSideEncryptionConfiguration.builder()
                    .rules(ServerSideEncryptionRule.builder()
                            .applyServerSideEncryptionByDefault(ServerSideEncryptionByDefault.builder()
                                    .sseAlgorithm(ServerSideEncryption.AES256)
                                    .build())
                            .bucketKeyEnabled(true)
                            .build())
                    .build();

            PutBucketEncryptionRequest encryptionRequest = PutBucketEncryptionRequest.builder()
                    .bucket(bucketName)
                    .serverSideEncryptionConfiguration(encryptionConfig)
                    .build();

            s3Client.putBucketEncryption(encryptionRequest);
        }
    }

    public String uploadFile(String fileName, byte[] fileData, String contentType, Map<String, String> metadata) {
        String fileId = UUID.randomUUID().toString();
        
        if (testMode) {
            Map<String, String> fileMetadata = new HashMap<>(metadata != null ? metadata : new HashMap<>());
            fileMetadata.put("original-filename", fileName);
            fileMetadata.put("content-type", contentType);
            fileMetadata.put("upload-timestamp", LocalDateTime.now().toString());
            
            testStorage.put(fileId, new TestFileData(fileData, fileName, contentType, fileMetadata));
            return fileId;
        }
        String key = "files/" + fileId;

        Map<String, String> s3Metadata = new HashMap<>(metadata != null ? metadata : new HashMap<>());
        s3Metadata.put("original-filename", fileName);
        s3Metadata.put("content-type", contentType);
        s3Metadata.put("upload-timestamp", LocalDateTime.now().toString());

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .metadata(s3Metadata)
                .serverSideEncryption(ServerSideEncryption.AES256)
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(fileData));
        return fileId;
    }

    public FileMetaData getFileMetadata(String fileId) {
        if (testMode) {
            TestFileData testFile = testStorage.get(fileId);
            if (testFile != null) {
                return new FileMetaData(
                        fileId,
                        testFile.fileName,
                        testFile.createdAt,
                        testFile.data.length,
                        testFile.contentType,
                        testFile.metadata,
                        null
                );
            }
            return null;
        }
        
        String key = "files/" + fileId;
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            HeadObjectResponse response = s3Client.headObject(headObjectRequest);
            Map<String, String> metadata = response.metadata();

            return new FileMetaData(
                    fileId,
                    metadata.get("original-filename"),
                    LocalDateTime.parse(metadata.get("upload-timestamp")),
                    response.contentLength(),
                    response.contentType(),
                    metadata,
                    null // Don't load data for metadata-only requests
            );
        } catch (NoSuchKeyException e) {
            return null;
        }
    }

    public byte[] getFileData(String fileId) {
        if (testMode) {
            TestFileData testFile = testStorage.get(fileId);
            return testFile != null ? testFile.data : null;
        }
        
        String key = "files/" + fileId;
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            return s3Client.getObject(getObjectRequest).readAllBytes();
        } catch (Exception e) {
            return null;
        }
    }

    public List<FileMetaData> listFiles() {
        if (testMode) {
            return testStorage.entrySet().stream()
                    .map(entry -> {
                        String fileId = entry.getKey();
                        TestFileData testFile = entry.getValue();
                        return new FileMetaData(
                                fileId,
                                testFile.fileName,
                                testFile.createdAt,
                                testFile.data.length,
                                testFile.contentType,
                                testFile.metadata,
                                null
                        );
                    })
                    .collect(Collectors.toList());
        }
        
        ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix("files/")
                .build();

        ListObjectsV2Response response = s3Client.listObjectsV2(listRequest);
        
        return response.contents().stream()
                .map(obj -> {
                    String fileId = obj.key().substring("files/".length());
                    return getFileMetadata(fileId);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public boolean deleteFile(String fileId) {
        if (testMode) {
            return testStorage.remove(fileId) != null;
        }
        
        String key = "files/" + fileId;
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteRequest);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean updateFile(String fileId, byte[] newData, String contentType, Map<String, String> metadata) {
        if (testMode) {
            TestFileData existingFile = testStorage.get(fileId);
            if (existingFile == null) {
                return false;
            }
            
            Map<String, String> updatedMetadata = new HashMap<>(existingFile.metadata);
            if (metadata != null) {
                updatedMetadata.putAll(metadata);
            }
            if (contentType != null) {
                updatedMetadata.put("content-type", contentType);
            }
            
            testStorage.put(fileId, new TestFileData(
                    newData,
                    existingFile.fileName,
                    contentType != null ? contentType : existingFile.contentType,
                    updatedMetadata
            ));
            return true;
        }
        
        if (getFileMetadata(fileId) == null) {
            return false;
        }

        // Delete old file and upload new one with updated data
        String key = "files/" + fileId;
        try {
            // Get existing metadata to preserve what's not being updated
            FileMetaData existingFile = getFileMetadata(fileId);
            Map<String, String> updatedMetadata = new HashMap<>(existingFile.getMetadata());
            
            if (metadata != null) {
                updatedMetadata.putAll(metadata);
            }
            
            if (contentType != null) {
                updatedMetadata.put("content-type", contentType);
            }

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType != null ? contentType : existingFile.getContentType())
                    .metadata(updatedMetadata)
                    .serverSideEncryption(ServerSideEncryption.AES256)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(newData));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}