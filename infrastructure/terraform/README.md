# DropBox S3 Infrastructure

This directory contains Terraform configurations to create secure S3 buckets for the DropBox application, addressing the S3 security misconfiguration issue.

## Security Features Implemented

This Terraform configuration addresses the critical security issue (Control ID: S3.3) by implementing:

### 1. Block Public Access Settings
- ✅ **BlockPublicAcls**: `true` - Blocks public ACLs
- ✅ **IgnorePublicAcls**: `true` - Ignores existing public ACLs  
- ✅ **BlockPublicPolicy**: `true` - Blocks public bucket policies
- ✅ **RestrictPublicBuckets**: `true` - Restricts public bucket access

### 2. Additional Security Measures
- 🔒 **Encryption**: Server-side encryption enabled by default
- 📝 **Access Logging**: All access requests are logged
- 🔄 **Versioning**: Object versioning enabled for data protection
- 🚫 **Secure Transport**: HTTPS-only access enforced
- 🏷️ **Lifecycle Management**: Automatic storage class transitions

### 3. Compliance
- ✅ **PCI DSS Compliant**: No public access allowed
- ✅ **NIST 800-53 Compliant**: Access controls and audit logging
- ✅ **Zero Public Write Access**: Completely prevents unauthorized data manipulation

## Quick Start

1. **Copy variables file**:
   ```bash
   cp terraform.tfvars.example terraform.tfvars
   ```

2. **Edit variables** in `terraform.tfvars`:
   ```hcl
   aws_region = "us-east-1"
   bucket_name = "your-unique-bucket-name"
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

## Security Validation

After deployment, verify the security settings:

### 1. Check Public Access Block Settings
```bash
aws s3api get-public-access-block --bucket your-bucket-name
```

Expected output should show all settings as `true`:
```json
{
    "PublicAccessBlockConfiguration": {
        "BlockPublicAcls": true,
        "IgnorePublicAcls": true,
        "BlockPublicPolicy": true,
        "RestrictPublicBuckets": true
    }
}
```

### 2. Test Public Write Access (Should Fail)
```bash
echo "test" > test-file.txt
aws s3 cp test-file.txt s3://your-bucket-name/test-file.txt --no-sign-request
```

This command should fail with an access denied error, confirming public write access is blocked.

### 3. Verify Bucket Policy
```bash
aws s3api get-bucket-policy --bucket your-bucket-name
```

## Resources Created

- **Primary S3 Bucket**: Secure storage for DropBox files
- **Logging S3 Bucket**: Stores access logs for audit trail
- **Public Access Block**: Prevents all public access
- **Bucket Policy**: Additional security restrictions
- **Encryption Configuration**: AES-256 server-side encryption
- **Versioning**: Object version management
- **Lifecycle Rules**: Cost-effective storage management

## Troubleshooting

### Common Issues

1. **Bucket Name Already Exists**:
   - S3 bucket names must be globally unique
   - Change the `bucket_name` variable to a unique value

2. **Permission Denied**:
   - Ensure your AWS credentials have S3 management permissions
   - Required permissions: `s3:CreateBucket`, `s3:PutBucketPolicy`, etc.

3. **Region Mismatch**:
   - Ensure AWS CLI region matches the `aws_region` variable

## Cost Optimization

The configuration includes lifecycle rules to reduce costs:
- Files transition to Standard-IA after 30 days
- Files move to Glacier after 90 days
- Old versions are deleted after 365 days

## Maintenance

- Regular security audits using AWS Config
- Monitor access logs for suspicious activity
- Review and update bucket policies as needed
- Keep Terraform state secure and backed up