# DropBox S3 Security Configuration

This directory contains Terraform configuration to address the critical security issue: **S3 general purpose buckets should block public write access**.

## Security Features Implemented

### 🔒 S3 Public Access Block
- **Block Public ACLs**: Prevents new public ACLs and uploads with public ACLs
- **Block Public Policy**: Prevents new public bucket policies  
- **Ignore Public ACLs**: Ignores existing public ACLs
- **Restrict Public Buckets**: Restricts cross-account access via bucket policies

### 🛡️ Explicit Deny Policies
The bucket policy explicitly denies:
- `s3:PutObject` - Prevents public file uploads
- `s3:PutObjectAcl` - Prevents public ACL modifications
- `s3:DeleteObject` - Prevents public file deletions
- `s3:DeleteObjectVersion` - Prevents public version deletions
- `s3:RestoreObject` - Prevents public object restoration
- Bucket-level operations that could compromise security

### 🔐 Additional Security Measures
- **Server-side encryption** with AES256
- **Versioning enabled** for data protection
- **Lifecycle policies** for cost optimization
- **Comprehensive tagging** for resource management

## Deployment Instructions

1. **Install Terraform** (version >= 1.0)
2. **Configure AWS credentials**:
   ```bash
   aws configure
   ```

3. **Customize variables**:
   ```bash
   cp terraform.tfvars.example terraform.tfvars
   # Edit terraform.tfvars with your specific values
   ```

4. **Deploy the infrastructure**:
   ```bash
   terraform init
   terraform plan
   terraform apply
   ```

## Validation

After deployment, verify the security configuration:

```bash
# Check public access block status
aws s3api get-public-access-block --bucket YOUR_BUCKET_NAME

# Verify bucket policy
aws s3api get-bucket-policy --bucket YOUR_BUCKET_NAME
```

## Compliance

This configuration addresses:
- **AWS S3 Security Best Practices**
- **NIST Cybersecurity Framework**
- **SOC 2 Type II Controls**
- **GDPR Data Protection Requirements**

## Risk Score: RESOLVED ✅

The implementation of this configuration resolves the critical security issue (Risk Score: 10/10) by:
- Blocking all public write access at the bucket level
- Implementing defense-in-depth security controls
- Following AWS security best practices
- Ensuring compliance with security frameworks