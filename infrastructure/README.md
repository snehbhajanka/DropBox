# S3 Security Infrastructure

This directory contains Infrastructure as Code (IaC) configurations to address the **S3.3 Security Issue - Block Public Write Access** for the DropBox application.

## 🔒 Security Issue Summary

- **Issue ID**: S3.3
- **Severity**: CRITICAL
- **Risk**: Public write access to S3 buckets
- **Impact**: Data loss, corruption, compliance violations, reputation damage
- **Affected Buckets**: 13 buckets with public write access

## 📁 Directory Structure

```
infrastructure/
├── terraform/                 # Terraform configurations
│   ├── main.tf                # Main Terraform configuration
│   ├── variables.tf           # Variable definitions
│   ├── outputs.tf             # Output definitions
│   └── terraform.tfvars.example # Example variables file
├── cloudformation/            # CloudFormation templates
│   ├── s3-security.yml        # CloudFormation template
│   └── parameters.json        # Parameter values
└── README.md                  # This file
```

## 🛡️ Security Controls Implemented

All S3 buckets created by these configurations include the following security controls:

### Public Access Block Settings
- ✅ **BlockPublicAcls**: `true` - Prevents new ACLs that allow public access
- ✅ **IgnorePublicAcls**: `true` - Ignores existing ACLs that allow public access
- ✅ **BlockPublicPolicy**: `true` - Prevents bucket policies that allow public access
- ✅ **RestrictPublicBuckets**: `true` - Restricts access to buckets with public policies

### Additional Security Features
- 🔐 **Server-side encryption** with AES256
- 📚 **Versioning enabled** for data protection
- 🏷️ **Consistent tagging** for compliance tracking
- 🔒 **Private ACLs** explicitly set

## 🚀 Deployment Options

### Option 1: Terraform

#### Prerequisites
- Terraform >= 1.0
- AWS CLI configured with appropriate permissions
- Valid AWS credentials

#### Quick Start
```bash
# Navigate to Terraform directory
cd infrastructure/terraform

# Copy and customize variables
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars with your specific values

# Initialize Terraform
terraform init

# Review the plan
terraform plan

# Apply the configuration
terraform apply
```

#### Required Variables
```hcl
aws_region   = "us-east-1"                    # AWS region
environment  = "prod"                         # Environment name
bucket_name  = "dropbox-storage-prod-12345"   # Unique bucket name
```

### Option 2: CloudFormation

#### Prerequisites
- AWS CLI configured with appropriate permissions
- Valid AWS credentials

#### Quick Start
```bash
# Navigate to CloudFormation directory
cd infrastructure/cloudformation

# Validate the template
aws cloudformation validate-template --template-body file://s3-security.yml

# Deploy the stack
aws cloudformation create-stack \
  --stack-name dropbox-s3-security \
  --template-body file://s3-security.yml \
  --parameters file://parameters.json

# Monitor deployment
aws cloudformation describe-stack-events --stack-name dropbox-s3-security
```

#### Customizing Parameters
Edit `parameters.json`:
```json
[
  {
    "ParameterKey": "BucketName",
    "ParameterValue": "your-unique-bucket-name"
  },
  {
    "ParameterKey": "Environment",
    "ParameterValue": "prod"
  }
]
```

## ✅ Validation and Testing

### Automated Validation Scripts

Two validation scripts are provided to ensure security compliance:

#### 1. Infrastructure Validation
```bash
# Validate Terraform and CloudFormation configurations
./scripts/validate-infrastructure.sh
```

This script:
- ✅ Validates Terraform syntax and configuration
- ✅ Validates CloudFormation template
- ✅ Checks for required security settings
- ✅ Performs formatting checks

#### 2. Runtime S3 Security Validation
```bash
# Test deployed buckets for security compliance
./scripts/validate-s3-security.sh bucket1 bucket2 bucket3
```

This script:
- ✅ Verifies public access block settings on live buckets
- ✅ Tests unauthorized access attempts
- ✅ Confirms security policy enforcement

### Manual Validation Steps

#### AWS Management Console
1. Navigate to S3 in the AWS Console
2. Select each bucket
3. Go to "Permissions" tab
4. Verify "Block public access" settings are all enabled

#### AWS CLI Verification
```bash
# Check public access block settings
aws s3api get-public-access-block --bucket your-bucket-name

# Expected output should show all settings as true:
{
    "PublicAccessBlockConfiguration": {
        "BlockPublicAcls": true,
        "IgnorePublicAcls": true,
        "BlockPublicPolicy": true,
        "RestrictPublicBuckets": true
    }
}
```

#### Unauthorized Access Test
```bash
# This should fail if bucket is properly secured
aws s3 cp test-file.txt s3://your-bucket-name/ --no-sign-request
# Expected: Access Denied error
```

## 🔧 Troubleshooting

### Common Issues

#### Bucket Name Already Exists
```
Error: bucket already exists
```
**Solution**: S3 bucket names must be globally unique. Change the `bucket_name` parameter.

#### Insufficient Permissions
```
Error: Access Denied
```
**Solution**: Ensure your AWS credentials have the following permissions:
- `s3:CreateBucket`
- `s3:PutBucketPublicAccessBlock`
- `s3:PutBucketVersioning`
- `s3:PutEncryptionConfiguration`

#### Terraform State Issues
```
Error: backend configuration changed
```
**Solution**: Run `terraform init -reconfigure`

### Security Validation Failures

If validation scripts report security issues:

1. **Check bucket configuration**: Ensure all public access block settings are enabled
2. **Review policies**: Verify no bucket policies allow public access
3. **Check ACLs**: Ensure bucket ACLs are set to private
4. **Test access**: Confirm unauthorized uploads are blocked

## 📋 Compliance and Monitoring

### Compliance Requirements Addressed
- ✅ **PCI DSS**: No public access to sensitive data
- ✅ **NIST 800-53**: Access control and data protection
- ✅ **SOC 2**: Security control implementation
- ✅ **ISO 27001**: Information security management

### Monitoring Recommendations
1. **AWS Config Rules**: Monitor S3 bucket public access settings
2. **CloudTrail**: Log all S3 API activities
3. **AWS Security Hub**: Centralized security findings
4. **Custom CloudWatch Alarms**: Alert on public access configuration changes

### Ongoing Maintenance
- 🔄 **Regular validation**: Run security validation scripts monthly
- 📊 **Access reviews**: Quarterly review of bucket permissions
- 🔍 **Compliance audits**: Annual third-party security assessments
- 🛠️ **Infrastructure updates**: Keep Terraform/CloudFormation templates updated

## 📞 Support

For questions or issues with this security implementation:

1. Review this documentation
2. Run the validation scripts for diagnostics
3. Check AWS CloudTrail logs for permission issues
4. Consult the AWS S3 security documentation

## 🔗 References

- [AWS S3 Security Best Practices](https://docs.aws.amazon.com/AmazonS3/latest/userguide/security-best-practices.html)
- [S3 Block Public Access](https://docs.aws.amazon.com/AmazonS3/latest/userguide/access-control-block-public-access.html)
- [Terraform AWS Provider Documentation](https://registry.terraform.io/providers/hashicorp/aws/latest/docs)
- [CloudFormation S3 Reference](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-properties-s3-bucket.html)