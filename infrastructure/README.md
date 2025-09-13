# AWS S3 Security Implementation

This directory contains Infrastructure as Code (IaC) configurations to deploy secure S3 buckets that comply with AWS Security Hub control S3.3: "S3 buckets should block public write access".

## 🔒 Security Compliance

This implementation addresses the **CRITICAL** security vulnerability identified in AWS Security Hub:
- **Control ID**: S3.3
- **Risk Level**: CRITICAL (10/10)
- **Affected Resources**: S3 Buckets
- **Issue**: Block Public Write Access

## 📁 Directory Structure

```
infrastructure/
├── terraform/              # Terraform configurations
│   ├── main.tf             # Main Terraform configuration
│   ├── variables.tf        # Input variables
│   ├── outputs.tf          # Output values
│   └── terraform.tfvars.example  # Example variables file
├── cloudformation/         # CloudFormation templates
│   └── s3-secure-buckets.yaml    # CloudFormation template
└── README.md              # This file
```

## 🛡️ Security Controls Implemented

All S3 buckets created by these configurations enforce the following security controls:

### Public Access Block Configuration
- ✅ **BlockPublicAcls**: `true` - Prevents new public ACLs and uploads with public ACLs
- ✅ **IgnorePublicAcls**: `true` - Ignores all public ACLs on bucket and objects
- ✅ **BlockPublicPolicy**: `true` - Blocks putting bucket policies that allow public access
- ✅ **RestrictPublicBuckets**: `true` - Restricts access to buckets with public policies

### Additional Security Features
- 🔐 **Server-side encryption** with AES-256
- 📋 **Versioning enabled** for data protection
- 🗓️ **Lifecycle policies** for cost optimization
- 🏷️ **Comprehensive tagging** for governance

## 🚀 Deployment Options

### Option 1: Terraform (Recommended)

1. **Prerequisites**:
   - Terraform >= 1.0
   - AWS CLI configured with appropriate permissions
   - `jq` for JSON parsing (for validation scripts)

2. **Deploy**:
   ```bash
   cd infrastructure/terraform
   
   # Copy and customize variables
   cp terraform.tfvars.example terraform.tfvars
   # Edit terraform.tfvars with your specific values
   
   # Initialize and deploy
   terraform init
   terraform plan
   terraform apply
   ```

3. **Validate**:
   ```bash
   # Run security validation
   ../../scripts/validate-s3-security.sh --bucket-prefix your-org-dropbox
   ```

### Option 2: CloudFormation

1. **Deploy via AWS CLI**:
   ```bash
   aws cloudformation deploy \
     --template-file infrastructure/cloudformation/s3-secure-buckets.yaml \
     --stack-name dropbox-s3-security \
     --parameter-overrides \
       BucketPrefix=your-org-dropbox \
       Environment=prod
   ```

2. **Deploy via AWS Console**:
   - Upload `s3-secure-buckets.yaml` to CloudFormation
   - Provide required parameters
   - Deploy the stack

### Option 3: AWS CLI Scripts

1. **Quick Deployment**:
   ```bash
   # Deploy with default settings
   ./scripts/deploy-s3-security.sh --bucket-prefix your-org-dropbox
   
   # Deploy with custom settings
   ./scripts/deploy-s3-security.sh \
     --bucket-prefix your-org-dropbox \
     --region us-west-2 \
     --environment prod
   ```

2. **Dry Run (Preview)**:
   ```bash
   ./scripts/deploy-s3-security.sh --bucket-prefix your-org-dropbox --dry-run
   ```

## 🧪 Testing and Validation

### Automated Security Validation

Use the provided validation script to verify S3.3 compliance:

```bash
# Basic validation
./scripts/validate-s3-security.sh --bucket-prefix your-org-dropbox

# Verbose validation with custom region
./scripts/validate-s3-security.sh \
  --bucket-prefix your-org-dropbox \
  --region us-west-2 \
  --verbose
```

### Manual Verification

