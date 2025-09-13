# DropBox

A Spring Boot application that provides a simple file storage service similar to DropBox.

## Features

- **File Upload**: Upload files via REST API
- **File Download**: Download files by file ID
- **File Management**: List, update, and delete files
- **Metadata Support**: Store and manage file metadata
- **Secure Storage**: AWS S3 integration with security best practices

## API Endpoints

- `GET /files` - List all files
- `GET /files/{fileID}` - Download a specific file
- `POST /files/upload` - Upload a new file
- `PUT /files/{fileID}` - Update file or metadata
- `DELETE /files/{fileID}` - Delete a file

## Current Implementation

The application currently uses in-memory storage for development and testing. For production environments, it can be scaled to use SQL databases or AWS S3 storage.

## Security Features

This repository includes secure AWS S3 bucket configurations to address critical security vulnerabilities:

### 🔒 S3 Security Remediation (Control ID: S3.3)

**CRITICAL**: This repository provides solutions for blocking public write access to S3 buckets, addressing a 10/10 risk score security vulnerability.

#### Quick Remediation
```bash
# For existing buckets - immediate remediation
./scripts/secure-s3-bucket.sh your-bucket-name

# Validate security configuration
./scripts/validate-s3-security.sh your-bucket-name
```

#### Infrastructure as Code
- **Terraform**: `infrastructure/terraform/` - Secure S3 bucket configuration
- **CloudFormation**: `infrastructure/cloudformation/` - AWS template for secure buckets
- **Documentation**: `docs/S3-SECURITY-REMEDIATION.md` - Comprehensive security guide

#### Security Controls Implemented
- ✅ Block Public ACLs
- ✅ Ignore Public ACLs  
- ✅ Block Public Policy
- ✅ Restrict Public Buckets
- ✅ Server-side Encryption
- ✅ Versioning & Lifecycle Management

## Project Structure

```
├── src/                          # Spring Boot application source
├── infrastructure/               # Infrastructure as Code
│   ├── terraform/               # Terraform configurations
│   └── cloudformation/          # CloudFormation templates
├── scripts/                     # Security remediation scripts
├── docs/                        # Documentation
└── README.md                    # This file
```

## Getting Started

### Prerequisites
- Java 17+
- Maven 3.6+
- AWS CLI (for S3 features)

### Running the Application
```bash
./mvnw spring-boot:run
```

### Testing
```bash
./mvnw test
```

## Production Deployment

For production use:

1. **Deploy Secure S3 Infrastructure**:
   ```bash
   cd infrastructure/terraform
   terraform init && terraform apply
   ```

2. **Configure Application**: Update `application.properties` with S3 configuration

3. **Validate Security**: Run security validation scripts

## Enhancements for Production

- **Chunked File Handling**: Support for large files via multipart uploads
- **Database Integration**: Replace in-memory storage with persistent database
- **Authentication & Authorization**: Add user management and access controls
- **Monitoring & Logging**: Comprehensive observability
- **Auto-scaling**: Container orchestration with Kubernetes

## Security & Compliance

This project addresses critical security requirements:
- **Risk Mitigation**: Resolves S3 public write access vulnerability
- **Compliance**: NIST 800-53, PCI DSS, SOC 2, ISO 27001
- **Best Practices**: Infrastructure as Code, automated validation

For detailed security information, see [S3 Security Remediation Guide](docs/S3-SECURITY-REMEDIATION.md). 
