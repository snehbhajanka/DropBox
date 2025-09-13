# S3 Security Configuration

## Overview
This document describes the critical security configurations implemented to ensure S3 general purpose buckets block public write access, addressing the security misconfiguration with Risk Score: 10/10.

## Security Controls Implemented

### 1. Block Public Access Configuration
- **Default Setting**: `aws.s3.block-public-access=true`
- **Purpose**: Prevents any public write access to S3 buckets
- **Implementation**: Configured at application startup and validated during initialization

### 2. Bucket Policy Restrictions
The application automatically applies a bucket policy that:
- Explicitly denies public write operations (`s3:PutObject`, `s3:PutObjectAcl`, `s3:DeleteObject`)
- Only allows authenticated principals with proper IAM roles
- Prevents unauthorized file uploads and modifications

### 3. S3 Block Public Access Settings
When creating or configuring buckets, the following settings are enforced:
- `blockPublicAcls`: true - Blocks public ACLs
- `ignorePublicAcls`: true - Ignores existing public ACLs  
- `blockPublicPolicy`: true - Blocks public bucket policies
- `restrictPublicBuckets`: true - Restricts public bucket access

### 4. Server-Side Encryption
- **Default Setting**: `aws.s3.encryption-enabled=true`
- **Algorithm**: AES-256 server-side encryption
- **Purpose**: Ensures data at rest is encrypted

## Configuration Properties

### Application Properties
```properties
# AWS S3 Configuration for secure file storage
aws.s3.bucket-name=dropbox-secure-storage
aws.s3.region=us-east-2
aws.s3.access-key-id=${AWS_ACCESS_KEY_ID:}
aws.s3.secret-access-key=${AWS_SECRET_ACCESS_KEY:}

# Storage configuration - set to 's3' to use S3, 'memory' for in-memory
storage.type=memory

# Security settings
aws.s3.block-public-access=true
aws.s3.encryption-enabled=true
```

### Environment Variables
- `AWS_ACCESS_KEY_ID`: AWS access key (optional, can use IAM roles)
- `AWS_SECRET_ACCESS_KEY`: AWS secret key (optional, can use IAM roles)

## Usage

### Switching to S3 Storage
To enable secure S3 storage instead of in-memory storage:

1. Set the storage type: `storage.type=s3`
2. Configure AWS credentials (via environment variables or IAM roles)
3. Ensure the S3 bucket name is unique and appropriate
4. Start the application - it will automatically create and configure the bucket securely

### Security Validation
The application performs the following security validations at startup:

1. **Configuration Validation**: Ensures `block-public-access` is enabled
2. **Bucket Creation**: Creates bucket with secure settings if it doesn't exist
3. **Policy Application**: Applies bucket policy to deny public write access
4. **Encryption Setup**: Enables server-side encryption

## Architecture

### Storage Service Interface
The application uses a `FileStorageService` interface that allows switching between:
- `InMemoryStorageService`: Default, for development and testing
- `S3StorageService`: Production-ready, secure S3 storage

### Security Classes
- `S3Properties`: Configuration properties for S3 settings
- `S3Configuration`: Security configuration and bucket setup
- `S3StorageService`: Secure S3 operations implementation

## Testing

### Security Tests
The implementation includes comprehensive security tests:

1. **Default Security Settings**: Validates secure defaults
2. **Configuration Validation**: Tests security enforcement
3. **Serialization Tests**: Ensures data integrity
4. **Integration Tests**: Validates complete storage operations

### Running Security Tests
```bash
./mvnw test -Dtest=S3ConfigurationSecurityTest
```

## Compliance

This implementation addresses the following security requirements:

- ✅ **S3 General Purpose Buckets Block Public Write Access**
- ✅ **Data Encryption at Rest**
- ✅ **Secure Bucket Policies**
- ✅ **Configuration Validation**
- ✅ **Comprehensive Testing**

## Troubleshooting

### Common Issues

1. **Security Validation Failure**
   - Error: "SECURITY VIOLATION: S3 bucket must be configured to block public access"
   - Solution: Ensure `aws.s3.block-public-access=true` in configuration

2. **Missing Credentials**
   - Error: AWS authentication failure
   - Solution: Set environment variables or configure IAM roles

3. **Bucket Creation Failure**
   - Error: Insufficient permissions
   - Solution: Ensure AWS credentials have S3 bucket creation permissions

## Best Practices

1. **Use IAM Roles**: Prefer IAM roles over static credentials
2. **Monitor Bucket Policies**: Regularly audit bucket policies
3. **Enable CloudTrail**: Monitor S3 API calls
4. **Regular Security Reviews**: Periodically review S3 configurations

## Deployment Considerations

### Staging Environment
- Use separate S3 buckets for staging
- Apply same security configurations
- Test with realistic data volumes

### Production Environment
- Enable S3 access logging
- Set up CloudWatch monitoring
- Implement backup and versioning strategies
- Regular security audits