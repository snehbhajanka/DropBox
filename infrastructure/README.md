# AWS S3 Security Configuration

This directory contains infrastructure-as-code templates to address the security misconfiguration:

**Security Issue**: S3 general purpose buckets should block public write access  
**Risk Score**: 10/10 (Critical)  
**Cloud Provider**: AWS  
**Account ID**: 222634381402  

## Security Controls Implemented

### 1. Public Access Block Configuration
- ✅ `block_public_acls = true` - Blocks new public ACLs
- ✅ `block_public_policy = true` - Blocks new public bucket policies  
- ✅ `ignore_public_acls = true` - Ignores existing public ACLs
- ✅ `restrict_public_buckets = true` - Restricts cross-account bucket access

### 2. Additional Security Features
- ✅ **Server-side encryption** enabled with AES256
- ✅ **Versioning** enabled for data protection
- ✅ **Lifecycle policies** for cost optimization
- ✅ **IAM roles and policies** with least privilege access
- ✅ **Resource tagging** for compliance tracking

## Deployment Options

### Option 1: Terraform (Recommended)
```bash
cd infrastructure/terraform
terraform init
terraform plan
terraform apply
```

### Option 2: CloudFormation
```bash
aws cloudformation create-stack \
  --stack-name dropbox-secure-storage \
  --template-body file://infrastructure/cloudformation/s3-secure-storage.yaml \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameters ParameterKey=Environment,ParameterValue=prod
```

## Configuration Parameters

| Parameter | Default Value | Description |
|-----------|--------------|-------------|
| `bucket_name` | `dropbox-storage-secure-222634381402` | S3 bucket name |
| `aws_region` | `us-east-1` | AWS region |
| `environment` | `prod` | Environment tag |

## Compliance Verification

After deployment, verify the security configuration:

```bash
# Check public access block status
aws s3api get-public-access-block --bucket dropbox-storage-secure-222634381402

# Verify encryption
aws s3api get-bucket-encryption --bucket dropbox-storage-secure-222634381402

# Check versioning
aws s3api get-bucket-versioning --bucket dropbox-storage-secure-222634381402
```

## Integration with Application

To integrate with the DropBox Spring Boot application:

1. Add AWS SDK dependency to `pom.xml`
2. Configure AWS credentials via IAM roles or environment variables  
3. Update application configuration in `application.properties`
4. Implement S3 service layer to replace in-memory storage

## Security Best Practices

1. **Never use public buckets** for sensitive data
2. **Enable MFA delete** for production buckets
3. **Use bucket policies** with strict conditions
4. **Monitor access** with CloudTrail and S3 access logs
5. **Regularly audit** IAM permissions
6. **Implement data classification** and retention policies

## Testing

Run the security compliance tests:
```bash
cd infrastructure/tests
python test_s3_security.py
```

## Remediation Status

- [x] S3 bucket configured with public access block
- [x] Server-side encryption enabled
- [x] Versioning enabled for data protection
- [x] IAM roles configured with least privilege
- [x] Lifecycle policies implemented
- [x] Resource tagging applied
- [x] Infrastructure-as-code templates created
- [x] Documentation provided