# DropBox

## Overview
I have added all the five API's required. 

This application provides a secure file storage service with both in-memory and AWS S3 storage options.

## Storage Options

### In-Memory Storage (Default)
In this approach, I have used in-memory storage for development and testing purposes. 
For this implementation, I have assumed that the file size is considerably smaller and can be 
handled in one-go.

### AWS S3 Storage (Production)
We can scale this in production environments to use AWS S3 storage with comprehensive security controls:

- **Public Write Access Blocked**: All S3 buckets are configured to block public write access
- **Server-Side Encryption**: Data at rest is encrypted using AES-256
- **Secure Bucket Policies**: Explicit denial of public write operations
- **Configuration Validation**: Startup validation ensures security settings are properly configured

## Security Features

🔒 **Critical Security Configuration**: This application implements security controls to address the critical S3 misconfiguration where general purpose buckets should block public write access (Risk Score: 10/10).

See [S3_SECURITY_CONFIGURATION.md](S3_SECURITY_CONFIGURATION.md) for detailed security documentation.

## Configuration

### Storage Type
Set the storage type in `application.properties`:
```properties
# Use 'memory' for in-memory storage (default)
# Use 's3' for secure S3 storage
storage.type=memory
```

### S3 Configuration (when storage.type=s3)
```properties
aws.s3.bucket-name=dropbox-secure-storage
aws.s3.region=us-east-2
aws.s3.block-public-access=true
aws.s3.encryption-enabled=true
```

## APIs

The application provides the following REST endpoints:

1. **List Files**: `GET /files`
2. **Read File**: `GET /files/{fileID}`
3. **Upload File**: `POST /files/upload`
4. **Delete File**: `DELETE /files/{fileID}`
5. **Update File**: `PUT /files/{fileID}`

## Enhancements

As an enhancement to this, we can consider:
- File data processing in chunks for large files
- Additional metadata and file versioning
- Advanced security features like audit logging
- Integration with additional cloud storage providers 
