package com.dropbox.application;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@TestPropertySource(properties = {
    "aws.s3.bucket-name=test-bucket",
    "aws.s3.region=us-east-2",
    "aws.s3.access-key=test-key",
    "aws.s3.secret-key=test-secret"
})
class DropboxApplicationTests {

	@MockBean
	private S3Client s3Client;

	@Test
	void contextLoads() {
		// Mock S3 responses for initialization
		when(s3Client.headBucket(any(HeadBucketRequest.class)))
				.thenReturn(HeadBucketResponse.builder().build());
		
		when(s3Client.putPublicAccessBlock(any(PutPublicAccessBlockRequest.class)))
				.thenReturn(PutPublicAccessBlockResponse.builder().build());
		
		when(s3Client.putBucketPolicy(any(PutBucketPolicyRequest.class)))
				.thenReturn(PutBucketPolicyResponse.builder().build());
	}
}
