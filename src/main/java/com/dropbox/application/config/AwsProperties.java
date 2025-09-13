package com.dropbox.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "aws")
public class AwsProperties {
    
    private String region = "us-east-1";
    private S3Properties s3 = new S3Properties();
    
    public String getRegion() {
        return region;
    }
    
    public void setRegion(String region) {
        this.region = region;
    }
    
    public S3Properties getS3() {
        return s3;
    }
    
    public void setS3(S3Properties s3) {
        this.s3 = s3;
    }
    
    public static class S3Properties {
        private String bucketName = "dropbox-secure-storage";
        
        public String getBucketName() {
            return bucketName;
        }
        
        public void setBucketName(String bucketName) {
            this.bucketName = bucketName;
        }
    }
}