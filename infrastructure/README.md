# S3 Security Remediation - S3.3 Compliance

This directory contains infrastructure as code templates to address the **S3.3 CRITICAL security finding** that prevents public write access to S3 buckets.

## Security Issue Summary

**Misconfiguration ID:** S3.3  
**Severity:** CRITICAL  
**Risk:** Public write access to S3 buckets can lead to data loss, corruption, compliance violations, and unauthorized usage.

## Solution Overview

The provided infrastructure templates ensure that all S3 buckets have proper public access controls configured with:
- `BlockPublicAcls: true`
- `IgnorePublicAcls: true` 
- `BlockPublicPolicy: true`
- `RestrictPublicBuckets: true`

## Directory Structure

```
infrastructure/
├── terraform/              # Terraform configurations
│   ├── main.tf             # Main Terraform configuration
│   ├── variables.tf        # Variable definitions
│   ├── outputs.tf          # Output definitions
│   └── terraform.tfvars.example  # Example variables file
├── cloudformation/         # CloudFormation templates
│   ├── s3-secure-bucket.yaml     # Main CloudFormation template
│   └── parameters.example.yaml   # Example parameters file
└── README.md               # This file
```

## Deployment Options

### Option 1: Terraform Deployment

1. **Prerequisites:**
   - Terraform >= 1.0 installed
   - AWS CLI configured with appropriate credentials
   - S3 bucket name must be globally unique

2. **Deployment Steps:**
   ```bash
   cd infrastructure/terraform
   
   # Copy and customize variables
   cp terraform.tfvars.example terraform.tfvars
   # Edit terraform.tfvars with your specific values
   
   # Initialize Terraform
   terraform init
   
   # Plan the deployment
   terraform plan
   
   # Apply the configuration
   terraform apply
   ```

3. **Required Variables:**
   - `bucket_name`: Globally unique S3 bucket name
   - `aws_region`: AWS region (default: us-east-1)
   - `environment`: Environment name (dev/staging/prod)

### Option 2: CloudFormation Deployment

1. **Prerequisites:**
   - AWS CLI configured with appropriate credentials
   - S3 bucket name must be globally unique

2. **Via AWS CLI:**
   ```bash
   cd infrastructure/cloudformation
   
   # Deploy the stack
   aws cloudformation deploy \
     --template-file s3-secure-bucket.yaml \
     --stack-name dropbox-s3-secure \
     --parameter-overrides \
       BucketName=your-unique-bucket-name \
       Environment=dev \
     --capabilities CAPABILITY_IAM
   ```

3. **Via AWS Console:**
   - Upload `s3-secure-bucket.yaml` to CloudFormation
   - Provide required parameters (BucketName, Environment)
   - Enable IAM capabilities and deploy

## Security Features Implemented

### Public Access Block Settings ✅
- **BlockPublicAcls**: `true` - Prevents new public ACLs
- **IgnorePublicAcls**: `true` - Ignores existing public ACLs  
- **BlockPublicPolicy**: `true` - Prevents public bucket policies
- **RestrictPublicBuckets**: `true` - Restricts access to buckets with public policies

### Additional Security Features
- **Server-side encryption** with AES-256
- **Versioning enabled** for data protection
- **Access logging** to separate bucket
- **Lifecycle policies** for cost optimization
- **CloudWatch logging** for monitoring (CloudFormation only)
- **IAM roles** with least privilege access (CloudFormation only)

## Validation and Testing

### 1. Verify Public Access Block Settings

**Via AWS CLI:**
```bash
# Check public access block status
aws s3api get-public-access-block --bucket <your-bucket-name>

# Expected output should show all settings as 'true':
# {
#     "PublicAccessBlockConfiguration": {
#         "BlockPublicAcls": true,
#         "IgnorePublicAcls": true,
#         "BlockPublicPolicy": true,
#         "RestrictPublicBuckets": true
#     }
# }
```

**Via AWS Management Console:**
1. Navigate to S3 service
2. Select your bucket
3. Go to "Permissions" tab
4. Verify "Block public access" section shows all settings as "On"

### 2. Test Public Write Access Denial

**Using AWS CLI (should fail):**
```bash
# This command should fail with access denied
aws s3 cp test-file.txt s3://<your-bucket-name>/test-file.txt --no-sign-request
```

**Expected Result:** Command should fail with "Access Denied" error, confirming public write access is blocked.

### 3. Compliance Verification Script

Create and run this validation script:

```bash
#!/bin/bash
# save as validate-s3-security.sh

BUCKET_NAME="your-bucket-name"

echo "Validating S3 security configuration for bucket: $BUCKET_NAME"
echo "=================================================="

# Check public access block
echo "1. Checking Public Access Block settings..."
aws s3api get-public-access-block --bucket $BUCKET_NAME

# Check bucket policy (should not allow public access)
echo -e "\n2. Checking bucket policy..."
aws s3api get-bucket-policy --bucket $BUCKET_NAME 2>/dev/null || echo "No bucket policy found (this is good for security)"

# Check bucket ACL
echo -e "\n3. Checking bucket ACL..."
aws s3api get-bucket-acl --bucket $BUCKET_NAME

# Test public access (should fail)
echo -e "\n4. Testing public write access (should fail)..."
echo "test" > /tmp/test-file.txt
aws s3 cp /tmp/test-file.txt s3://$BUCKET_NAME/test-public-access.txt --no-sign-request 2>&1 && echo "ERROR: Public write access is allowed!" || echo "SUCCESS: Public write access properly blocked"
rm -f /tmp/test-file.txt

echo -e "\nValidation complete!"
```

### 4. Monitoring and Alerts

**CloudWatch Metrics to Monitor:**
- `BucketRequests` - Monitor for unauthorized access attempts
- `BucketSizeBytes` - Monitor for unexpected size changes
- `NumberOfObjects` - Monitor for unauthorized object uploads

**Recommended CloudWatch Alarms:**
- Failed authentication attempts
- Unusual access patterns
- Large number of delete operations

## Compliance Standards

This configuration helps meet requirements for:
- **PCI DSS** - Data Security Standard
- **NIST 800-53** - Security Controls Framework
- **SOC 2** - Service Organization Control 2
- **GDPR** - General Data Protection Regulation
- **HIPAA** - Health Insurance Portability and Accountability Act

## Troubleshooting

### Common Issues

1. **Bucket name already exists**
   - S3 bucket names must be globally unique
   - Try adding a suffix like your organization name or timestamp

2. **Access denied during deployment**
   - Ensure AWS credentials have necessary S3 and IAM permissions
   - Required permissions: `s3:CreateBucket`, `s3:PutBucketPublicAccessBlock`, `iam:CreateRole`

3. **Terraform state issues**
   - Use `terraform import` for existing resources
   - Consider using remote state backend for team collaboration

## Next Steps

1. **Deploy the infrastructure** using either Terraform or CloudFormation
2. **Run validation tests** to confirm security settings
3. **Update application code** to use the new S3 bucket
4. **Set up monitoring** and alerting for the bucket
5. **Document the bucket name** and access patterns for your team
6. **Schedule regular security reviews** to ensure continued compliance

## Support

For issues with this configuration:
1. Check AWS CloudFormation/Terraform logs for specific error messages
2. Verify AWS credentials and permissions
3. Ensure bucket names are globally unique
4. Review AWS S3 documentation for latest best practices

---

**Security Compliance Status:** ✅ S3.3 REMEDIATED  
**Last Updated:** September 2024
**Review Required:** Quarterly