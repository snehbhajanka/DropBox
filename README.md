# DropBox - Secure File Storage Application

A Spring Boot application providing secure file storage APIs with AWS S3 backend.

## 🔒 Security Features

This application implements enterprise-grade security to prevent critical vulnerabilities:

- **✅ S3 Public Access Block**: Prevents public write access (addresses Risk Score 10/10 vulnerability)
- **✅ Server-Side Encryption**: AES256 encryption for all files
- **✅ Secure File Organization**: UUID-based file keys prevent enumeration
- **✅ Access Controls**: No direct S3 URL exposure
- **✅ Audit Trail**: Comprehensive metadata and logging

## API Endpoints

This application provides 5 secure REST APIs for file management:

### 1. Upload File
```bash
POST /files/upload
```

### 2. List Files  
```bash
GET /files
```

### 3. Read File
```bash
GET /files/{fileID}
```

### 4. Update File
```bash
PUT /files/{fileID}
```

### 5. Delete File
```bash
DELETE /files/{fileID}
```

## Configuration

### Production Configuration
```properties
# AWS S3 Configuration
aws.s3.region=us-east-2
aws.s3.bucket-name=your-secure-bucket-name
aws.s3.block-public-access=true

# Spring Boot Configuration  
server.port=8080
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
```

### Test Configuration
For development and testing, set `aws.s3.test-mode=true` to use in-memory storage.

## Building and Running

```bash
# Build the application
mvn clean compile

# Run tests (includes security validation tests)
mvn test

# Run the application
mvn spring-boot:run
```

## Architecture

- **Backend**: Spring Boot 3.x with Java 17
- **Storage**: AWS S3 with security controls
- **Security**: Public access block, encryption, access controls
- **Testing**: Comprehensive test coverage including security scenarios

## Migration from In-Memory Storage

This application has been upgraded from in-memory storage to secure AWS S3 storage to address critical security vulnerabilities. The API interface remains unchanged for seamless integration.

**Previous approach**: Used HashMap for in-memory storage (vulnerable to data loss, no encryption, no access controls)

**Current approach**: AWS S3 with enterprise security controls, encryption, and audit capabilities

## Security Documentation

For detailed security information, see [SECURITY.md](./SECURITY.md).

## Production Deployment

1. Set up AWS IAM role with minimal S3 permissions
2. Configure environment variables for AWS credentials  
3. Enable S3 access logging and CloudWatch monitoring
4. Regular security audits of S3 configurations

## Contributing

As a security-focused application, all contributions must include:
- Security impact assessment
- Test coverage for new features
- Documentation updates
- Compliance with security best practices 
