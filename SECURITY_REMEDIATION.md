# Security Remediation Guide: S3 Public Write Access

This document provides step-by-step instructions to remediate the **S3.3 Security Issue** - Block Public Write Access for the DropBox application.

## 🚨 Security Issue Overview

- **Issue ID**: S3.3
- **Severity**: CRITICAL  
- **Risk Score**: 10/10
- **Affected Resources**: 13 S3 buckets with public write access
- **Account ID**: 222634381402

## 🎯 Remediation Objectives

1. Block all public write access to S3 buckets
2. Implement security controls to prevent future misconfigurations
3. Validate security settings through automated testing
4. Ensure compliance with security standards (PCI DSS, NIST 800-53)

## 📋 Prerequisites

Before starting the remediation:

- [ ] AWS CLI installed and configured
- [ ] Appropriate AWS IAM permissions for S3 operations
- [ ] Terraform >= 1.0 (for Terraform deployment)
- [ ] Valid AWS credentials for account 222634381402

### Required IAM Permissions

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "s3:CreateBucket",
                "s3:DeleteBucket",
                "s3:GetBucketPublicAccessBlock",
                "s3:PutBucketPublicAccessBlock",
                "s3:PutBucketVersioning",
                "s3:PutEncryptionConfiguration",
                "s3:PutBucketAcl",
                "s3:ListBucket"
            ],
            "Resource": [
                "arn:aws:s3:::*",
                "arn:aws:s3:::*/*"
            ]
        }
    ]
}
```

## 🛠️ Remediation Steps

### Step 1: Deploy Secure Infrastructure

Choose one of the following deployment methods:

#### Option A: Terraform Deployment (Recommended)

```bash
# 1. Navigate to Terraform directory
cd infrastructure/terraform

# 2. Copy and customize variables
cp terraform.tfvars.example terraform.tfvars

# 3. Edit terraform.tfvars with your bucket names
# Replace with your actual bucket names from the 13 affected buckets
vim terraform.tfvars

# 4. Initialize Terraform
terraform init

# 5. Review the security plan
terraform plan

# 6. Apply the secure configuration
terraform apply
```

#### Option B: CloudFormation Deployment

```bash
# 1. Navigate to CloudFormation directory
cd infrastructure/cloudformation

# 2. Customize parameters
vim parameters.json

# 3. Validate template
aws cloudformation validate-template --template-body file://s3-security.yml

# 4. Deploy stack
aws cloudformation create-stack \
  --stack-name dropbox-s3-security \
  --template-body file://s3-security.yml \
  --parameters file://parameters.json

# 5. Monitor deployment
aws cloudformation describe-stack-events --stack-name dropbox-s3-security
```

### Step 2: Apply Security Settings to Existing Buckets

For the 13 affected buckets that already exist, apply security settings directly:

```bash
# List all buckets to identify the affected ones
aws s3api list-buckets --query 'Buckets[].Name' --output table

# For each affected bucket, apply public access block
BUCKET_NAME="your-affected-bucket-name"

aws s3api put-public-access-block \
  --bucket $BUCKET_NAME \
  --public-access-block-configuration \
  BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
```

### Step 3: Enable Additional Security Features

```bash
# Enable versioning (recommended for data protection)
aws s3api put-bucket-versioning \
  --bucket $BUCKET_NAME \
  --versioning-configuration Status=Enabled

# Enable server-side encryption
aws s3api put-bucket-encryption \
  --bucket $BUCKET_NAME \
  --server-side-encryption-configuration '{
    "Rules": [
      {
        "ApplyServerSideEncryptionByDefault": {
          "SSEAlgorithm": "AES256"
        },
        "BucketKeyEnabled": true
      }
    ]
  }'
```

## ✅ Validation and Testing

### Automated Validation

Run the provided validation scripts to ensure security compliance:

```bash
# 1. Validate infrastructure configurations
./scripts/validate-infrastructure.sh

# 2. Test live bucket security settings
# Replace with your actual bucket names
./scripts/validate-s3-security.sh bucket1 bucket2 bucket3
```

### Manual Validation

#### AWS CLI Verification

```bash
# Check public access block settings for each bucket
aws s3api get-public-access-block --bucket your-bucket-name

# Expected output:
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
# Create a test file
echo "Test upload attempt" > test-unauthorized.txt

