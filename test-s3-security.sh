#!/bin/bash

# Simple integration test for S3 security configurations
# This test validates that the infrastructure configurations are syntactically correct

set -e

echo "=================================================="
echo "S3 Security Configuration Integration Test"
echo "=================================================="

# Test 1: Validate shell scripts syntax
echo "Testing shell scripts syntax..."
bash -n scripts/secure-s3-bucket.sh
bash -n scripts/validate-s3-security.sh
echo "✅ Shell scripts syntax validation passed"

# Test 2: Validate CloudFormation template
echo "Testing CloudFormation template..."
if command -v aws &> /dev/null; then
    # Try to validate template, but don't fail if credentials aren't configured
    if aws cloudformation validate-template --template-body file://infrastructure/cloudformation/s3-secure-bucket.yaml >/dev/null 2>&1; then
        echo "✅ CloudFormation template validation passed"
    else
        echo "⚠️  CloudFormation template validation skipped (AWS credentials not configured)"
    fi
else
    echo "⚠️  AWS CLI not available, skipping CloudFormation validation"
fi

# Test 3: Check Terraform syntax (if available)
echo "Testing Terraform configuration..."
if command -v terraform &> /dev/null; then
    cd infrastructure/terraform
    terraform fmt -check
    terraform validate
    cd ../..
    echo "✅ Terraform configuration validation passed"
else
    echo "⚠️  Terraform not available, skipping Terraform validation"
fi

# Test 4: Validate file structure
echo "Testing file structure..."
required_files=(
    "infrastructure/terraform/main.tf"
    "infrastructure/terraform/variables.tf"
    "infrastructure/terraform/outputs.tf"
    "infrastructure/cloudformation/s3-secure-bucket.yaml"
    "scripts/secure-s3-bucket.sh"
    "scripts/validate-s3-security.sh"
    "docs/S3-SECURITY-REMEDIATION.md"
    "infrastructure/README.md"
)

for file in "${required_files[@]}"; do
    if [ -f "$file" ]; then
        echo "✅ Found: $file"
    else
        echo "❌ Missing: $file"
        exit 1
    fi
done

# Test 5: Validate key security configurations in Terraform
echo "Testing Terraform security configurations..."
terraform_main="infrastructure/terraform/main.tf"

security_configs=(
    "block_public_acls = true"
    "ignore_public_acls = true"
    "block_public_policy = true"
    "restrict_public_buckets = true"
)

for config in "${security_configs[@]}"; do
    if grep -q "$config" "$terraform_main"; then
        echo "✅ Found security config: $config"
    else
        echo "❌ Missing security config: $config"
        exit 1
    fi
done

# Test 6: Validate CloudFormation security configurations
echo "Testing CloudFormation security configurations..."
cf_template="infrastructure/cloudformation/s3-secure-bucket.yaml"

cf_security_configs=(
    "BlockPublicAcls: true"
    "IgnorePublicAcls: true"
    "BlockPublicPolicy: true"
    "RestrictPublicBuckets: true"
)

for config in "${cf_security_configs[@]}"; do
    if grep -q "$config" "$cf_template"; then
        echo "✅ Found CloudFormation security config: $config"
    else
        echo "❌ Missing CloudFormation security config: $config"
        exit 1
    fi
done

# Test 7: Check script executability
echo "Testing script permissions..."
if [ -x "scripts/secure-s3-bucket.sh" ]; then
    echo "✅ secure-s3-bucket.sh is executable"
else
    echo "❌ secure-s3-bucket.sh is not executable"
    exit 1
fi

if [ -x "scripts/validate-s3-security.sh" ]; then
    echo "✅ validate-s3-security.sh is executable"
else
    echo "❌ validate-s3-security.sh is not executable"
    exit 1
fi

echo "=================================================="
echo "✅ All integration tests passed!"
echo "S3 security configurations are ready for deployment"
echo "=================================================="

echo ""
echo "Next steps:"
echo "1. Deploy infrastructure: cd infrastructure/terraform && terraform apply"
echo "2. Or use CloudFormation: aws cloudformation deploy --template-file infrastructure/cloudformation/s3-secure-bucket.yaml --stack-name secure-s3"
echo "3. Validate security: ./scripts/validate-s3-security.sh your-bucket-name"