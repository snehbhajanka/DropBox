# DropBox S3 Infrastructure

This directory contains Terraform configuration to provision secure AWS S3 infrastructure for the DropBox application. The configuration implements AWS Security Hub Control S3.3 to block public write access to S3 buckets.

## 🔒 Security Features

- **Block Public Access**: All public access settings are enabled to prevent unauthorized access
- **Encryption**: Server-side encryption enabled with AES256
- **Versioning**: Object versioning enabled for data protection
- **IAM Roles**: Least privilege access via dedicated IAM roles
- **Bucket Policies**: Explicit deny policies for public write access
- **HTTPS Only**: Enforces secure transport (SSL/TLS) for all requests

## 📋 AWS Security Hub Control S3.3 Compliance

This configuration addresses the following security requirements:

- ✅ **BlockPublicAcls**: Set to `true`
- ✅ **IgnorePublicAcls**: Set to `true`
- ✅ **BlockPublicPolicy**: Set to `true`
- ✅ **RestrictPublicBuckets**: Set to `true`

## 🚀 Quick Start

### Prerequisites

1. **AWS CLI** installed and configured
2. **Terraform** >= 1.0 installed
3. **jq** installed (for validation script)
4. AWS credentials with appropriate permissions

### Deploy Infrastructure

1. **Copy variables file**:
   ```bash
   cp terraform.tfvars.example terraform.tfvars
   ```

2. **Edit terraform.tfvars** with your specific values:
   ```hcl
   aws_region  = "us-east-1"
   bucket_name = "dropbox-files-myorg-prod"  # Must be globally unique
   environment = "prod"
   ```

3. **Initialize Terraform**:
   ```bash
   terraform init
   ```

4. **Plan deployment**:
   ```bash
   terraform plan
   ```

5. **Apply configuration**:
   ```bash
   terraform apply
   ```

### Validate Security Configuration

After deployment, run the validation script to ensure security compliance:

```bash
./validate-s3-security.sh <your-bucket-name>
```

Example:
```bash
./validate-s3-security.sh dropbox-files-myorg-prod
```

## 📁 File Structure

```
infrastructure/terraform/
├── main.tf                    # Main Terraform configuration
├── variables.tf               # Variable definitions
├── outputs.tf                 # Output definitions
├── terraform.tfvars.example  # Example variables file
├── validate-s3-security.sh    # Security validation script
└── README.md                  # This file
```

## 🔧 Configuration Details

### S3 Bucket Resources

- **aws_s3_bucket**: Main storage bucket
- **aws_s3_bucket_public_access_block**: Blocks all public access
- **aws_s3_bucket_versioning**: Enables object versioning
- **aws_s3_bucket_server_side_encryption_configuration**: Encrypts objects
- **aws_s3_bucket_policy**: Explicit security policies

### IAM Resources

- **aws_iam_role**: Application role for S3 access
- **aws_iam_role_policy**: Least privilege S3 permissions
- **aws_iam_instance_profile**: EC2 instance profile

## 🔍 Verification Commands

### Check Public Access Block Settings
```bash
aws s3api get-public-access-block --bucket <bucket-name>
```

### Verify Bucket Policy
```bash
aws s3api get-bucket-policy --bucket <bucket-name>
```

### Test Public Access (Should Fail)
```bash
aws s3 cp testfile.txt s3://<bucket-name>/ --no-sign-request
```

## 🛡️ Security Best Practices

1. **Least Privilege**: IAM policies grant only necessary permissions
2. **Encryption**: All objects encrypted at rest
3. **HTTPS Only**: SSL/TLS required for all requests
4. **No Public Access**: All public access explicitly blocked
5. **Monitoring**: Use AWS CloudTrail and S3 access logging
6. **Regular Audits**: Run validation script regularly

## 🔄 Environment Management

The configuration supports multiple environments (dev, staging, prod). Each environment should have:

- Separate bucket names
- Separate IAM roles
- Environment-specific tags

## 📊 Outputs

After deployment, Terraform outputs:

- S3 bucket name and ARN
- IAM role ARN
- Instance profile name
- Public access block settings

## ⚠️ Important Notes

1. **Bucket Names**: Must be globally unique across all AWS accounts
2. **Permissions**: Ensure your AWS credentials have sufficient permissions
3. **State Management**: Consider using remote state storage for production
4. **Backup**: Enable S3 Cross-Region Replication for critical data

## 🆘 Troubleshooting

### Common Issues

1. **Bucket name already exists**: Choose a different, globally unique name
2. **Permission denied**: Ensure AWS credentials have required permissions
3. **Validation fails**: Check AWS CLI configuration and bucket accessibility

### Support

For issues with this configuration, check:
1. AWS CloudTrail logs
2. Terraform plan output
3. AWS S3 console bucket settings
4. IAM role permissions

## 📚 References

- [AWS S3 Security Best Practices](https://docs.aws.amazon.com/AmazonS3/latest/userguide/security-best-practices.html)
- [AWS Security Hub S3.3 Control](https://docs.aws.amazon.com/securityhub/latest/userguide/s3-controls.html#s3-3)
- [Terraform AWS Provider](https://registry.terraform.io/providers/hashicorp/aws/latest/docs)