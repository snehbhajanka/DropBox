#!/bin/bash

# Quick S3 Security Deployment Script
# This script provides guided deployment of S3 security fixes

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_header() {
    echo -e "${BLUE}=========================================="
    echo -e "S3 Security Deployment Script"
    echo -e "Addresses Security Issue S3.3"
    echo -e "==========================================${NC}"
    echo
}

print_status() {
    local status=$1
    local message=$2
    case $status in
        "SUCCESS")
            echo -e "${GREEN}✓ $message${NC}"
            ;;
        "ERROR")
            echo -e "${RED}✗ $message${NC}"
            ;;
        "WARNING")
            echo -e "${YELLOW}⚠ $message${NC}"
            ;;
        "INFO")
            echo -e "${NC}ℹ $message${NC}"
            ;;
    esac
}

check_prerequisites() {
    print_status "INFO" "Checking prerequisites..."
    
    # Check AWS CLI
    if ! command -v aws &> /dev/null; then
        print_status "ERROR" "AWS CLI is not installed"
        echo "Please install AWS CLI: https://aws.amazon.com/cli/"
        exit 1
    fi
    
    # Check AWS credentials
    if ! aws sts get-caller-identity >/dev/null 2>&1; then
        print_status "ERROR" "AWS credentials not configured"
        echo "Please configure AWS credentials: aws configure"
        exit 1
    fi
    
    print_status "SUCCESS" "Prerequisites check passed"
    echo
}

deploy_terraform() {
    print_status "INFO" "Starting Terraform deployment..."
    
    if ! command -v terraform &> /dev/null; then
        print_status "ERROR" "Terraform is not installed"
        echo "Please install Terraform: https://developer.hashicorp.com/terraform/downloads"
        return 1
    fi
    
    cd infrastructure/terraform
    
    # Check if terraform.tfvars exists
    if [ ! -f "terraform.tfvars" ]; then
        print_status "WARNING" "terraform.tfvars not found, copying example file"
        cp terraform.tfvars.example terraform.tfvars
        print_status "INFO" "Please edit terraform.tfvars with your bucket names"
        echo "Required variables:"
        echo "  - bucket_name: Must be globally unique"
        echo "  - aws_region: Target AWS region"
        echo "  - environment: Environment name (dev/staging/prod)"
        echo
        read -p "Press Enter after editing terraform.tfvars..."
    fi
    
    # Initialize Terraform
    print_status "INFO" "Initializing Terraform..."
    if terraform init; then
        print_status "SUCCESS" "Terraform initialized"
    else
        print_status "ERROR" "Terraform initialization failed"
        return 1
    fi
    
    # Plan
    print_status "INFO" "Creating Terraform plan..."
    if terraform plan -out=tfplan; then
        print_status "SUCCESS" "Terraform plan created"
    else
        print_status "ERROR" "Terraform plan failed"
        return 1
    fi
    
    # Apply
    echo
    print_status "WARNING" "About to apply Terraform configuration"
    read -p "Do you want to proceed? (y/N): " confirm
    if [[ $confirm =~ ^[Yy]$ ]]; then
        if terraform apply tfplan; then
            print_status "SUCCESS" "Terraform applied successfully"
            rm -f tfplan
        else
            print_status "ERROR" "Terraform apply failed"
            return 1
        fi
    else
        print_status "INFO" "Deployment cancelled"
        rm -f tfplan
        return 1
    fi
    
    cd - >/dev/null
}

