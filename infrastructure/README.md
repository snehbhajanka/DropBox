# Infrastructure Configuration

This directory contains secure AWS S3 bucket configurations to address the critical security vulnerability regarding public write access (Control ID: S3.3).

## Directory Structure

```
infrastructure/
├── terraform/               # Terraform configuration for secure S3 buckets
│   ├── main.tf              # Main Terraform configuration
│   ├── variables.tf         # Input variables
│   ├── outputs.tf           # Output values
│   └── terraform.tfvars.example # Example variables file
└── cloudformation/          # CloudFormation templates
    └── s3-secure-bucket.yaml # Secure S3 bucket template
```

## Quick Start

### Using Terraform

1. **Initialize and configure**:
   ```bash
   cd infrastructure/terraform
   cp terraform.tfvars.example terraform.tfvars
   # Edit terraform.tfvars with your bucket name
   ```

2. **Deploy the infrastructure**:
   ```bash
   terraform init
   terraform plan
   terraform apply
   ```

### Using CloudFormation

1. **Deploy via AWS CLI**:
   ```bash
   aws cloudformation deploy \
     --template-file infrastructure/cloudformation/s3-secure-bucket.yaml \
     --stack-name dropbox-secure-storage \
     --parameter-overrides BucketName=your-unique-bucket-name-12345
   ```

2. **Deploy via AWS Console**:
   - Upload the `s3-secure-bucket.yaml` template
   - Provide required parameters
   - Deploy the stack

## Security Features

All configurations implement the following security controls:

- ✅ **Block Public ACLs**: Prevents new public ACLs from being applied
- ✅ **Ignore Public ACLs**: Ignores any existing public ACLs
- ✅ **Block Public Policy**: Blocks public access granted through bucket policies
- ✅ **Restrict Public Buckets**: Restricts access to buckets with public policies
- ✅ **Server-side Encryption**: AES256 encryption enabled by default
- ✅ **Versioning**: Object versioning enabled for data protection
- ✅ **Lifecycle Management**: Automated transition to cost-effective storage classes

## Validation

After deployment, validate the security configuration:

```bash
# Using the validation script
./scripts/validate-s3-security.sh your-bucket-name

# Using AWS CLI
aws s3api get-public-access-block --bucket your-bucket-name
```

Expected output should show all values as `true`:
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

## Customization

### Terraform Variables

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `bucket_name` | S3 bucket name (must be globally unique) | - | ✅ |
| `aws_region` | AWS region for deployment | `us-east-1` | ❌ |
| `environment` | Environment tag (dev/staging/prod) | `dev` | ❌ |

### CloudFormation Parameters

| Parameter | Description | Default | Required |
|-----------|-------------|---------|----------|
| `BucketName` | S3 bucket name (must be globally unique) | - | ✅ |
| `Environment` | Environment tag (dev/staging/prod) | `dev` | ❌ |

## Cost Optimization

The configurations include lifecycle policies to optimize storage costs:

- **Standard-IA**: Objects transition after 30 days
- **Cleanup**: Incomplete multipart uploads deleted after 7 days

## Monitoring

Monitor your bucket security with:

1. **AWS Config Rule**: `s3-bucket-public-access-prohibited`
2. **CloudWatch Alarms**: Monitor for configuration changes
3. **CloudTrail**: Track API calls related to public access settings

## Troubleshooting

### Common Issues

1. **Bucket Name Conflicts**: S3 bucket names must be globally unique
   - Solution: Use a more specific naming pattern (e.g., include account ID or timestamp)

2. **Permission Errors**: Ensure IAM user/role has required permissions
   - Required: `s3:PutBucketPublicAccessBlock`, `s3:GetBucketPublicAccessBlock`

3. **State File Conflicts** (Terraform): Multiple users modifying state
   - Solution: Use remote state with state locking (S3 + DynamoDB)

### Getting Help

- Review the [S3 Security Remediation Documentation](../docs/S3-SECURITY-REMEDIATION.md)
- Check AWS CloudTrail logs for detailed error information
- Contact your AWS support team for account-specific issues

## Compliance

These configurations help achieve compliance with:

- **NIST 800-53** (Access Control family)
- **PCI DSS** (Requirement 7)
- **SOC 2** (CC6.1)
- **ISO 27001** (A.9.1.2)

---

**Security Level**: High 🔒  
**Risk Mitigation**: Critical vulnerability addressed  
**Compliance**: Multiple frameworks supported