#!/bin/bash

# Terraform deployment script for DropBox S3 security configuration
# This script deploys secure S3 buckets with blocked public write access

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TERRAFORM_DIR="$SCRIPT_DIR/../terraform"

echo "🔒 DropBox S3 Security Deployment Script"
echo "========================================"

# Check prerequisites
echo "📋 Checking prerequisites..."

# Check if Terraform is installed
if ! command -v terraform &> /dev/null; then
    echo "❌ Error: Terraform is not installed. Please install Terraform >= 1.0"
    exit 1
fi

# Check if AWS CLI is installed and configured
if ! command -v aws &> /dev/null; then
    echo "❌ Error: AWS CLI is not installed. Please install AWS CLI"
    exit 1
fi

# Check AWS credentials
if ! aws sts get-caller-identity &> /dev/null; then
    echo "❌ Error: AWS credentials not configured. Please run 'aws configure'"
    exit 1
fi

echo "✅ Prerequisites check passed"

# Navigate to Terraform directory
cd "$TERRAFORM_DIR"

# Check if terraform.tfvars exists
if [ ! -f "terraform.tfvars" ]; then
    echo "⚠️  Warning: terraform.tfvars not found"
    echo "📋 Please copy terraform.tfvars.example to terraform.tfvars and customize the values"
    echo ""
    echo "cp terraform.tfvars.example terraform.tfvars"
    echo ""
    read -p "Press Enter to continue after creating terraform.tfvars, or Ctrl+C to exit..."
fi

# Validate terraform.tfvars
if [ ! -f "terraform.tfvars" ]; then
    echo "❌ Error: terraform.tfvars still not found. Exiting."
    exit 1
fi

echo "🔧 Initializing Terraform..."
terraform init

echo "📝 Planning deployment..."
terraform plan -out=tfplan

echo ""
echo "🚀 Ready to deploy secure S3 configuration"
echo "This will create:"
echo "  • S3 bucket with blocked public write access"
echo "  • Public access block configuration (all settings enabled)"
echo "  • Bucket encryption (AES256)"
echo "  • Versioning enabled"
echo "  • Lifecycle policies for cost optimization"
echo "  • Secure bucket policy"
echo ""

read -p "Do you want to proceed with the deployment? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "🔨 Applying Terraform configuration..."
    terraform apply tfplan
    
    echo ""
    echo "✅ Deployment completed successfully!"
    echo "📊 Terraform outputs:"
    terraform output
    
    echo ""
    echo "🔍 Security Verification:"
    echo "Run the following command to verify public access is blocked:"
    bucket_name=$(terraform output -raw bucket_name)
    echo "aws s3api get-public-access-block --bucket $bucket_name"
    
else
    echo "❌ Deployment cancelled"
    rm -f tfplan
    exit 1
fi

# Clean up plan file
rm -f tfplan

echo ""
echo "🎉 S3 security configuration deployed successfully!"
echo "📖 See docs/AWS_S3_SECURITY.md for validation steps and troubleshooting"