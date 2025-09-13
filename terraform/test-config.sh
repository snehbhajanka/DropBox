#!/bin/bash

# Test script for Terraform S3 configuration
# This script validates the Terraform configuration without deploying resources

set -e

echo "=== Testing Terraform S3 Security Configuration ==="

# Change to terraform directory
cd "$(dirname "$0")"

# Check if terraform is installed
if ! command -v terraform &> /dev/null; then
    echo "ERROR: Terraform is not installed"
    exit 1
fi

echo "✓ Terraform is installed"

# Initialize terraform
echo "Initializing Terraform..."
terraform init > /dev/null 2>&1
echo "✓ Terraform initialized successfully"

# Validate configuration
echo "Validating Terraform configuration..."
terraform validate
echo "✓ Terraform configuration is valid"

# Check formatting
echo "Checking Terraform formatting..."
if terraform fmt -check; then
    echo "✓ Terraform files are properly formatted"
else
    echo "⚠ Terraform files need formatting (run 'terraform fmt')"
fi

# Validate configuration syntax and structure (without AWS credentials)
echo "Validating Terraform structure and syntax..."

# Check that all required resources are defined
REQUIRED_RESOURCES=(
    "aws_s3_bucket"
    "aws_s3_bucket_public_access_block"
    "aws_s3_bucket_server_side_encryption_configuration"
    "aws_s3_bucket_versioning"
    "aws_s3_bucket_lifecycle_configuration"
)

echo "Checking for required security resources..."
for resource in "${REQUIRED_RESOURCES[@]}"; do
    if grep -q "resource \"$resource\"" main.tf; then
        echo "✓ $resource is defined"
    else
        echo "✗ $resource is missing"
        exit 1
    fi
done

# Check public access block configuration
echo "Validating public access block configuration..."
SECURITY_SETTINGS=(
    "block_public_acls = true"
    "ignore_public_acls = true"
    "block_public_policy = true"
    "restrict_public_buckets = true"
)

for setting in "${SECURITY_SETTINGS[@]}"; do
    if grep -q "$setting" main.tf; then
        echo "✓ $setting is configured"
    else
        echo "✗ $setting is missing"
        exit 1
    fi
done

# Check that variables are properly defined
echo "Validating variable definitions..."
REQUIRED_VARIABLES=(
    "aws_region"
    "bucket_count"
    "bucket_prefix"
    "environment"
    "enable_versioning"
)

for var in "${REQUIRED_VARIABLES[@]}"; do
    if grep -q "variable \"$var\"" variables.tf; then
        echo "✓ Variable $var is defined"
    else
        echo "✗ Variable $var is missing"
        exit 1
    fi
done

# Check that outputs are defined
echo "Validating output definitions..."
REQUIRED_OUTPUTS=(
    "bucket_names"
    "bucket_arns"
    "public_access_block_status"
)

for output in "${REQUIRED_OUTPUTS[@]}"; do
    if grep -q "output \"$output\"" outputs.tf; then
        echo "✓ Output $output is defined"
    else
        echo "✗ Output $output is missing"
        exit 1
    fi
done

echo "✓ All tests passed! The Terraform configuration is properly structured and secure."
echo
echo "NOTE: This validation only checks configuration structure and syntax."
echo "AWS credentials are required for full deployment testing."
echo
echo "To deploy the infrastructure:"
echo "  1. Configure AWS credentials (aws configure)"
echo "  2. Copy terraform.tfvars.example to terraform.tfvars"
echo "  3. Customize the values in terraform.tfvars"
echo "  4. Run 'terraform plan' to review changes"
echo "  5. Run 'terraform apply' to deploy"