package com.dropbox.application.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class to verify S3 configuration is properly set up
 * Tests security-related configurations
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "aws.region=us-east-1"
})
class S3ConfigTest {

    @Test
    void testS3ClientConfiguration() {
        // Create S3Config instance and test configuration
        S3Config s3Config = new S3Config();
        
        // Use reflection to set the region property for testing
        try {
            var regionField = S3Config.class.getDeclaredField("awsRegion");
            regionField.setAccessible(true);
            regionField.set(s3Config, "us-east-1");
            
            // Test that S3Client can be created without exceptions
            assertDoesNotThrow(() -> {
                s3Config.s3Client();
            }, "S3Config should create S3Client without exceptions when region is provided");
            
        } catch (Exception e) {
            fail("Test setup failed: " + e.getMessage());
        }
    }

    @Test
    void testS3ConfigurationExists() {
        // Verify S3Config class exists and is properly annotated
        assertTrue(S3Config.class.isAnnotationPresent(org.springframework.context.annotation.Configuration.class),
                "S3Config should be annotated with @Configuration");
    }
}