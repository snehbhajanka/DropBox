# AWS S3 Integration Configuration

This file demonstrates how to configure the DropBox application to use AWS S3 for production deployment.

## Dependencies

Add these dependencies to your `pom.xml`:

```xml
<!-- AWS SDK for S3 -->
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
    <version>2.20.0</version>
</dependency>

<!-- AWS SDK for credential providers -->
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>sts</artifactId>
    <version>2.20.0</version>
</dependency>
```

## Application Configuration

Add to `application.properties`:

```properties
# AWS Configuration
aws.region=us-east-1
aws.s3.bucket-name=${S3_BUCKET_NAME:dropbox-files-dev}

# Use environment-based configuration for production
spring.profiles.active=${SPRING_PROFILES_ACTIVE:dev}
```

For production, use environment variables:
```bash
export S3_BUCKET_NAME=dropbox-files-prod
export AWS_REGION=us-east-1
export SPRING_PROFILES_ACTIVE=prod
```

## Example S3 Service Implementation

```java
@Service
public class S3FileStorageService {
    
    private final S3Client s3Client;
    private final String bucketName;
    
    public S3FileStorageService(@Value("${aws.s3.bucket-name}") String bucketName) {
        this.bucketName = bucketName;
        this.s3Client = S3Client.builder()
            .region(Region.of(System.getenv("AWS_REGION")))
            .credentialsProvider(InstanceProfileCredentialsProvider.create())
            .build();
    }
    
    public String uploadFile(String fileName, byte[] fileData, String contentType) {
        String key = UUID.randomUUID().toString() + "-" + fileName;
        
        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .contentType(contentType)
            .serverSideEncryption(ServerSideEncryption.AES256)
            .build();
            
        s3Client.putObject(request, RequestBody.fromBytes(fileData));
        return key;
    }
    
    public byte[] downloadFile(String key) {
        GetObjectRequest request = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build();
            
        return s3Client.getObjectAsBytes(request).asByteArray();
    }
    
    public void deleteFile(String key) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build();
            
        s3Client.deleteObject(request);
    }
}
```

## IAM Role Configuration

When deploying on EC2, use the IAM instance profile created by Terraform:

1. Attach the instance profile to your EC2 instances
2. The application will automatically use the IAM role credentials
3. No need to manage AWS access keys in the application

## Security Best Practices

1. **Use IAM Roles**: Never hardcode AWS credentials
2. **Least Privilege**: The Terraform configuration provides minimal required permissions
3. **HTTPS Only**: All S3 access enforces SSL/TLS
4. **Encryption**: All objects are encrypted at rest
5. **Monitoring**: Enable CloudTrail and S3 access logging

## Environment Variables

Set these environment variables for production:

```bash
# Required
S3_BUCKET_NAME=your-production-bucket-name
AWS_REGION=us-east-1

# Optional (defaults provided)
SPRING_PROFILES_ACTIVE=prod
```

## Deployment Notes

1. Deploy the Terraform infrastructure first
2. Use the bucket name and IAM role from Terraform outputs
3. Deploy the application with S3 integration
4. Verify security configuration using the validation script