# DropBox

A Spring Boot application that provides file storage and management APIs similar to Dropbox.

## Features

I have added all the five API's required:
- **GET /files** - List all files
- **GET /files/{fileID}** - Download a specific file
- **POST /files/upload** - Upload a new file
- **PUT /files/{fileID}** - Update file content or metadata
- **DELETE /files/{fileID}** - Delete a file

## Current Implementation

In this approach, I have used in-memory storage. However, we can scale this in production environments to use SQL or AWS storage.

For this commit I have assumed that the file size is considerably smaller and can be handled in one-go. As an enhancement to this, we can consider the file data in chunks and handle.

## 🔒 Security & AWS Integration

This repository now includes secure AWS S3 infrastructure configurations to address critical security requirements:

- **Infrastructure as Code**: Terraform configurations in `/infrastructure/terraform/`
- **Security Compliance**: S3 buckets configured to block public write access
- **Validation Tools**: Automated security validation scripts
- **Documentation**: Comprehensive security remediation guide

### Quick Start for AWS Deployment

1. **Review Security Documentation**:
   ```bash
   cat SECURITY_REMEDIATION.md
   ```

2. **Deploy Secure S3 Infrastructure**:
   ```bash
   cd infrastructure/terraform
   cp terraform.tfvars.example terraform.tfvars
   # Edit terraform.tfvars with your values
   terraform init
   terraform apply
   ```

3. **Validate Security Settings**:
   ```bash
   cd infrastructure
   ./validate-s3-security.sh your-bucket-name
   ```

For detailed security information, see [SECURITY_REMEDIATION.md](SECURITY_REMEDIATION.md). 
