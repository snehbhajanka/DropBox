# AWS S3 Security Configuration

This directory contains Infrastructure as Code (IaC) templates to deploy secure S3 buckets for the DropBox application. The configuration addresses the critical security requirement to **block public write access** to S3 buckets as identified in AWS Security Hub control `S3.3`.

## Security Features Implemented

✅ **Block Public ACLs**: Prevents setting public ACLs on bucket and objects  
✅ **Ignore Public ACLs**: Ignores all public ACLs on bucket and objects  
✅ **Block Public Bucket Policies**: Prevents setting public bucket policies  
✅ **Restrict Public Buckets**: Restricts public read access to buckets with public policies  

## Deployment Options

### Option 1: Terraform (Recommended)

**Prerequisites:**
- Terraform >= 1.0
- AWS CLI configured with appropriate permissions
- IAM role/user with S3 and IAM permissions

**Steps:**
1. Navigate to the terraform directory:
   ```bash
   cd terraform/
   ```

2. Copy and customize the variables file:
   ```bash
   cp terraform.tfvars.example terraform.tfvars
   # Edit terraform.tfvars with your specific values
   ```

3. Initialize Terraform:
   ```bash
   terraform init
   ```

4. Plan the deployment:
   ```bash
   terraform plan
   ```

5. Apply the configuration:
   ```bash
   terraform apply
   ```

### Option 2: CloudFormation

**Prerequisites:**
- AWS CLI configured with appropriate permissions
- IAM role/user with S3 and CloudFormation permissions

**Steps:**
1. Navigate to the cloudformation directory:
   ```bash
   cd cloudformation/
   ```

2. Copy and customize the parameters file:
   ```bash
   cp parameters.json.example parameters.json
   # Edit parameters.json with your specific values
   ```

3. Deploy the stack:
   ```bash
   aws cloudformation create-stack \
     --stack-name dropbox-s3-security \
     --template-body file://s3-security.yaml \
     --parameters file://parameters.json \
     --capabilities CAPABILITY_IAM
   ```

## Required IAM Permissions

The deployment requires the following minimum IAM permissions:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:CreateBucket",
        "s3:DeleteBucket",
        "s3:GetBucketAcl",
        "s3:GetBucketPolicy",
        "s3:GetBucketPublicAccessBlock",
        "s3:GetBucketVersioning",
        "s3:GetEncryptionConfiguration",
        "s3:GetLifecycleConfiguration",
        "s3:PutBucketAcl",
        "s3:PutBucketPolicy",
        "s3:PutBucketPublicAccessBlock",
        "s3:PutBucketVersioning",
        "s3:PutEncryptionConfiguration",
        "s3:PutLifecycleConfiguration"
      ],
      "Resource": "*"
    }
  ]
}
```

## Validation and Testing

### Console Verification
1. Navigate to the AWS Management Console
2. Open the Amazon S3 console
3. Select the created bucket
4. Go to "Permissions" tab
5. Verify that "Block public access (bucket settings)" shows:
   - ✅ Block public access to buckets and objects granted through new access control lists (ACLs)
   - ✅ Block public access to buckets and objects granted through any access control lists (ACLs)
   - ✅ Block public access to buckets and objects granted through new public bucket or access point policies
   - ✅ Block public access to buckets and objects granted through any public bucket or access point policies

### CLI Verification
```bash
# Replace <bucket-name> with your actual bucket name
aws s3api get-public-access-block --bucket <bucket-name>
```

Expected output:
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

### Functional Testing
```bash
# This should fail with access denied
aws s3 cp test-file.txt s3://<bucket-name>/test-file.txt --no-sign-request
```

## Security Compliance

This configuration ensures compliance with:
- **AWS Security Hub Control S3.3**: S3 general purpose buckets should block public write access
- **AWS Config Rule**: s3-bucket-public-write-prohibited
- **CIS AWS Foundations Benchmark**: 2.1.2
- **PCI DSS**: Requirement 7 (Restrict access to cardholder data)
- **NIST 800-53**: AC-3 (Access Enforcement)

## Cost Optimization Features

- **Lifecycle Policies**: Automatic transition to lower-cost storage classes
  - Standard → Standard-IA (30 days)
  - Standard-IA → Glacier (90 days)
  - Glacier → Deep Archive (365 days)
- **Incomplete Multipart Upload Cleanup**: Automatically delete incomplete uploads after 7 days

## Monitoring and Alerts

Consider setting up the following monitoring:
- CloudTrail for S3 API calls
- CloudWatch alarms for bucket policy changes
- AWS Config rules for compliance monitoring
- GuardDuty for threat detection

## Troubleshooting

### Common Issues

1. **Bucket name already exists**
   - Solution: Choose a globally unique bucket name

2. **Insufficient permissions**
   - Solution: Ensure IAM user/role has required S3 permissions

3. **Public access block conflicts**
   - Solution: This is expected - the configuration intentionally blocks public access

### Support

For issues related to this configuration, please check:
1. AWS CloudFormation/Terraform logs
2. IAM permissions
3. AWS Service Health Dashboard
4. AWS Support (if you have a support plan)