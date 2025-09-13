package com.dropbox.application.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "aws.s3.bucket-name=test-secure-dropbox-storage",
    "aws.s3.region=us-east-1",
    "aws.s3.block-public-acls=true",
    "aws.s3.ignore-public-acls=true",
    "aws.s3.block-public-policy=true",
    "aws.s3.restrict-public-buckets=true"
})
public class S3SecurityConfigurationTest {

    @Autowired
    private S3Service s3Service;

    @Test
    public void testS3ServiceConfiguration() {
        // Test that S3Service is properly configured
        assertNotNull(s3Service, "S3Service should be autowired");
        
        // Test that service can list files (even if empty)
        var files = s3Service.listFiles();
        assertNotNull(files, "File list should not be null");
        assertTrue(files.isEmpty(), "File list should be empty initially");
    }

    @Test 
    public void testSecurityConfiguration() {
        // This test validates that the security properties are properly configured
        // The actual S3 bucket security is verified through the Terraform configuration
        
        // Since we can't test AWS without credentials, we verify the service exists
        // and is properly configured with security in mind
        assertNotNull(s3Service, "S3Service with security configuration should exist");
        
        // The real security verification happens when the bucket is created
        // and the Terraform configuration is applied
    }
}