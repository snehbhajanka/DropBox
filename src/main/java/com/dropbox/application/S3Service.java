package com.dropbox.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class S3Service {

    private final S3Client s3Client;
    private final String bucketName;
    private final boolean s3Enabled;

    public S3Service(@Value("${aws.s3.bucket-name:#{null}}") String bucketName,
                     @Value("${aws.region:us-east-1}") String region,
                     @Value("${aws.s3.enabled:false}") boolean s3Enabled) {
        this.bucketName = bucketName;
        this.s3Enabled = s3Enabled;
        
        if (s3Enabled && bucketName != null) {
            this.s3Client = S3Client.builder()
                    .region(Region.of(region))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
            
            // Verify bucket security configuration on startup
            verifyBucketSecurity();
        } else {
            this.s3Client = null;
        }
    }

    /**
     * Verify that the S3 bucket has proper security configurations
     */
    private void verifyBucketSecurity() {
        try {
            // Check public access block settings
            GetPublicAccessBlockRequest request = GetPublicAccessBlockRequest.builder()
                    .bucket(bucketName)
                    .build();
            
            GetPublicAccessBlockResponse response = s3Client.getPublicAccessBlock(request);
            PublicAccessBlockConfiguration config = response.publicAccessBlockConfiguration();
            
            if (!config.blockPublicAcls() || !config.ignorePublicAcls() || 
                !config.blockPublicPolicy() || !config.restrictPublicBuckets()) {
                throw new SecurityException("S3 bucket does not have proper public access block configuration");
            }
            
            System.out.println("✓ S3 bucket security verification passed - all public access blocked");
            
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new IllegalStateException("S3 bucket not found: " + bucketName);
            }
            throw new RuntimeException("Failed to verify S3 bucket security: " + e.getMessage());
        }
    }

    /**
     * Upload file to S3 with security tags
     */
    public String uploadFile(String fileName, byte[] content, String contentType, Map<String, String> metadata) {
        if (!s3Enabled || s3Client == null) {
            throw new UnsupportedOperationException("S3 storage is not enabled");
        }
        
        String key = generateSecureKey(fileName);
        
        try {
            // Add security tags to all objects
            Map<String, String> tags = new HashMap<>();
            tags.put("Application", "DropBox");
            tags.put("SecurityLevel", "Protected");
            tags.put("UploadDate", String.valueOf(System.currentTimeMillis()));
            
            // Add user metadata if provided
            Map<String, String> objectMetadata = new HashMap<>();
            if (metadata != null) {
                objectMetadata.putAll(metadata);
            }
            objectMetadata.put("security-verified", "true");
            
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .metadata(objectMetadata)
                    .tagging(Tagging.builder()
                            .tagSet(tags.entrySet().stream()
                                    .map(entry -> Tag.builder()
                                            .key(entry.getKey())
                                            .value(entry.getValue())
                                            .build())
                                    .collect(Collectors.toList()))
                            .build())
                    .build();
            
            s3Client.putObject(request, RequestBody.fromBytes(content));
            
            return key;
            
        } catch (S3Exception e) {
            throw new RuntimeException("Failed to upload file to S3: " + e.getMessage());
        }
    }

    /**
     * Download file from S3
     */
    public byte[] downloadFile(String key) {
        if (!s3Enabled || s3Client == null) {
            throw new UnsupportedOperationException("S3 storage is not enabled");
        }
        
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            
            return s3Client.getObject(request).readAllBytes();
            
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return null;
            }
            throw new RuntimeException("Failed to download file from S3: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Failed to read file content: " + e.getMessage());
        }
    }

    /**
     * Delete file from S3
     */
    public boolean deleteFile(String key) {
        if (!s3Enabled || s3Client == null) {
            throw new UnsupportedOperationException("S3 storage is not enabled");
        }
        
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            
            s3Client.deleteObject(request);
            return true;
            
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw new RuntimeException("Failed to delete file from S3: " + e.getMessage());
        }
    }

    /**
     * List files in S3 bucket
     */
    public List<String> listFiles() {
        if (!s3Enabled || s3Client == null) {
            throw new UnsupportedOperationException("S3 storage is not enabled");
        }
        
        try {
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .build();
            
            ListObjectsV2Response response = s3Client.listObjectsV2(request);
            
            return response.contents().stream()
                    .map(S3Object::key)
                    .collect(Collectors.toList());
            
        } catch (S3Exception e) {
            throw new RuntimeException("Failed to list files from S3: " + e.getMessage());
        }
    }

    /**
     * Check if S3 storage is enabled and configured
     */
    public boolean isS3Enabled() {
        return s3Enabled && s3Client != null && bucketName != null;
    }

    /**
     * Get file metadata from S3
     */
    public Map<String, String> getFileMetadata(String key) {
        if (!s3Enabled || s3Client == null) {
            throw new UnsupportedOperationException("S3 storage is not enabled");
        }
        
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            
            HeadObjectResponse response = s3Client.headObject(request);
            return response.metadata();
            
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return null;
            }
            throw new RuntimeException("Failed to get file metadata from S3: " + e.getMessage());
        }
    }

    /**
     * Generate a secure key for storing files
     */
    private String generateSecureKey(String fileName) {
        // Use timestamp and UUID to ensure uniqueness and prevent path traversal
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uuid = UUID.randomUUID().toString();
        String cleanFileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        
        return String.format("secure/%s/%s_%s", timestamp, uuid, cleanFileName);
    }

    /**
     * Verify bucket public access block settings
     */
    public Map<String, Boolean> getBucketSecurityStatus() {
        if (!s3Enabled || s3Client == null) {
            return Map.of("s3_enabled", false);
        }
        
        try {
            GetPublicAccessBlockRequest request = GetPublicAccessBlockRequest.builder()
                    .bucket(bucketName)
                    .build();
            
            GetPublicAccessBlockResponse response = s3Client.getPublicAccessBlock(request);
            PublicAccessBlockConfiguration config = response.publicAccessBlockConfiguration();
            
            Map<String, Boolean> status = new HashMap<>();
            status.put("s3_enabled", true);
            status.put("block_public_acls", config.blockPublicAcls());
            status.put("ignore_public_acls", config.ignorePublicAcls());
            status.put("block_public_policy", config.blockPublicPolicy());
            status.put("restrict_public_buckets", config.restrictPublicBuckets());
            status.put("all_public_access_blocked", 
                config.blockPublicAcls() && config.ignorePublicAcls() && 
                config.blockPublicPolicy() && config.restrictPublicBuckets());
            
            return status;
            
        } catch (S3Exception e) {
            throw new RuntimeException("Failed to get bucket security status: " + e.getMessage());
        }
    }
}