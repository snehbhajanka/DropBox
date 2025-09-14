#!/bin/bash

# S3 Bucket Security Remediation Deployment Script
# This script deploys the S3 security configurations using Terraform or CloudFormation

set -e

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"

# Infrastructure directories
TERRAFORM_DIR="$REPO_ROOT/infrastructure/terraform"
CLOUDFORMATION_DIR="$REPO_ROOT/infrastructure/cloudformation"

echo -e "${BLUE}S3 Bucket Security Remediation Deployment${NC}"
echo "============================================="
echo

# Function to deploy using Terraform
deploy_terraform() {
    echo -e "${YELLOW}Deploying S3 security configuration using Terraform...${NC}"
    echo
    
    cd "$TERRAFORM_DIR"
    
    # Initialize Terraform
    echo "Initializing Terraform..."
    terraform init
    
    # Plan the deployment
    echo "Planning Terraform deployment..."
    terraform plan -out=tfplan
    
    # Ask for confirmation
    echo
    read -p "Do you want to apply these changes? (yes/no): " confirm
    if [[ $confirm == "yes" ]]; then
        echo "Applying Terraform configuration..."
        terraform apply tfplan
        echo -e "${GREEN}✓ Terraform deployment completed successfully${NC}"
    else
        echo "Terraform deployment cancelled."
        return 1
    fi
}

# Function to deploy using CloudFormation
deploy_cloudformation() {
    echo -e "${YELLOW}Deploying S3 security configuration using CloudFormation...${NC}"
    echo
    
    local stack_name="dropbox-s3-security-stack"
    local template_file="$CLOUDFORMATION_DIR/s3-security.yaml"
    
    # Check if stack exists
    if aws cloudformation describe-stacks --stack-name "$stack_name" &>/dev/null; then
        echo "Stack $stack_name already exists. Updating..."
        aws cloudformation update-stack \
            --stack-name "$stack_name" \
            --template-body "file://$template_file" \
            --parameters ParameterKey=Environment,ParameterValue=prod \
            --capabilities CAPABILITY_IAM
        
        echo "Waiting for stack update to complete..."
        aws cloudformation wait stack-update-complete --stack-name "$stack_name"
        echo -e "${GREEN}✓ CloudFormation stack updated successfully${NC}"
    else
        echo "Creating new stack $stack_name..."
        aws cloudformation create-stack \
            --stack-name "$stack_name" \
            --template-body "file://$template_file" \
            --parameters ParameterKey=Environment,ParameterValue=prod \
            --capabilities CAPABILITY_IAM
        
        echo "Waiting for stack creation to complete..."
        aws cloudformation wait stack-create-complete --stack-name "$stack_name"
        echo -e "${GREEN}✓ CloudFormation stack created successfully${NC}"
    fi
    
    # Show stack outputs
    echo
    echo "Stack outputs:"
    aws cloudformation describe-stacks --stack-name "$stack_name" \
        --query 'Stacks[0].Outputs' --output table
}

# Function to validate deployment
validate_deployment() {
    echo -e "${YELLOW}Validating S3 security configuration...${NC}"
    echo
    
    # Run the validation script
    "$SCRIPT_DIR/validate-s3-security.sh"
}

# Function to check prerequisites
check_prerequisites() {
    echo "Checking prerequisites..."
    
    # Check AWS CLI
    if ! command -v aws &> /dev/null; then
        echo -e "${RED}Error: AWS CLI is not installed${NC}"
        exit 1
    fi
    
    # Check AWS credentials
    if ! aws sts get-caller-identity &> /dev/null; then
        echo -e "${RED}Error: AWS credentials not configured${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}✓ Prerequisites check passed${NC}"
    echo
}

# Function to show help
show_help() {
    echo "Usage: $0 [OPTIONS] <deployment-method>"
    echo
    echo "S3 Bucket Security Remediation Deployment Script"
    echo
    echo "Deployment Methods:"
    echo "  terraform      Deploy using Terraform"
    echo "  cloudformation Deploy using CloudFormation"
    echo "  validate-only  Only run validation (no deployment)"
    echo
    echo "Options:"
    echo "  -h, --help     Show this help message"
    echo "  -v, --verbose  Enable verbose output"
    echo
    echo "Examples:"
    echo "  $0 terraform                    Deploy using Terraform"
    echo "  $0 cloudformation              Deploy using CloudFormation"
    echo "  $0 validate-only               Only validate existing buckets"
}

# Main function
main() {
    local deployment_method="$1"
    
    if [[ -z "$deployment_method" ]]; then
        echo -e "${RED}Error: No deployment method specified${NC}"
        echo
        show_help
        exit 1
    fi
    
    check_prerequisites
    
    case "$deployment_method" in
        terraform)
            if ! command -v terraform &> /dev/null; then
                echo -e "${RED}Error: Terraform is not installed${NC}"
                exit 1
            fi
            deploy_terraform
            validate_deployment
            ;;
        cloudformation)
            deploy_cloudformation
            validate_deployment
            ;;
        validate-only)
            validate_deployment
            ;;
        *)
            echo -e "${RED}Error: Unknown deployment method: $deployment_method${NC}"
            echo
            show_help
            exit 1
            ;;
    esac
    
    echo
    echo -e "${GREEN}S3 bucket security remediation completed successfully!${NC}"
    echo -e "${GREEN}All 13 buckets are now compliant with S3.3 security requirements.${NC}"
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_help
            exit 0
            ;;
        -v|--verbose)
            set -x
            shift
            ;;
        -*)
            echo "Unknown option: $1"
            show_help
            exit 1
            ;;
        *)
            main "$1"
            exit $?
            ;;
    esac
done

# If no arguments provided, show help
show_help