deploy_cloudformation() {
    print_status "INFO" "Starting CloudFormation deployment..."
    
    cd infrastructure/cloudformation
    
    # Validate template
    print_status "INFO" "Validating CloudFormation template..."
    if aws cloudformation validate-template --template-body file://s3-security.yml >/dev/null; then
        print_status "SUCCESS" "CloudFormation template is valid"
    else
        print_status "ERROR" "CloudFormation template validation failed"
        return 1
    fi
    
    # Check parameters file
    if [ ! -f "parameters.json" ]; then
        print_status "ERROR" "parameters.json not found"
        return 1
    fi
    
    # Get stack name
    read -p "Enter CloudFormation stack name [dropbox-s3-security]: " stack_name
    stack_name=${stack_name:-dropbox-s3-security}
    
    # Deploy stack
    print_status "INFO" "Deploying CloudFormation stack: $stack_name"
    if aws cloudformation create-stack \
        --stack-name "$stack_name" \
        --template-body file://s3-security.yml \
        --parameters file://parameters.json; then
        print_status "SUCCESS" "CloudFormation stack deployment initiated"
        
        # Wait for completion
        print_status "INFO" "Waiting for stack creation to complete..."
        if aws cloudformation wait stack-create-complete --stack-name "$stack_name"; then
            print_status "SUCCESS" "CloudFormation stack created successfully"
        else
            print_status "ERROR" "CloudFormation stack creation failed"
            return 1
        fi
    else
        print_status "ERROR" "Failed to create CloudFormation stack"
        return 1
    fi
    
    cd - >/dev/null
}

apply_to_existing_buckets() {
    print_status "INFO" "Applying security settings to existing buckets..."
    
    echo "Enter the names of your 13 affected buckets (one per line, empty line to finish):"
    buckets=()
    while IFS= read -r bucket; do
        if [ -z "$bucket" ]; then
            break
        fi
        buckets+=("$bucket")
    done
    
    if [ ${#buckets[@]} -eq 0 ]; then
        print_status "WARNING" "No bucket names provided, skipping this step"
        return 0
    fi
    
    for bucket in "${buckets[@]}"; do
        print_status "INFO" "Securing bucket: $bucket"
        
        # Apply public access block
        if aws s3api put-public-access-block \
            --bucket "$bucket" \
            --public-access-block-configuration \
            BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true; then
            print_status "SUCCESS" "Public access blocked for $bucket"
        else
            print_status "ERROR" "Failed to block public access for $bucket"
        fi
        
        # Enable versioning
        if aws s3api put-bucket-versioning \
            --bucket "$bucket" \
            --versioning-configuration Status=Enabled; then
            print_status "SUCCESS" "Versioning enabled for $bucket"
        else
            print_status "WARNING" "Failed to enable versioning for $bucket"
        fi
    done
}

run_validation() {
    print_status "INFO" "Running security validation..."
    
    # Validate infrastructure
    if [ -f "scripts/validate-infrastructure.sh" ]; then
        if ./scripts/validate-infrastructure.sh; then
            print_status "SUCCESS" "Infrastructure validation passed"
        else
            print_status "WARNING" "Infrastructure validation had issues"
        fi
    fi
    
    # Validate bucket security
    if [ -f "scripts/validate-s3-security.sh" ]; then
        echo "Enter bucket names to validate (space-separated):"
        read -r bucket_names
        if [ -n "$bucket_names" ]; then
            if ./scripts/validate-s3-security.sh $bucket_names; then
                print_status "SUCCESS" "Bucket security validation passed"
            else
                print_status "ERROR" "Bucket security validation failed"
            fi
        fi
    fi
}

main() {
    print_header
    
    check_prerequisites
    
    echo "Choose deployment method:"
    echo "1) Terraform (recommended)"
    echo "2) CloudFormation"
    echo "3) Apply to existing buckets only"
    echo "4) Run validation only"
    read -p "Enter choice (1-4): " choice
    
    case $choice in
        1)
            deploy_terraform
            ;;
        2)
            deploy_cloudformation
            ;;
        3)
            apply_to_existing_buckets
            ;;
        4)
            run_validation
            ;;
        *)
            print_status "ERROR" "Invalid choice"
            exit 1
            ;;
    esac
    
    echo
    print_status "INFO" "Would you like to run validation tests?"
    read -p "Run validation? (y/N): " run_tests
    if [[ $run_tests =~ ^[Yy]$ ]]; then
        run_validation
    fi
    
    echo
    print_status "SUCCESS" "Deployment process complete!"
    echo "Next steps:"
    echo "1. Review the security validation results"
    echo "2. Monitor AWS Config for compliance status"
    echo "3. Update your application configurations if needed"
    echo "4. Schedule regular security validations"
}

main "$@"