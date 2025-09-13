#!/bin/bash
# Validation script for DropBox S3 security configuration
# This script validates the Terraform configuration addresses security issue S3.3

set -e

echo "🔍 Validating DropBox S3 Security Configuration..."
echo "================================================="

# Change to terraform directory
cd "$(dirname "$0")"

# Check if Terraform is installed
if ! command -v terraform &> /dev/null; then
    echo "❌ Terraform is not installed. Please install Terraform first."
    exit 1
fi

echo "✅ Terraform found: $(terraform version --json | jq -r '.terraform_version')"

# Initialize Terraform
echo "🔧 Initializing Terraform..."
terraform init -backend=false

# Validate Terraform configuration
echo "🔍 Validating Terraform syntax..."
terraform validate

# Format check
echo "🎨 Checking Terraform formatting..."
if ! terraform fmt -check; then
    echo "⚠️  Terraform files are not properly formatted. Running terraform fmt..."
    terraform fmt
fi

# Generate plan to check configuration
echo "📋 Generating Terraform plan..."
terraform plan -out=security-validation.plan

# Extract and validate security settings from plan
echo "🔒 Validating security configuration..."

# Check if plan contains the required security resources
if terraform show -json security-validation.plan | jq -e '.planned_values.root_module.resources[] | select(.type == "aws_s3_bucket_public_access_block")' > /dev/null; then
    echo "✅ S3 bucket public access block resource found"
else
    echo "❌ S3 bucket public access block resource not found"
    exit 1
fi

# Validate that all security flags are set to true
PLAN_JSON=$(terraform show -json security-validation.plan)

# Extract public access block values
BLOCK_PUBLIC_ACLS=$(echo "$PLAN_JSON" | jq -r '.planned_values.root_module.resources[] | select(.type == "aws_s3_bucket_public_access_block") | .values.block_public_acls')
IGNORE_PUBLIC_ACLS=$(echo "$PLAN_JSON" | jq -r '.planned_values.root_module.resources[] | select(.type == "aws_s3_bucket_public_access_block") | .values.ignore_public_acls')
BLOCK_PUBLIC_POLICY=$(echo "$PLAN_JSON" | jq -r '.planned_values.root_module.resources[] | select(.type == "aws_s3_bucket_public_access_block") | .values.block_public_policy')
RESTRICT_PUBLIC_BUCKETS=$(echo "$PLAN_JSON" | jq -r '.planned_values.root_module.resources[] | select(.type == "aws_s3_bucket_public_access_block") | .values.restrict_public_buckets')

echo "🔒 Security Configuration Validation:"
echo "  - block_public_acls: $BLOCK_PUBLIC_ACLS"
echo "  - ignore_public_acls: $IGNORE_PUBLIC_ACLS" 
echo "  - block_public_policy: $BLOCK_PUBLIC_POLICY"
echo "  - restrict_public_buckets: $RESTRICT_PUBLIC_BUCKETS"

# Validate all settings are true
if [[ "$BLOCK_PUBLIC_ACLS" == "true" && "$IGNORE_PUBLIC_ACLS" == "true" && "$BLOCK_PUBLIC_POLICY" == "true" && "$RESTRICT_PUBLIC_BUCKETS" == "true" ]]; then
    echo "✅ All S3 security requirements are properly configured!"
    echo "✅ Security misconfiguration S3.3 is RESOLVED"
else
    echo "❌ S3 security configuration is incomplete!"
    echo "❌ Security misconfiguration S3.3 is NOT resolved"
    exit 1
fi

# Clean up plan file
rm -f security-validation.plan

echo ""
echo "🎉 Validation complete! The Terraform configuration properly addresses:"
echo "   - CRITICAL security issue S3.3: Block Public Write Access"
echo "   - All four public access block settings are enabled"
echo "   - S3 buckets will be secure from public write access"
echo ""
echo "Next steps:"
echo "1. Copy terraform.tfvars.example to terraform.tfvars"
echo "2. Customize the bucket name and other variables"
echo "3. Run 'terraform apply' to create the secure S3 infrastructure"