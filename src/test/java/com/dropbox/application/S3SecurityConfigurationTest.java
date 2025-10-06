package com.dropbox.application;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.atLeastOnce;

@SpringBootTest
class S3SecurityConfigurationTest {

    @MockBean
    private S3Client s3Client;

    @Test
    void testBucketPublicAccessIsBlocked() {
        // Mock bucket head request (bucket exists)
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenReturn(HeadBucketResponse.builder().build());

        // Mock public access block configuration
        when(s3Client.putPublicAccessBlock(any(PutPublicAccessBlockRequest.class)))
                .thenReturn(PutPublicAccessBlockResponse.builder().build());

        // Mock bucket policy request
        when(s3Client.putBucketPolicy(any(PutBucketPolicyRequest.class)))
                .thenReturn(PutBucketPolicyResponse.builder().build());

        // Initialize S3StorageService which should configure secure bucket
        S3StorageService storageService = new S3StorageService();
        storageService.s3Client = s3Client;
        storageService.initializeBucket();

        // Verify that public access block was configured
        verify(s3Client, atLeastOnce()).putPublicAccessBlock(any(PutPublicAccessBlockRequest.class));
        
        // Verify that bucket policy was set
        verify(s3Client, atLeastOnce()).putBucketPolicy(any(PutBucketPolicyRequest.class));
    }

    @Test
    void testPublicAccessBlockConfiguration() {
        // Mock bucket head request (bucket exists)
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenReturn(HeadBucketResponse.builder().build());

        // Capture the public access block configuration
        when(s3Client.putPublicAccessBlock(any(PutPublicAccessBlockRequest.class)))
                .thenAnswer(invocation -> {
                    PutPublicAccessBlockRequest request = invocation.getArgument(0);
                    PublicAccessBlockConfiguration config = request.publicAccessBlockConfiguration();
                    
                    // Verify all public access is blocked
                    assert config.blockPublicAcls() == true : "Public ACLs should be blocked";
                    assert config.ignorePublicAcls() == true : "Public ACLs should be ignored";
                    assert config.blockPublicPolicy() == true : "Public policies should be blocked";
                    assert config.restrictPublicBuckets() == true : "Public buckets should be restricted";
                    
                    return PutPublicAccessBlockResponse.builder().build();
                });

        // Mock bucket policy request
        when(s3Client.putBucketPolicy(any(PutBucketPolicyRequest.class)))
                .thenAnswer(invocation -> {
                    PutBucketPolicyRequest request = invocation.getArgument(0);
                    String policy = request.policy();
                    
                    // Verify policy denies public write access
                    assert policy.contains("DenyPublicWrite") : "Policy should deny public write";
                    assert policy.contains("s3:PutObject") : "Policy should deny PutObject";
                    assert policy.contains("s3:DeleteObject") : "Policy should deny DeleteObject";
                    assert policy.contains("Effect\": \"Deny") : "Policy should have Deny effect";
                    
                    return PutBucketPolicyResponse.builder().build();
                });

        // Initialize S3StorageService
        S3StorageService storageService = new S3StorageService();
        storageService.s3Client = s3Client;
        storageService.initializeBucket();

        // Verify configurations were applied
        verify(s3Client, atLeastOnce()).putPublicAccessBlock(any(PutPublicAccessBlockRequest.class));
        verify(s3Client, atLeastOnce()).putBucketPolicy(any(PutBucketPolicyRequest.class));
    }
}