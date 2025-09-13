#!/bin/bash

# Terraform Deployment Script for Secure S3 Buckets
# This script deploys the secure S3 bucket configuration

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
TERRAFORM_DIR="$PROJECT_DIR/terraform"

echo "Deploying secure S3 bucket infrastructure..."
echo "============================================="

# Function to check if Terraform is available
check_terraform() {
    if ! command -v terraform &> /dev/null; then
        echo "❌ Terraform not found. Please install Terraform to deploy the infrastructure."
        exit 1
    fi
    echo "✅ Terraform found: $(terraform version -json | jq -r '.terraform_version')"
}

# Function to initialize Terraform
terraform_init() {
    echo ""
    echo "🔧 Initializing Terraform..."
    cd "$TERRAFORM_DIR"
    terraform init
}

# Function to plan Terraform deployment
terraform_plan() {
    echo ""
    echo "📋 Planning Terraform deployment..."
    cd "$TERRAFORM_DIR"
    terraform plan -out=tfplan
}

# Function to apply Terraform deployment
terraform_apply() {
    echo ""
    echo "🚀 Applying Terraform deployment..."
    cd "$TERRAFORM_DIR"
    terraform apply tfplan
}

# Function to show outputs
show_outputs() {
    echo ""
    echo "📊 Deployment outputs:"
    cd "$TERRAFORM_DIR"
    terraform output
}

# Function to validate deployment
validate_deployment() {
    echo ""
    echo "🔍 Validating deployment..."
    cd "$TERRAFORM_DIR"
    BUCKET_NAME=$(terraform output -raw bucket_name)
    
    if [[ -n "$BUCKET_NAME" ]]; then
        echo "✅ Bucket created: $BUCKET_NAME"
        echo "🔒 Running security validation..."
        "$SCRIPT_DIR/validate-s3-security.sh" "$BUCKET_NAME"
    else
        echo "❌ Failed to get bucket name from Terraform outputs"
        exit 1
    fi
}

# Main deployment function
main() {
    echo "Secure S3 Bucket Deployment"
    echo "==========================="
    echo "Project: DropBox Application"
    echo "Date: $(date)"
    echo ""
    
    check_terraform
    terraform_init
    terraform_plan
    
    echo ""
    read -p "Do you want to apply this Terraform plan? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        terraform_apply
        show_outputs
        validate_deployment
        
        echo ""
        echo "✅ Deployment completed successfully!"
        echo "🔐 Your S3 bucket is now secured with block public access settings."
        echo ""
        echo "Next steps:"
        echo "1. Update your application configuration with the bucket name"
        echo "2. Configure AWS credentials for your application"
        echo "3. Set storage.type=s3 in application.properties to use S3 storage"
    else
        echo "Deployment cancelled."
        exit 0
    fi
}

# Cleanup function
cleanup() {
    if [[ -f "$TERRAFORM_DIR/tfplan" ]]; then
        rm "$TERRAFORM_DIR/tfplan"
    fi
}

# Set trap for cleanup
trap cleanup EXIT

main "$@"