package com.dropbox.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class S3StorageServiceTest {

    @Mock
    private S3Client s3Client;

    private S3StorageService s3StorageService;
    private final String testBucketName = "test-secure-bucket";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Mock bucket exists check
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenReturn(HeadBucketResponse.builder().build());
        
        // Mock public access block operations
        when(s3Client.putPublicAccessBlock(any(PutPublicAccessBlockRequest.class)))
                .thenReturn(PutPublicAccessBlockResponse.builder().build());
        
        // Mock bucket policy operations
        when(s3Client.putBucketPolicy(any(PutBucketPolicyRequest.class)))
                .thenReturn(PutBucketPolicyResponse.builder().build());
        
        s3StorageService = new S3StorageService(s3Client, testBucketName);
    }

    @Test
    void testSecurityConfigurationAppliedOnInitialization() {
        // Verify that security configurations are applied during initialization
        verify(s3Client, times(1)).putPublicAccessBlock(any(PutPublicAccessBlockRequest.class));
        verify(s3Client, times(1)).putBucketPolicy(any(PutBucketPolicyRequest.class));
    }

    @Test
    void testUploadFileWithSecureSettings() {
        // Mock upload operation
        when(s3Client.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        byte[] testData = "test file content".getBytes();
        Map<String, String> metadata = new HashMap<>();
        metadata.put("author", "test-user");

        // Test upload
        assertDoesNotThrow(() -> {
            s3StorageService.uploadFile("test-key", testData, "text/plain", metadata);
        });

        // Verify that the upload was called with private ACL
        verify(s3Client, times(1)).putObject(
                argThat((PutObjectRequest request) -> request.acl() == ObjectCannedACL.PRIVATE),
                any(software.amazon.awssdk.core.sync.RequestBody.class)
        );
    }

    @Test
    void testVerifySecurityConfiguration() {
        // Mock security configuration verification
        PublicAccessBlockConfiguration secureConfig = PublicAccessBlockConfiguration.builder()
                .blockPublicAcls(true)
                .ignorePublicAcls(true)
                .blockPublicPolicy(true)
                .restrictPublicBuckets(true)
                .build();

        when(s3Client.getPublicAccessBlock(any(GetPublicAccessBlockRequest.class)))
                .thenReturn(GetPublicAccessBlockResponse.builder()
                        .publicAccessBlockConfiguration(secureConfig)
                        .build());

        // Test security verification
        assertTrue(s3StorageService.verifySecurityConfiguration());
        
        verify(s3Client, times(1)).getPublicAccessBlock(any(GetPublicAccessBlockRequest.class));
    }

    @Test
    void testVerifySecurityConfigurationFails() {
        // Mock insecure configuration
        PublicAccessBlockConfiguration insecureConfig = PublicAccessBlockConfiguration.builder()
                .blockPublicAcls(false)  // Not secure
                .ignorePublicAcls(true)
                .blockPublicPolicy(true)
                .restrictPublicBuckets(true)
                .build();

        when(s3Client.getPublicAccessBlock(any(GetPublicAccessBlockRequest.class)))
                .thenReturn(GetPublicAccessBlockResponse.builder()
                        .publicAccessBlockConfiguration(insecureConfig)
                        .build());

        // Test security verification fails
        assertFalse(s3StorageService.verifySecurityConfiguration());
    }

    @Test
    void testFileExists() {
        // Mock file exists
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());

        assertTrue(s3StorageService.fileExists("existing-file"));

        // Mock file doesn't exist
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build());

        assertFalse(s3StorageService.fileExists("non-existing-file"));
    }

    @Test
    void testDeleteFile() {
        // Mock delete operation
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

        assertDoesNotThrow(() -> {
            s3StorageService.deleteFile("test-key");
        });

        verify(s3Client, times(1)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void testSecureBucketPolicyDeniesPublicWrite() {
        // Capture the bucket policy that was applied
        verify(s3Client, times(1)).putBucketPolicy(argThat((PutBucketPolicyRequest request) -> {
            String policy = request.policy();
            // Verify that the policy contains security measures
            return policy.contains("DenyPublicWriteAccess") &&
                   policy.contains("s3:PutObject") &&
                   policy.contains("s3:DeleteObject") &&
                   policy.contains("Effect\": \"Deny") &&
                   policy.contains("Principal\": \"*");
        }));
    }

    @Test
    void testPublicAccessBlockConfiguration() {
        // Verify that public access block was configured with all security settings
        verify(s3Client, times(1)).putPublicAccessBlock(argThat((PutPublicAccessBlockRequest request) -> {
            PublicAccessBlockConfiguration config = request.publicAccessBlockConfiguration();
            return config.blockPublicAcls() &&
                   config.ignorePublicAcls() &&
                   config.blockPublicPolicy() &&
                   config.restrictPublicBuckets();
        }));
    }
}