package com.dropbox.application.service;

import com.dropbox.application.config.S3Properties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.core.exception.SdkException;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * S3 Storage Service
 * Provides secure file operations using AWS S3
 * Ensures all operations respect bucket security configurations
 */
@Service
@ConditionalOnProperty(name = "dropbox.storage.type", havingValue = "s3")
public class S3StorageService {
    
    private static final Logger logger = Logger.getLogger(S3StorageService.class.getName());
    
    @Autowired
    private S3Client s3Client;
    
    @Autowired
    private S3Properties s3Properties;
    
    /**
     * Upload file to S3 with secure settings
     */
    public void uploadFile(String key, byte[] data, String contentType) {
        try {
            // Ensure no public-read or public-read-write ACL
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3Properties.getBucketName())
                    .key(key)
                    .contentType(contentType)
                    .acl(ObjectCannedACL.PRIVATE) // Explicitly set to private
                    .serverSideEncryption(ServerSideEncryption.AES256) // Encrypt at rest
                    .build();
            
            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(data));
            logger.info("File uploaded securely to S3: " + key);
            
        } catch (Exception e) {
            logger.severe("Failed to upload file to S3: " + e.getMessage());
            throw new RuntimeException("Failed to upload file to S3", e);
        }
    }
    
    /**
     * Download file from S3
     */
    public byte[] downloadFile(String key) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(s3Properties.getBucketName())
                    .key(key)
                    .build();
            
            return s3Client.getObject(getObjectRequest).readAllBytes();
            
        } catch (Exception e) {
            logger.severe("Failed to download file from S3: " + e.getMessage());
            throw new RuntimeException("Failed to download file from S3", e);
        }
    }
    
    /**
     * Delete file from S3
     */
    public void deleteFile(String key) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(s3Properties.getBucketName())
                    .key(key)
                    .build();
            
            s3Client.deleteObject(deleteObjectRequest);
            logger.info("File deleted from S3: " + key);
            
        } catch (Exception e) {
            logger.severe("Failed to delete file from S3: " + e.getMessage());
            throw new RuntimeException("Failed to delete file from S3", e);
        }
    }
    
    /**
     * Check if file exists in S3
     */
    public boolean fileExists(String key) {
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(s3Properties.getBucketName())
                    .key(key)
                    .build();
            
            s3Client.headObject(headObjectRequest);
            return true;
            
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            logger.severe("Failed to check file existence in S3: " + e.getMessage());
            throw new RuntimeException("Failed to check file existence in S3", e);
        }
    }
    
    /**
     * Validate S3 bucket security configuration
     * This method checks that the bucket has proper security settings
     */
    public void validateBucketSecurity() {
        try {
            // Check public access block configuration
            GetPublicAccessBlockRequest request = GetPublicAccessBlockRequest.builder()
                    .bucket(s3Properties.getBucketName())
                    .build();
            
            GetPublicAccessBlockResponse response = s3Client.getPublicAccessBlock(request);
            PublicAccessBlockConfiguration config = response.publicAccessBlockConfiguration();
            
            // Validate all security settings are enabled
            if (!config.blockPublicAcls() || 
                !config.ignorePublicAcls() || 
                !config.blockPublicPolicy() || 
                !config.restrictPublicBuckets()) {
                
                throw new SecurityException("S3 bucket does not have proper public access block configuration. " +
                    "All public access settings must be blocked for security compliance.");
            }
            
            logger.info("S3 bucket security validation passed - all public access is properly blocked");
            
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new SecurityException("S3 bucket does not have public access block configuration. " +
                    "This is required for security compliance.");
            }
            throw new RuntimeException("Failed to validate S3 bucket security", e);
        } catch (Exception e) {
            logger.severe("Failed to validate S3 bucket security: " + e.getMessage());
            throw new RuntimeException("Failed to validate S3 bucket security", e);
        }
    }
}