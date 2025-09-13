# DropBox S3 Infrastructure

This directory contains Terraform configuration files to create and manage secure S3 buckets for the DropBox application.

## Security Configuration

This Terraform configuration addresses the **CRITICAL** security misconfiguration S3.3 by implementing comprehensive public access blocking on all S3 buckets.

### Security Features Implemented

- **Block Public ACLs**: Prevents new public ACLs and uploading of public objects
- **Ignore Public ACLs**: Ignores all public ACLs on buckets and contained objects
- **Block Public Policy**: Prevents users from putting bucket policies that grant public access
- **Restrict Public Buckets**: Restricts access to AWS service principals and authorized users only

### Additional Security Features

- **Server-Side Encryption**: All buckets use AES256 encryption by default
- **Versioning**: Object versioning is enabled for data protection
- **Lifecycle Management**: Automatic transition to cost-effective storage classes

## Usage

1. **Prerequisites**
   - AWS CLI configured with appropriate permissions
   - Terraform >= 1.0 installed

2. **Configuration**
   ```bash
   # Copy and customize the example variables file
   cp terraform.tfvars.example terraform.tfvars
   
   # Edit terraform.tfvars with your specific values
   ```

3. **Deployment**
   ```bash
   # Initialize Terraform
   terraform init
   
   # Plan the deployment
   terraform plan
   
   # Apply the configuration
   terraform apply
   ```

4. **Validation**
   ```bash
   # Verify public access block settings
   aws s3api get-public-access-block --bucket <bucket-name>
   
   # Test unauthorized access (should be denied)
   aws s3 cp test-file.txt s3://<bucket-name>/ --profile unauthorized-profile
   ```

## Variables

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `aws_region` | AWS region for resources | `us-east-1` | No |
| `bucket_count` | Number of S3 buckets to create | `13` | No |
| `bucket_prefix` | Prefix for bucket names | `dropbox-storage` | No |
| `environment` | Environment identifier | `prod` | No |
| `enable_versioning` | Enable S3 versioning | `true` | No |

## Outputs

- `bucket_names`: List of created bucket names
- `bucket_arns`: List of bucket ARNs
- `bucket_domains`: List of bucket domain names
- `public_access_block_status`: Security configuration status for all buckets
- `aws_account_id`: AWS account ID

## Compliance

This configuration ensures compliance with:
- PCI DSS requirements for data protection
- NIST 800-53 security controls
- AWS security best practices

## Verification Commands

After deployment, verify the security configuration:

```bash
# Check public access block settings
aws s3api get-public-access-block --bucket dropbox-storage-1

# Verify encryption configuration
aws s3api get-bucket-encryption --bucket dropbox-storage-1

# Test unauthorized access (should fail)
curl -X PUT https://dropbox-storage-1.s3.amazonaws.com/test-file.txt
```

Expected response for public access attempts: `403 Forbidden`