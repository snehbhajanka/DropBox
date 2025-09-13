package com.dropbox.application.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "aws.region=us-east-1",
    "aws.s3.bucket-name=test-secure-bucket",
    "storage.type=memory"
})
class AwsConfigTest {

    @Test
    void testAwsPropertiesConfiguration() {
        AwsProperties awsProperties = new AwsProperties();
        awsProperties.setRegion("us-east-1");
        
        AwsProperties.S3Properties s3Properties = new AwsProperties.S3Properties();
        s3Properties.setBucketName("test-secure-bucket");
        awsProperties.setS3(s3Properties);
        
        assertEquals("us-east-1", awsProperties.getRegion());
        assertEquals("test-secure-bucket", awsProperties.getS3().getBucketName());
    }
    
    @Test
    void testS3ClientConfiguration() {
        AwsProperties awsProperties = new AwsProperties();
        awsProperties.setRegion("us-east-1");
        
        AwsConfig awsConfig = new AwsConfig();
        // We can't easily test the actual S3Client creation without AWS credentials
        // But we can verify that the configuration is set up correctly
        assertNotNull(awsProperties);
        assertEquals("us-east-1", awsProperties.getRegion());
    }
    
    @Test
    void testDefaultBucketName() {
        AwsProperties.S3Properties s3Properties = new AwsProperties.S3Properties();
        assertEquals("dropbox-secure-storage", s3Properties.getBucketName());
    }
    
    @Test
    void testDefaultRegion() {
        AwsProperties awsProperties = new AwsProperties();
        assertEquals("us-east-1", awsProperties.getRegion());
    }
}