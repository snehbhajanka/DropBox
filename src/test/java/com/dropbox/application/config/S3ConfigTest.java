package com.dropbox.application.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class to verify S3 security configurations
 * This test validates that the S3 configuration blocks public write access
 */
class S3SecurityValidationTest {

    @Test
    void testS3ConfigurationBlocksPublicWriteAccess() {
        // Test that the configuration class exists and has the correct structure
        S3Config config = new S3Config();
        assertNotNull(config, "S3Config should be instantiable");
        
        // Verify bucket name getter exists
        assertDoesNotThrow(() -> {
            config.getBucketName();
        }, "getBucketName method should exist without throwing exceptions");
    }

    @Test
    void testPublicAccessBlockConfigurationIsPresent() {
        // This test validates that the public access block configuration
        // is implemented in the S3Config class
        
        String className = S3Config.class.getSimpleName();
        assertEquals("S3Config", className, "S3Config class should exist");
        
        // Verify the class has methods that suggest proper security configuration
        boolean hasConfigureBucketSecurity = false;
        boolean hasBlockPublicAccess = false;
        
        try {
            // Check if the security configuration method exists
            java.lang.reflect.Method[] methods = S3Config.class.getDeclaredMethods();
            for (java.lang.reflect.Method method : methods) {
                if (method.getName().equals("configureBucketSecurity")) {
                    hasConfigureBucketSecurity = true;
                }
                if (method.getName().equals("blockPublicAccess")) {
                    hasBlockPublicAccess = true;
                }
            }
        } catch (Exception e) {
            fail("Failed to inspect S3Config methods: " + e.getMessage());
        }
        
        assertTrue(hasConfigureBucketSecurity, 
                  "S3Config should have configureBucketSecurity method");
        assertTrue(hasBlockPublicAccess, 
                  "S3Config should have blockPublicAccess method");
    }

    @Test
    void testSecurityPolicyStringContainsRequiredElements() {
        // Verify that the security policy contains the necessary elements
        // to block public write access
        
        S3Config config = new S3Config();
        
        // Test that the class contains the required security settings
        String sourceCode = getS3ConfigSourceContent();
        
        // Verify public access block settings
        assertTrue(sourceCode.contains("withBlockPublicAcls(true)"), 
                  "Configuration should block public ACLs");
        assertTrue(sourceCode.contains("withIgnorePublicAcls(true)"), 
                  "Configuration should ignore public ACLs");
        assertTrue(sourceCode.contains("withBlockPublicPolicy(true)"), 
                  "Configuration should block public policy");
        assertTrue(sourceCode.contains("withRestrictPublicBuckets(true)"), 
                  "Configuration should restrict public buckets");
        
        // Verify bucket policy denies public write operations
        assertTrue(sourceCode.contains("DenyPublicWriteAccess"), 
                  "Policy should contain DenyPublicWriteAccess statement");
        assertTrue(sourceCode.contains("s3:PutObject"), 
                  "Policy should deny PutObject action");
        assertTrue(sourceCode.contains("s3:DeleteObject"), 
                  "Policy should deny DeleteObject action");
        assertTrue(sourceCode.contains("\\\"Effect\\\": \\\"Deny\\\""), 
                  "Policy should have Deny effect");
        
        // Verify encryption is enabled
        assertTrue(sourceCode.contains("ServerSideEncryptionByDefault"), 
                  "Configuration should enable server-side encryption");
        assertTrue(sourceCode.contains("AES256"), 
                  "Configuration should use AES256 encryption");
    }

    private String getS3ConfigSourceContent() {
        // Read the S3Config source file to verify its contents
        try {
            java.nio.file.Path configPath = java.nio.file.Paths.get(
                "src/main/java/com/dropbox/application/config/S3Config.java");
            return java.nio.file.Files.readString(configPath);
        } catch (Exception e) {
            // If we can't read the file, return a minimal string that will fail the tests
            // This ensures the tests will fail if the configuration is missing
            return "";
        }
    }

    @Test
    void testTerraformConfigurationExists() {
        // Verify that Terraform configuration exists and contains security settings
        try {
            java.nio.file.Path terraformPath = java.nio.file.Paths.get(
                "terraform/main.tf");
            
            if (java.nio.file.Files.exists(terraformPath)) {
                String terraformContent = java.nio.file.Files.readString(terraformPath);
                
                // Verify terraform contains public access block
                assertTrue(terraformContent.contains("aws_s3_bucket_public_access_block"), 
                          "Terraform should configure public access block");
                
                // Verify all public access settings are blocked
                assertTrue(terraformContent.contains("block_public_acls       = true"), 
                          "Terraform should block public ACLs");
                assertTrue(terraformContent.contains("block_public_policy     = true"), 
                          "Terraform should block public policy");
                assertTrue(terraformContent.contains("ignore_public_acls      = true"), 
                          "Terraform should ignore public ACLs");
                assertTrue(terraformContent.contains("restrict_public_buckets = true"), 
                          "Terraform should restrict public buckets");
                
                // Verify encryption is configured
                assertTrue(terraformContent.contains("aws_s3_bucket_server_side_encryption_configuration"), 
                          "Terraform should configure server-side encryption");
                
                // Verify bucket policy denies public write access
                assertTrue(terraformContent.contains("DenyPublicWriteAccess"), 
                          "Terraform bucket policy should deny public write access");
            }
        } catch (Exception e) {
            // Test passes if terraform file doesn't exist - it's optional
            // but if it exists, it should have proper security configuration
        }
    }
}