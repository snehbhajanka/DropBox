# S3 Security Remediation

This Terraform configuration addresses the **CRITICAL** security misconfiguration S3.3 by implementing secure S3 buckets with proper public access blocking.

## Security Features Implemented

### Block Public Access (BPA) Settings
All S3 buckets are configured with the following security settings to prevent public write access:

- **BlockPublicAcls**: `true` - Prevents setting public ACLs on bucket/objects
- **IgnorePublicAcls**: `true` - Treats public ACLs as non-public  
- **BlockPublicPolicy**: `true` - Prevents setting public bucket policies
- **RestrictPublicBuckets**: `true` - Restricts cross-account access to buckets with public policies

### Additional Security Controls

1. **Server-Side Encryption**: AES256 encryption for all objects
2. **Bucket Versioning**: Enabled for data protection and recovery
3. **Lifecycle Management**: Automated transition to cost-effective storage classes
4. **Explicit Deny Policy**: Additional bucket policy to deny public access
5. **Account-Level Protection**: Resources restricted to current AWS account only

## Usage

### Prerequisites
- AWS CLI configured with appropriate credentials
- Terraform >= 1.0 installed
- Sufficient AWS permissions to create S3 buckets and policies

### Deployment

1. **Initialize Terraform**:
   ```bash
   cd terraform
   terraform init
   ```

2. **Create Variables File**:
   ```bash
   cp terraform.tfvars.example terraform.tfvars
   # Edit terraform.tfvars with your specific configuration
   ```

3. **Plan Deployment**:
   ```bash
   terraform plan
   ```

4. **Apply Configuration**:
   ```bash
   terraform apply
   ```

### Validation

After deployment, verify the security configuration:

1. **Check Block Public Access Settings**:
   ```bash
   aws s3api get-public-access-block --bucket <bucket-name>
   ```

2. **Verify All Settings are True**:
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

3. **Test Public Access Prevention**:
   ```bash
   # This should fail with access denied
   aws s3 cp test-file.txt s3://<bucket-name>/ --no-sign-request
   ```

## Compliance

This configuration addresses:
- **AWS Config Rule**: s3-bucket-public-read-prohibited
- **AWS Config Rule**: s3-bucket-public-write-prohibited  
- **CIS AWS Foundations Benchmark**: 2.1.1, 2.1.2
- **NIST 800-53**: AC-3, AC-4, SC-7
- **PCI DSS**: Requirement 1, 2

## Resources Created

- **Primary Storage Bucket**: For main file storage with security controls
- **Backup Bucket**: For backup and archival with identical security controls
- **Public Access Block**: Applied to both buckets
- **Bucket Policies**: Explicit deny policies for additional protection
- **Encryption Configuration**: Server-side encryption for data at rest
- **Lifecycle Policies**: Cost optimization through storage class transitions

## Outputs

- `storage_bucket_name`: Name of the primary storage bucket
- `backup_bucket_name`: Name of the backup bucket
- `storage_bucket_arn`: ARN of the primary storage bucket
- `backup_bucket_arn`: ARN of the backup bucket