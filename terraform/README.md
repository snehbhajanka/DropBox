# DropBox S3 Security Configuration

This directory contains Terraform infrastructure code that addresses the **CRITICAL** security misconfiguration **S3.3 - Block Public Write Access**.

## Security Issue Addressed

**Problem**: S3 buckets with public write access pose a significant security risk, allowing anyone on the internet to upload, modify, or delete objects.

**Solution**: This Terraform configuration creates S3 buckets with all public access blocked, implementing the following security controls:

- ✅ `block_public_acls = true` - Prevents new public ACLs from being applied
- ✅ `ignore_public_acls = true` - Ignores any existing public ACLs  
- ✅ `block_public_policy = true` - Prevents public bucket policies
- ✅ `restrict_public_buckets = true` - Restricts access to buckets with public policies

## Quick Start

1. **Configure AWS credentials** (using AWS CLI, environment variables, or IAM roles)

2. **Set up Terraform variables**:
   ```bash
   cp terraform.tfvars.example terraform.tfvars
   # Edit terraform.tfvars with your specific configuration
   ```

3. **Initialize and apply Terraform**:
   ```bash
   cd terraform
   terraform init
   terraform plan
   terraform apply
   ```

## Validation Commands

After deployment, verify the security configuration:

```bash
# Check public access block settings
aws s3api get-public-access-block --bucket <your-bucket-name>

# Verify all settings are true:
# - BlockPublicAcls: true
# - IgnorePublicAcls: true  
# - BlockPublicPolicy: true
# - RestrictPublicBuckets: true
```

## Security Features

- **Public Access Blocking**: All four public access block settings enabled
- **Encryption**: Server-side encryption with AES256
- **Versioning**: Object versioning enabled for data protection
- **Lifecycle Management**: Automatic cleanup of old versions after 90 days

## Compliance

This configuration helps meet security standards including:
- PCI DSS requirements
- NIST 800-53 controls
- AWS Security Best Practices
- SOC 2 compliance requirements

## Files

- `main.tf` - Main Terraform configuration with S3 resources
- `variables.tf` - Input variables and validation
- `outputs.tf` - Output values for integration
- `terraform.tfvars.example` - Example configuration file

## Support

For questions about this security configuration, refer to:
- [AWS S3 Security Best Practices](https://docs.aws.amazon.com/AmazonS3/latest/userguide/security-best-practices.html)
- [Terraform AWS Provider Documentation](https://registry.terraform.io/providers/hashicorp/aws/latest/docs)