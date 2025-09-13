# DropBox

A secure file storage application with AWS S3 integration and comprehensive security controls.

## Features

I have added all the five api's required:
- Upload files
- Download files  
- List files
- Update files
- Delete files

## Security Implementation

This application implements **CRITICAL** security measures to prevent S3 public write access vulnerabilities:

### 🔒 Security Controls
- **Block Public ACLs**: Prevents public read/write access via ACLs
- **Ignore Public ACLs**: Ignores any existing public ACLs
- **Block Public Policy**: Prevents public bucket policies
- **Restrict Public Buckets**: Blocks public access entirely

### 📋 Compliance
- Addresses Security Hub control **S3.3**
- Compliant with **PCI DSS** requirements
- Follows **NIST 800-53** guidelines
- Implements AWS security best practices

## Storage Options

### In-Memory Storage (Default)
In this approach, I have used in-memory storage for development and testing. Files are stored in memory using HashMap.

```properties
storage.type=memory
```

### AWS S3 Storage (Production)
For production environments, the application supports secure AWS S3 storage with comprehensive security controls.

```properties
storage.type=s3
aws.region=us-east-1
aws.s3.bucket-name=dropbox-secure-storage
```

## Security Features

### Infrastructure Security
- **Terraform**: Infrastructure as Code with security controls
- **CloudFormation**: Alternative deployment with security templates
- **Validation Scripts**: Automated security compliance checking

### Application Security
- **Storage Abstraction**: Interface-driven secure storage implementation
- **Configuration-driven**: Secure defaults with explicit configuration
- **AWS SDK Integration**: Secure credential management

## Deployment

### Prerequisites
- Java 17+
- Maven 3.6+
- AWS CLI (for S3 storage)
- Terraform (for infrastructure deployment)

### Quick Start
```bash
# Clone and build
./mvnw clean install

# Run with in-memory storage (secure default)
./mvnw spring-boot:run

# Deploy secure S3 infrastructure
./scripts/deploy-infrastructure.sh

# Validate security settings
./scripts/validate-s3-security.sh <bucket-name>
```

### Production Deployment
1. **Deploy Infrastructure**: Use Terraform or CloudFormation templates
2. **Configure Application**: Set S3 bucket and region
3. **Validate Security**: Run security validation scripts
4. **Monitor Compliance**: Set up continuous monitoring

## File Size Considerations

For this implementation, I have assumed that the file size is considerably smaller and can be handled in one-go. As an enhancement, we can consider handling file data in chunks for larger files.

## API Endpoints

- `GET /files` - List all files
- `GET /files/{fileID}` - Download specific file
- `POST /files/upload` - Upload new file
- `PUT /files/{fileID}` - Update existing file
- `DELETE /files/{fileID}` - Delete file

## Security Documentation

See [S3 Security Implementation](docs/S3_SECURITY.md) for detailed security documentation.

## Testing

```bash
# Run all tests
./mvnw test

# Run security validation
./scripts/validate-s3-security.sh

# Test specific functionality
./mvnw test -Dtest=InMemoryStorageServiceTest
```

## Monitoring

The application includes comprehensive security monitoring:
- Automated compliance checking
- Security validation scripts  
- Infrastructure monitoring
- Access logging 