1. **AWS CLI Verification**:
   ```bash
   # Check public access block configuration
   aws s3api get-public-access-block --bucket your-bucket-name
   
   # Expected output:
   # {
   #     "PublicAccessBlockConfiguration": {
   #         "BlockPublicAcls": true,
   #         "IgnorePublicAcls": true,
   #         "BlockPublicPolicy": true,
   #         "RestrictPublicBuckets": true
   #     }
   # }
   ```

2. **AWS Console Verification**:
   - Navigate to S3 → Select bucket → Permissions
   - Verify "Block public access (bucket settings)" shows all options enabled

### Functional Testing

Test that public write access is properly blocked:

```bash
# This should fail if security is properly configured
aws s3 cp test-file.txt s3://your-bucket-name/ --no-sign-request
# Expected: Access Denied error
```

## 📊 Bucket Configuration

### Primary Storage Bucket
- **Name**: `{prefix}-dropbox-storage`
- **Purpose**: Main file storage for the DropBox application
- **Lifecycle**: 30 days → IA, 90 days → Glacier, 2 years → Delete
- **Versioning**: Enabled

### Temporary Uploads Bucket
- **Name**: `{prefix}-dropbox-temp-uploads`
- **Purpose**: Temporary storage for file uploads during processing
- **Lifecycle**: 7 days → Delete, 1 day → Abort incomplete uploads
- **Versioning**: Disabled (temporary data)

## 🔧 Customization

### Terraform Variables

Edit `terraform.tfvars` to customize:

```hcl
# Required
bucket_prefix = "your-org-dropbox"  # Must be globally unique

# Optional
aws_region        = "us-east-1"
environment       = "prod"
enable_versioning = true
enable_lifecycle  = true

tags = {
  Owner       = "DevOps Team"
  Application = "DropBox File Storage"
  CostCenter  = "Engineering"
}
```

### CloudFormation Parameters

Customize parameters when deploying:

```yaml
Parameters:
  BucketPrefix: "your-org-dropbox"
  Environment: "prod"
  EnableVersioning: "Enabled"
```

## 🚨 Important Notes

### Bucket Naming
- Bucket names must be **globally unique** across all AWS accounts
- Use a meaningful prefix that includes your organization name
- Follow DNS naming conventions (lowercase, no underscores)

### Permissions Required
The deploying AWS user/role needs these permissions:
- `s3:CreateBucket`
- `s3:PutBucketPublicAccessBlock`
- `s3:PutBucketVersioning`
- `s3:PutBucketEncryption`
- `s3:PutBucketLifecycleConfiguration`
- `s3:PutBucketTagging`
- `s3:GetBucketPublicAccessBlock`

### Cost Considerations
- Versioning increases storage costs
- Lifecycle policies help optimize costs automatically
- Monitor usage and adjust policies as needed

## 🔍 Troubleshooting

### Common Issues

1. **Bucket name already exists**:
   - Bucket names are globally unique
   - Use a more specific prefix
   - Check for typos in the bucket name

2. **Access denied errors**:
   - Verify AWS credentials are configured
   - Check IAM permissions
   - Ensure you're deploying in the correct region

3. **Validation script fails**:
   - Install `jq`: `sudo apt-get install jq` (Ubuntu) or `brew install jq` (macOS)
   - Verify AWS CLI is configured: `aws sts get-caller-identity`

### Getting Help

If you encounter issues:
1. Check AWS CloudTrail for API call details
2. Review AWS CLI error messages
3. Verify the bucket was created in the expected region
4. Use `--verbose` flag with validation scripts for detailed output

## 📚 References

- [AWS S3 Security Best Practices](https://docs.aws.amazon.com/AmazonS3/latest/userguide/security-best-practices.html)
- [AWS Security Hub S3.3 Control](https://docs.aws.amazon.com/securityhub/latest/userguide/securityhub-standards-fsbp-controls.html#fsbp-s3-3)
- [S3 Block Public Access](https://docs.aws.amazon.com/AmazonS3/latest/userguide/access-control-block-public-access.html)
- [Terraform AWS S3 Provider](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/s3_bucket)
- [CloudFormation S3 Resources](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/AWS_S3.html)