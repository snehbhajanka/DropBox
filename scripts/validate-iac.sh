#!/bin/bash

# Basic syntax validation for Infrastructure as Code templates
# This script performs basic validation without requiring terraform/aws-cli to be installed

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR/.."

echo "🔍 Infrastructure as Code Validation"
echo "===================================="

# Test 1: Check file structure
echo "📋 Test 1: File Structure Validation"
echo "------------------------------------"

REQUIRED_TERRAFORM_FILES=(
    "terraform/main.tf"
    "terraform/variables.tf"
    "terraform/outputs.tf"
    "terraform/terraform.tfvars.example"
)

REQUIRED_CLOUDFORMATION_FILES=(
    "cloudformation/s3-security.yaml"
    "cloudformation/parameters.json.example"
)

REQUIRED_DOCS=(
    "docs/AWS_S3_SECURITY.md"
)

REQUIRED_SCRIPTS=(
    "scripts/deploy-terraform.sh"
    "scripts/deploy-cloudformation.sh"
    "scripts/validate-s3-security.sh"
)

for file in "${REQUIRED_TERRAFORM_FILES[@]}"; do
    if [ -f "$PROJECT_DIR/$file" ]; then
        echo "✅ $file exists"
    else
        echo "❌ $file missing"
        exit 1
    fi
done

for file in "${REQUIRED_CLOUDFORMATION_FILES[@]}"; do
    if [ -f "$PROJECT_DIR/$file" ]; then
        echo "✅ $file exists"
    else
        echo "❌ $file missing"
        exit 1
    fi
done

for file in "${REQUIRED_DOCS[@]}"; do
    if [ -f "$PROJECT_DIR/$file" ]; then
        echo "✅ $file exists"
    else
        echo "❌ $file missing"
        exit 1
    fi
done

for file in "${REQUIRED_SCRIPTS[@]}"; do
    if [ -f "$PROJECT_DIR/$file" ] && [ -x "$PROJECT_DIR/$file" ]; then
        echo "✅ $file exists and is executable"
    else
        echo "❌ $file missing or not executable"
        exit 1
    fi
done

# Test 2: Basic syntax validation for Terraform files
echo ""
echo "🔧 Test 2: Terraform Syntax Validation"
echo "--------------------------------------"

# Check for basic Terraform syntax issues in main.tf
if grep -q "resource \"aws_s3_bucket\"" "$PROJECT_DIR/terraform/main.tf"; then
    echo "✅ S3 bucket resource found"
else
    echo "❌ S3 bucket resource not found"
    exit 1
fi

if grep -q "aws_s3_bucket_public_access_block" "$PROJECT_DIR/terraform/main.tf"; then
    echo "✅ Public access block resource found"
else
    echo "❌ Public access block resource not found"
    exit 1
fi

# Check for all required public access block settings
if grep -q "block_public_acls.*=.*true" "$PROJECT_DIR/terraform/main.tf" && \
   grep -q "ignore_public_acls.*=.*true" "$PROJECT_DIR/terraform/main.tf" && \
   grep -q "block_public_policy.*=.*true" "$PROJECT_DIR/terraform/main.tf" && \
   grep -q "restrict_public_buckets.*=.*true" "$PROJECT_DIR/terraform/main.tf"; then
    echo "✅ All public access block settings configured"
else
    echo "❌ Missing public access block settings"
    exit 1
fi

# Test 3: CloudFormation YAML syntax validation
echo ""
echo "📄 Test 3: CloudFormation YAML Validation"
echo "-----------------------------------------"

# Basic YAML structure check
if grep -q "AWSTemplateFormatVersion:" "$PROJECT_DIR/cloudformation/s3-security.yaml"; then
    echo "✅ CloudFormation template format version found"
else
    echo "❌ CloudFormation template format version missing"
    exit 1
fi

if grep -q "PublicAccessBlockConfiguration:" "$PROJECT_DIR/cloudformation/s3-security.yaml"; then
    echo "✅ Public Access Block Configuration found"
else
    echo "❌ Public Access Block Configuration missing"
    exit 1
fi

# Check for all required public access block settings in CloudFormation
if grep -A 10 "PublicAccessBlockConfiguration:" "$PROJECT_DIR/cloudformation/s3-security.yaml" | grep -q "BlockPublicAcls: true" && \
   grep -A 10 "PublicAccessBlockConfiguration:" "$PROJECT_DIR/cloudformation/s3-security.yaml" | grep -q "IgnorePublicAcls: true" && \
   grep -A 10 "PublicAccessBlockConfiguration:" "$PROJECT_DIR/cloudformation/s3-security.yaml" | grep -q "BlockPublicPolicy: true" && \
   grep -A 10 "PublicAccessBlockConfiguration:" "$PROJECT_DIR/cloudformation/s3-security.yaml" | grep -q "RestrictPublicBuckets: true"; then
    echo "✅ All CloudFormation public access block settings configured"
else
    echo "❌ Missing CloudFormation public access block settings"
    exit 1
fi

# Test 4: Security configuration validation
echo ""
echo "🔒 Test 4: Security Configuration Validation"
echo "--------------------------------------------"

# Check for deny statements in bucket policy
if grep -A 20 "DenyPublicWrite" "$PROJECT_DIR/terraform/main.tf" | grep -q "s3:PutObject\|s3:DeleteObject"; then
    echo "✅ Terraform bucket policy contains deny statements for public write"
else
    echo "❌ Terraform bucket policy missing deny statements for public write"
    exit 1
fi

if grep -A 20 "DenyPublicWrite" "$PROJECT_DIR/cloudformation/s3-security.yaml" | grep -q "s3:PutObject\|s3:DeleteObject"; then
    echo "✅ CloudFormation bucket policy contains deny statements for public write"
else
    echo "❌ CloudFormation bucket policy missing deny statements for public write"
    exit 1
fi

# Test 5: Documentation validation
echo ""
echo "📖 Test 5: Documentation Validation"
echo "-----------------------------------"

if grep -q "S3.3" "$PROJECT_DIR/docs/AWS_S3_SECURITY.md"; then
    echo "✅ Documentation references AWS Security Hub control S3.3"
else
    echo "❌ Documentation missing S3.3 reference"
    exit 1
fi

if grep -q "BlockPublicAcls" "$PROJECT_DIR/docs/AWS_S3_SECURITY.md"; then
    echo "✅ Documentation includes public access block details"
else
    echo "❌ Documentation missing public access block details"
    exit 1
fi

echo ""
echo "✅ All validation tests passed!"
echo "🛡️  Infrastructure as Code templates are properly configured for S3 security"