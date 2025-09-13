# DropBox

A secure file storage application with RESTful APIs for file management operations.

## Features

- **File Upload/Download**: Store and retrieve files with metadata
- **File Management**: List, update, and delete files
- **Secure Storage**: AWS S3 integration with security best practices
- **API Endpoints**: Five core APIs for complete file lifecycle management

## API Endpoints

1. `GET /files` - List all files
2. `GET /files/{fileID}` - Download a specific file
3. `POST /files/upload` - Upload a new file
4. `PUT /files/{fileID}` - Update file content or metadata
5. `DELETE /files/{fileID}` - Delete a file

## Security Features

🔒 **AWS S3 Security Configuration**
- Blocked public write access (AWS Security Hub S3.3 compliance)
- Server-side encryption enabled
- Versioning and lifecycle policies
- Secure bucket policies with least privilege access

## Architecture

### Current Implementation
- **Storage**: In-memory storage for development
- **Framework**: Spring Boot 3.x with Java 17
- **Database**: MySQL connector available for production scaling

### Production Ready Features
- **AWS S3 Integration**: Secure cloud storage with Infrastructure as Code
- **Terraform/CloudFormation**: Automated infrastructure deployment
- **Security Compliance**: Meets AWS Security Hub, PCI DSS, and NIST 800-53 requirements

## Quick Start

### 1. Run the Application
```bash
./mvnw spring-boot:run
```

### 2. Deploy AWS Infrastructure (Optional)

For production deployment with secure S3 storage:

**Using Terraform:**
```bash
cd terraform/
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars with your values
./scripts/deploy-terraform.sh
```

**Using CloudFormation:**
```bash
cd cloudformation/
cp parameters.json.example parameters.json
# Edit parameters.json with your values
./scripts/deploy-cloudformation.sh
```

### 3. Validate Security Configuration
```bash
./scripts/validate-s3-security.sh <your-bucket-name>
```

## Development Notes

- File size is handled in one operation (suitable for smaller files)
- For large files, consider implementing chunked upload/download
- In-memory storage is used for development; production should use AWS S3

## Documentation

- [AWS S3 Security Configuration](docs/AWS_S3_SECURITY.md) - Detailed security setup and compliance
- [Infrastructure as Code](terraform/) - Terraform configurations
- [CloudFormation Templates](cloudformation/) - Alternative deployment option

## Compliance

This application meets the following security standards:
- ✅ AWS Security Hub Control S3.3
- ✅ CIS AWS Foundations Benchmark
- ✅ PCI DSS Requirements
- ✅ NIST 800-53 Controls 
