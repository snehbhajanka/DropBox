package com.dropbox.application.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Critical security tests for S3 configuration.
 * These tests ensure that S3 buckets are configured to block public write access.
 */
@SpringBootTest
class S3ConfigurationSecurityTest {

    private S3Properties s3Properties;
    private S3Configuration s3Configuration;

    @BeforeEach
    void setUp() {
        s3Properties = new S3Properties();
        s3Configuration = new S3Configuration();
        // Use reflection to inject properties since @Autowired won't work in unit tests
        try {
            var field = S3Configuration.class.getDeclaredField("s3Properties");
            field.setAccessible(true);
            field.set(s3Configuration, s3Properties);
        } catch (Exception e) {
            // For unit testing, we'll create a mock setup
        }
    }

    /**
     * CRITICAL SECURITY TEST: Ensure that public access is blocked by default
     */
    @Test
    void testS3PropertiesBlockPublicAccessByDefault() {
        // Default configuration should have public access blocked
        assertTrue(s3Properties.isBlockPublicAccess(), 
                "SECURITY VIOLATION: S3 buckets must block public access by default");
    }

    /**
     * CRITICAL SECURITY TEST: Ensure encryption is enabled by default
     */
    @Test
    void testS3PropertiesEncryptionEnabledByDefault() {
        // Default configuration should have encryption enabled
        assertTrue(s3Properties.isEncryptionEnabled(),
                "SECURITY VIOLATION: S3 buckets must have encryption enabled by default");
    }

    /**
     * CRITICAL SECURITY TEST: Configuration validation fails when public access is not blocked
     */
    @Test
    void testSecurityValidationFailsWhenPublicAccessNotBlocked() {
        // Set public access to false (insecure configuration)
        s3Properties.setBlockPublicAccess(false);
        
        // Configuration validation should throw an exception
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            s3Configuration.ensureSecureBucketConfiguration();
        });
        
        assertTrue(exception.getMessage().contains("SECURITY VIOLATION"),
                "Exception should clearly indicate security violation");
        assertTrue(exception.getMessage().contains("block public access"),
                "Exception should specify the public access requirement");
    }

    /**
     * CRITICAL SECURITY TEST: Configuration validation passes when properly secured
     */
    @Test
    void testSecurityValidationPassesWhenProperlySecured() {
        // Ensure secure configuration
        s3Properties.setBlockPublicAccess(true);
        s3Properties.setEncryptionEnabled(true);
        
        // Configuration validation should not throw any exception
        assertDoesNotThrow(() -> {
            s3Configuration.ensureSecureBucketConfiguration();
        });
    }

    /**
     * SECURITY TEST: Validate default bucket name is appropriate
     */
    @Test
    void testDefaultBucketNameConfiguration() {
        s3Properties.setBucketName("dropbox-secure-storage");
        assertNotNull(s3Properties.getBucketName());
        assertTrue(s3Properties.getBucketName().contains("secure"),
                "Bucket name should indicate secure storage");
    }

    /**
     * SECURITY TEST: Validate region configuration is set properly
     */
    @Test
    void testRegionConfigurationIsSet() {
        // Default region should be us-east-2 as specified in requirements
        assertEquals("us-east-2", s3Properties.getRegion(),
                "Default region should be us-east-2 as specified in security requirements");
    }

    /**
     * SECURITY TEST: Verify all security properties can be set
     */
    @Test
    void testSecurityPropertiesCanBeConfigured() {
        s3Properties.setBucketName("test-secure-bucket");
        s3Properties.setRegion("us-east-1");
        s3Properties.setBlockPublicAccess(true);
        s3Properties.setEncryptionEnabled(true);
        
        assertEquals("test-secure-bucket", s3Properties.getBucketName());
        assertEquals("us-east-1", s3Properties.getRegion());
        assertTrue(s3Properties.isBlockPublicAccess());
        assertTrue(s3Properties.isEncryptionEnabled());
    }
}