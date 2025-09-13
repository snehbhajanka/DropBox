# S3 Security Configuration Guide

## Overview
This document outlines the security measures implemented to address Security Issue S3.3 - Block Public Write Access to S3 Buckets.

## Security Requirements Addressed

### Critical Security Settings
All S3 buckets created using the provided infrastructure code enforce the following security configurations:

1. **BlockPublicAcls: true** - Prevents new public ACLs from being applied to the bucket and objects
2. **IgnorePublicAcls: true** - Causes Amazon S3 to ignore any public ACLs on the bucket and objects  
3. **BlockPublicPolicy: true** - Blocks new public bucket policies and access point policies
4. **RestrictPublicBuckets: true** - Restricts access to buckets with public policies

### Additional Security Measures

- **Private ACL**: All objects are uploaded with `private` ACL by default
- **Server-Side Encryption**: AES256 encryption enabled for all objects
- **Bucket Versioning**: Enabled to protect against accidental deletions
- **Object Ownership**: Set to `BucketOwnerEnforced` to disable ACLs
- **Lifecycle Policies**: Configured to clean up old versions after 30 days

## Infrastructure Deployment

### Option 1: Terraform Deployment

```bash
cd infrastructure/terraform

# Initialize Terraform
terraform init

# Plan deployment
terraform plan -var="bucket_name=your-dropbox-bucket-name" -var="environment=prod"

# Apply configuration
terraform apply -var="bucket_name=your-dropbox-bucket-name" -var="environment=prod"
```

### Option 2: CloudFormation Deployment

```bash
cd infrastructure/cloudformation

# Deploy stack
aws cloudformation create-stack \
  --stack-name dropbox-s3-secure \
  --template-body file://s3-secure-bucket.yaml \
  --parameters ParameterKey=BucketName,ParameterValue=your-dropbox-bucket-name \
               ParameterKey=Environment,ParameterValue=prod \
  --capabilities CAPABILITY_IAM
```

## Application Configuration

### Environment Variables
Set the following environment variables to enable S3 storage:

```bash
# Storage Configuration
export STORAGE_TYPE=s3

# S3 Configuration
export AWS_S3_BUCKET_NAME=your-dropbox-bucket-name
export AWS_REGION=us-east-1

# AWS Credentials (Use IAM roles in production)
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key
```

### Production Recommendations

1. **Use IAM Roles**: Instead of access keys, use IAM roles for EC2 instances
2. **Enable MFA**: Require multi-factor authentication for sensitive operations
3. **Monitor Access**: Enable CloudTrail logging for all S3 operations
4. **Regular Audits**: Periodically verify security configurations

## Validation Steps

### 1. AWS Management Console Verification
1. Navigate to S3 → Your Bucket → Permissions
2. Verify "Block public access" settings are all enabled
3. Check that "Access control list (ACL)" shows "Disabled"

### 2. AWS CLI Verification
```bash
# Check public access block configuration
aws s3api get-public-access-block --bucket your-dropbox-bucket-name

# Expected output should show all settings as true:
# {
#     "PublicAccessBlockConfiguration": {
#         "BlockPublicAcls": true,
#         "IgnorePublicAcls": true,
#         "BlockPublicPolicy": true,
#         "RestrictPublicBuckets": true
#     }
# }
```

### 3. Application Security Test
Run the security validation in the application:
```bash
# Build and test
mvn clean test -Dtest=S3StorageServiceSecurityTest

# Run application with S3 enabled
export STORAGE_TYPE=s3
mvn spring-boot:run
```

### 4. Unauthorized Access Test
Attempt to upload a file without proper credentials:
```bash
# This should fail
curl -X POST "https://your-dropbox-bucket.s3.amazonaws.com/" \
  -F "file=@test.txt" \
  -F "key=test.txt"
```

## Compliance Verification

The implemented security measures ensure compliance with:

- **AWS Security Best Practices**
- **PCI DSS Requirements** (if applicable)
- **NIST 800-53 Controls** (if applicable)
- **SOC 2 Type II** (if applicable)

## Troubleshooting

### Common Issues

1. **Access Denied Errors**: Verify IAM permissions and bucket policies
2. **Security Validation Failures**: Check that all public access block settings are enabled
3. **Upload Failures**: Ensure application has proper AWS credentials

### Security Monitoring

Monitor the following CloudWatch metrics:
- `BucketRequests` for unusual access patterns
- `NumberOfObjects` for unexpected changes
- `BucketSizeBytes` for storage usage

## Emergency Response

If a security breach is suspected:

1. **Immediate Actions**:
   - Review CloudTrail logs for unauthorized access
   - Verify public access block settings
   - Rotate AWS credentials if compromised

2. **Investigation**:
   - Check S3 access logs
   - Review IAM user activities
   - Scan for public objects

3. **Recovery**:
   - Remove any unauthorized objects
   - Update security configurations
   - Document lessons learned