# Try to upload without proper authentication (should fail)
aws s3 cp test-unauthorized.txt s3://your-bucket-name/ --no-sign-request

# Expected result: Access Denied error
# Clean up
rm test-unauthorized.txt
```

### AWS Management Console Verification

1. Open the [AWS S3 Console](https://console.aws.amazon.com/s3/)
2. Select each affected bucket
3. Navigate to the "Permissions" tab
4. Verify "Block public access" settings:
   - ✅ Block public access to buckets and objects granted through new access control lists (ACLs)
   - ✅ Block public access to buckets and objects granted through any access control lists (ACLs)
   - ✅ Block public access to buckets and objects granted through new public bucket or access point policies
   - ✅ Block public access to buckets and objects granted through any public bucket or access point policies

## 📊 Compliance Verification

### Security Standards Addressed

- ✅ **PCI DSS Requirement 7**: Restrict access to cardholder data
- ✅ **NIST 800-53 AC-3**: Access Enforcement
- ✅ **SOC 2 CC6.1**: Logical and Physical Access Controls
- ✅ **ISO 27001 A.9.1**: Access Control Policy

### Documentation Requirements

Create documentation showing:
- [ ] Before and after configurations
- [ ] Validation test results
- [ ] Access control policies implemented
- [ ] Monitoring and alerting setup

## 🔍 Monitoring and Alerting

### AWS Config Rules (Recommended)

Deploy AWS Config rules to monitor ongoing compliance:

```bash
# Deploy S3 bucket public access prohibited rule
aws configservice put-config-rule \
  --config-rule '{
    "ConfigRuleName": "s3-bucket-public-access-prohibited",
    "Source": {
      "Owner": "AWS",
      "SourceIdentifier": "S3_BUCKET_PUBLIC_ACCESS_PROHIBITED"
    }
  }'
```

### CloudWatch Alarms

Set up alarms for configuration changes:

```bash
# Create alarm for S3 bucket policy changes
aws cloudwatch put-metric-alarm \
  --alarm-name "S3-Bucket-Policy-Changes" \
  --alarm-description "Alarm for S3 bucket policy changes" \
  --metric-name "PolicyChangeCount" \
  --namespace "AWS/S3" \
  --statistic "Sum" \
  --period 300 \
  --threshold 1 \
  --comparison-operator "GreaterThanOrEqualToThreshold" \
  --evaluation-periods 1
```

## 🔄 Ongoing Maintenance

### Monthly Tasks
- [ ] Run security validation scripts
- [ ] Review access logs for unauthorized attempts
- [ ] Update infrastructure configurations if needed

### Quarterly Tasks
- [ ] Review bucket permissions and policies
- [ ] Audit compliance with security standards
- [ ] Update documentation

### Annual Tasks
- [ ] Third-party security assessment
- [ ] Infrastructure security review
- [ ] Update incident response procedures

## ❓ Troubleshooting

### Common Issues and Solutions

#### Issue: "Access Denied" during remediation
**Solution**: Verify IAM permissions include all required S3 actions

#### Issue: Bucket name conflicts
**Solution**: S3 bucket names must be globally unique. Use organization-specific prefixes

#### Issue: Application errors after applying security settings
**Solution**: Update application code to use proper AWS credentials and IAM roles

#### Issue: Validation scripts fail
**Solution**: Check AWS CLI configuration and bucket names

### Getting Help

1. Check the infrastructure README: `infrastructure/README.md`
2. Review AWS CloudTrail logs for permission issues
3. Run validation scripts with verbose output
4. Consult AWS S3 security documentation

## 🎉 Success Criteria

The remediation is complete when:

- ✅ All 13 affected buckets have public access blocked
- ✅ Validation scripts pass without errors
- ✅ Unauthorized access tests are blocked
- ✅ AWS Config rules show compliant status
- ✅ Documentation is updated
- ✅ Monitoring is configured

## 📞 Emergency Contacts

If critical issues arise during remediation:

1. **AWS Support**: Open a support case in AWS Console
2. **Security Team**: Follow your organization's incident response procedures
3. **Infrastructure Team**: Contact on-call engineer

---

**Remember**: This remediation addresses a CRITICAL security issue. Complete all steps and validation before considering the issue resolved.