# DropBox

A Spring Boot application that provides file storage and management APIs.

## Features

I have added all the five api's required:
- Upload files
- Download files  
- List files
- Update files
- Delete files

## Storage Approach

In this approach, I have used in-memory storage. However, 
we can scale this in production environments to use SQL or AWS storage. 
For this commit I have assumed that the file size is considerably smaller and can be 
handled in one-go. 
As an enhancement to this, we can consider the file data in chunks and handle.

## Security

### S3 Bucket Security Remediation

This repository includes comprehensive security configurations for AWS S3 buckets to prevent public write access vulnerabilities (S3.3 misconfiguration).

**Security Features:**
- 13 S3 buckets configured with public access blocks
- Terraform and CloudFormation infrastructure templates
- Automated validation and deployment scripts
- Compliance with PCI DSS and NIST 800-53 standards

**Quick Security Deployment:**
```bash
# Deploy secure S3 configuration
./scripts/deploy-s3-security.sh terraform

# Validate security settings
./scripts/validate-s3-security.sh
```

For detailed security information, see [S3 Security Remediation Guide](docs/S3-SECURITY-REMEDIATION.md).

## Project Structure

```
├── src/                           # Java source code
├── infrastructure/                # AWS infrastructure configurations
│   ├── terraform/                 # Terraform configurations
│   └── cloudformation/           # CloudFormation templates
├── scripts/                      # Deployment and validation scripts
├── docs/                         # Documentation
└── README.md                     # This file
``` 
