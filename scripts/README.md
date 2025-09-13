# Validation Scripts

This directory contains scripts to validate and deploy the secure S3 infrastructure.

## Scripts

### `validate-s3-security.sh`

Comprehensive security validation script that checks:
- Public Access Block settings (all four controls)
- Bucket ACL configuration
- Public write access testing
- Server-side encryption settings

**Usage:**
```bash
./scripts/validate-s3-security.sh [bucket-name] [aws-region]
./scripts/validate-s3-security.sh dropbox-secure-storage us-east-1
```

**Requirements:**
- AWS CLI configured with appropriate permissions
- jq (for JSON parsing)

### `deploy-infrastructure.sh`

Automated deployment script for the secure S3 infrastructure using Terraform.

**Usage:**
```bash
./scripts/deploy-infrastructure.sh
```

**What it does:**
1. Initializes Terraform
2. Plans the deployment
3. Prompts for confirmation
4. Applies the configuration
5. Validates the security settings
6. Shows deployment outputs

**Requirements:**
- Terraform installed
- AWS credentials configured
- Appropriate IAM permissions for S3 and IAM operations

## Security Validation Checklist

The validation scripts check compliance with:

### ✅ S3 Security Controls
- [ ] Block Public ACLs = `true`
- [ ] Ignore Public ACLs = `true`
- [ ] Block Public Policy = `true`
- [ ] Restrict Public Buckets = `true`

### ✅ Additional Security Features
- [ ] Private bucket ACL
- [ ] No public grants in ACL
- [ ] Public write access blocked
- [ ] Server-side encryption enabled

### ✅ Compliance Standards
- [ ] AWS Security Hub S3.3 control
- [ ] PCI DSS requirements
- [ ] NIST 800-53 guidelines

## Continuous Monitoring

### Automated Checks
Set up the validation script to run periodically:

```bash
# Add to crontab for weekly validation
0 2 * * 1 /path/to/validate-s3-security.sh bucket-name >> /var/log/s3-validation.log 2>&1
```

### AWS Config Rules
Enable AWS Config rules for continuous compliance monitoring:
- `s3-bucket-public-access-prohibited`
- `s3-bucket-public-read-prohibited`
- `s3-bucket-public-write-prohibited`

### CloudWatch Alarms
Set up CloudWatch alarms for:
- Bucket policy changes
- ACL modifications
- Public access configuration changes

## Troubleshooting

### Common Issues

**AWS CLI not found**
```bash
# Install AWS CLI
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install
```

**Permissions denied**
Ensure your AWS credentials have the following permissions:
```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "s3:GetBucketPublicAccessBlock",
                "s3:GetBucketAcl",
                "s3:GetBucketEncryption",
                "s3:ListBucket"
            ],
            "Resource": "arn:aws:s3:::*"
        }
    ]
}
```

**Terraform not found**
```bash
# Install Terraform
wget -O- https://apt.releases.hashicorp.com/gpg | gpg --dearmor | sudo tee /usr/share/keyrings/hashicorp-archive-keyring.gpg
echo "deb [signed-by=/usr/share/keyrings/hashicorp-archive-keyring.gpg] https://apt.releases.hashicorp.com $(lsb_release -cs) main" | sudo tee /etc/apt/sources.list.d/hashicorp.list
sudo apt update && sudo apt install terraform
```

## Emergency Response

If security validation fails:

1. **Immediate Action**: Re-apply block public access settings
   ```bash
   aws s3api put-public-access-block --bucket BUCKET_NAME --public-access-block-configuration "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"
   ```

2. **Investigation**: Check CloudTrail logs for unauthorized changes
   ```bash
   aws logs filter-log-events --log-group-name CloudTrail/S3DataEvents --filter-pattern "{ $.eventName = PutBucketAcl || $.eventName = PutBucketPolicy }"
   ```

3. **Validation**: Re-run security validation
   ```bash
   ./scripts/validate-s3-security.sh BUCKET_NAME
   ```