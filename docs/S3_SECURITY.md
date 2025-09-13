# S3 Security Configuration Documentation

## Overview
This repository contains security configurations for AWS S3 buckets used by the DropBox application. All configurations are designed to block public write access and follow AWS security best practices.

## Security Configurations Applied

### 1. Block Public Access Settings
All S3 buckets are configured with the following critical security settings:

- **BlockPublicAcls**: `true` - Blocks new public ACLs
- **IgnorePublicAcls**: `true` - Ignores existing public ACLs  
- **BlockPublicPolicy**: `true` - Blocks public bucket policies
- **RestrictPublicBuckets**: `true` - Restricts public bucket access

### 2. Infrastructure as Code

#### Terraform Configuration
Location: `infrastructure/terraform/s3-secure-bucket.tf`

```bash
# Deploy with Terraform
cd infrastructure/terraform
terraform init
terraform plan -var="bucket_name=your-bucket-name"
terraform apply
```

#### CloudFormation Template
Location: `infrastructure/cloudformation/s3-secure-bucket.yaml`

```bash
# Deploy with AWS CLI
aws cloudformation create-stack \
  --stack-name dropbox-s3-security \
  --template-body file://infrastructure/cloudformation/s3-secure-bucket.yaml \
  --parameters ParameterKey=BucketName,ParameterValue=your-bucket-name
```

### 3. Application Configuration
The Spring Boot application includes AWS S3 configuration with security properties in:
- `src/main/resources/application-aws.properties`
- `src/main/java/com/dropbox/application/config/S3Config.java`

### 4. Security Validation

#### Automated Script
Use the provided script to validate bucket security:

```bash
# Validate security settings
./scripts/validate-s3-security.sh your-bucket-name validate

# Apply security settings
./scripts/validate-s3-security.sh your-bucket-name apply

# Test unauthorized access (should be blocked)
./scripts/validate-s3-security.sh your-bucket-name test
```

#### Manual Validation with AWS CLI

```bash
# Check public access block settings
aws s3api get-public-access-block --bucket your-bucket-name

# Verify bucket ACL
aws s3api get-bucket-acl --bucket your-bucket-name

# Test upload (should fail for unauthorized users)
aws s3 cp test.txt s3://your-bucket-name/test.txt
```

## Compliance and Risk Mitigation

### Risk Addressed
- **CRITICAL**: S3.3 - Public write access blocked
- **Impact**: Prevents unauthorized data uploads, deletions, or modifications
- **Compliance**: Supports PCI DSS, NIST 800-53 requirements

### Security Benefits
1. **Data Integrity**: Prevents unauthorized modifications
2. **Access Control**: Restricts bucket access to authorized users only
3. **Compliance**: Meets regulatory requirements
4. **Cost Protection**: Prevents unauthorized usage costs

### Monitoring and Alerts
Consider implementing:
- AWS CloudTrail for API logging
- AWS Config for compliance monitoring
- CloudWatch alarms for unusual access patterns

## Troubleshooting

### Common Issues
1. **Access Denied Errors**: Verify IAM permissions for legitimate users
2. **Application Upload Failures**: Ensure application uses proper IAM roles
3. **Terraform/CloudFormation Failures**: Check AWS credentials and permissions

### Support
For security-related issues, contact the security team immediately.
Report any suspected public access through proper incident response channels.