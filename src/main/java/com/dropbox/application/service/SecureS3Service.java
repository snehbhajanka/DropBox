package com.dropbox.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.HashMap;
import java.util.Map;

/**
 * S3 Service with built-in security validation
 * Ensures all buckets have proper public access blocking before operations
 */
@Service
public class SecureS3Service {

    private final S3Client s3Client;
    private final String bucketName;

    public SecureS3Service(@Value("${aws.s3.bucket-name:dropbox-secure-storage}") String bucketName) {
        this.bucketName = bucketName;
        this.s3Client = S3Client.builder()
                .region(Region.US_EAST_1)
                .build();
    }

    /**
     * Validates that the bucket has proper security configuration
     * This addresses S3.3 security misconfiguration
     */
    public boolean validateBucketSecurity() {
        try {
            GetPublicAccessBlockRequest request = GetPublicAccessBlockRequest.builder()
                    .bucket(bucketName)
                    .build();
            
            GetPublicAccessBlockResponse response = s3Client.getPublicAccessBlock(request);
            PublicAccessBlockConfiguration config = response.publicAccessBlockConfiguration();
            
            // Verify all security settings are properly configured
            boolean isSecure = config.blockPublicAcls() &&
                             config.ignorePublicAcls() &&
                             config.blockPublicPolicy() &&
                             config.restrictPublicBuckets();
            
            if (!isSecure) {
                throw new SecurityException("S3 bucket does not have proper public access blocking configured");
            }
            
            return true;
            
        } catch (S3Exception e) {
            throw new RuntimeException("Failed to validate bucket security: " + e.getMessage(), e);
        }
    }

    /**
     * Upload file to S3 with security validation
     */
    public String uploadFile(String fileName, byte[] fileData, String contentType) {
        // Always validate security before operations
        validateBucketSecurity();
        
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .contentType(contentType)
                    .serverSideEncryption(ServerSideEncryption.AES256)
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(fileData));
            return fileName;
            
        } catch (S3Exception e) {
            throw new RuntimeException("Failed to upload file to S3: " + e.getMessage(), e);
        }
    }

    /**
     * Download file from S3 with security validation
     */
    public byte[] downloadFile(String fileName) {
        validateBucketSecurity();
        
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .build();

            return s3Client.getObject(request).readAllBytes();
            
        } catch (S3Exception e) {
            throw new RuntimeException("Failed to download file from S3: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Error reading file data: " + e.getMessage(), e);
        }
    }

    /**
     * Delete file from S3 with security validation
     */
    public void deleteFile(String fileName) {
        validateBucketSecurity();
        
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .build();

            s3Client.deleteObject(request);
            
        } catch (S3Exception e) {
            throw new RuntimeException("Failed to delete file from S3: " + e.getMessage(), e);
        }
    }

    /**
     * Check if file exists in S3
     */
    public boolean fileExists(String fileName) {
        validateBucketSecurity();
        
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .build();

            s3Client.headObject(request);
            return true;
            
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            throw new RuntimeException("Failed to check file existence: " + e.getMessage(), e);
        }
    }

    /**
     * Get detailed security report for the bucket
     */
    public Map<String, Object> getSecurityReport() {
        Map<String, Object> report = new HashMap<>();
        
        try {
            // Get public access block configuration
            GetPublicAccessBlockResponse response = s3Client.getPublicAccessBlock(
                GetPublicAccessBlockRequest.builder().bucket(bucketName).build()
            );
            
            PublicAccessBlockConfiguration config = response.publicAccessBlockConfiguration();
            
            report.put("bucket_name", bucketName);
            report.put("block_public_acls", config.blockPublicAcls());
            report.put("ignore_public_acls", config.ignorePublicAcls());
            report.put("block_public_policy", config.blockPublicPolicy());
            report.put("restrict_public_buckets", config.restrictPublicBuckets());
            report.put("security_compliant", validateBucketSecurity());
            report.put("timestamp", java.time.Instant.now().toString());
            
        } catch (Exception e) {
            report.put("error", "Failed to generate security report: " + e.getMessage());
        }
        
        return report;
    }
}