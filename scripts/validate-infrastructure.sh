#!/bin/bash

# Terraform Validation Script for S3 Security
# This script validates Terraform configurations for S3 security compliance

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
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

# Function to validate Terraform configuration
validate_terraform() {
    local terraform_dir=${1:-"infrastructure/terraform"}
    
    print_status "INFO" "Validating Terraform configuration in $terraform_dir"
    
    if [ ! -d "$terraform_dir" ]; then
        print_status "ERROR" "Terraform directory $terraform_dir does not exist"
        return 1
    fi
    
    cd "$terraform_dir"
    
    # Check if terraform is installed
    if ! command -v terraform &> /dev/null; then
        print_status "ERROR" "Terraform is not installed or not in PATH"
        return 1
    fi
    
    # Initialize Terraform
    print_status "INFO" "Initializing Terraform..."
    if terraform init -backend=false >/dev/null 2>&1; then
        print_status "SUCCESS" "Terraform initialization successful"
    else
        print_status "ERROR" "Terraform initialization failed"
        return 1
    fi
    
    # Validate Terraform configuration
    print_status "INFO" "Validating Terraform configuration..."
    if terraform validate >/dev/null 2>&1; then
        print_status "SUCCESS" "Terraform configuration is valid"
    else
        print_status "ERROR" "Terraform configuration validation failed"
        terraform validate
        return 1
    fi
    
    # Format check
    print_status "INFO" "Checking Terraform formatting..."
    if terraform fmt -check >/dev/null 2>&1; then
        print_status "SUCCESS" "Terraform files are properly formatted"
    else
        print_status "WARNING" "Terraform files need formatting (run 'terraform fmt')"
    fi
    
    # Security validation - check for required security configurations
    print_status "INFO" "Checking S3 security configurations..."
    
    local security_issues=0
    
    # Check for aws_s3_bucket_public_access_block resources
    if grep -q "aws_s3_bucket_public_access_block" *.tf; then
        print_status "SUCCESS" "Public access block resources found"
        
        # Check specific security settings
        if grep -q "block_public_acls.*=.*true" *.tf; then
            print_status "SUCCESS" "block_public_acls set to true"
        else
            print_status "ERROR" "block_public_acls not set to true"
            ((security_issues++))
        fi
        
        if grep -q "ignore_public_acls.*=.*true" *.tf; then
            print_status "SUCCESS" "ignore_public_acls set to true"
        else
            print_status "ERROR" "ignore_public_acls not set to true"
            ((security_issues++))
        fi
        
        if grep -q "block_public_policy.*=.*true" *.tf; then
            print_status "SUCCESS" "block_public_policy set to true"
        else
            print_status "ERROR" "block_public_policy not set to true"
            ((security_issues++))
        fi
        
        if grep -q "restrict_public_buckets.*=.*true" *.tf; then
            print_status "SUCCESS" "restrict_public_buckets set to true"
        else
            print_status "ERROR" "restrict_public_buckets not set to true"
            ((security_issues++))
        fi
    else
        print_status "ERROR" "No public access block resources found"
        ((security_issues++))
    fi
    
    # Check for proper ACL settings
    if grep -q "acl.*=.*\"private\"" *.tf; then
        print_status "SUCCESS" "Private ACL configuration found"
    else
        print_status "WARNING" "No explicit private ACL found (may be acceptable if using public access blocks)"
    fi
    
    # Plan the configuration (dry run)
    print_status "INFO" "Creating Terraform plan..."
    if terraform plan -out=tfplan >/dev/null 2>&1; then
        print_status "SUCCESS" "Terraform plan created successfully"
        rm -f tfplan
    else
        print_status "WARNING" "Terraform plan failed (may be due to missing variables or AWS credentials)"
    fi
    
    cd - >/dev/null
    
    if [ $security_issues -eq 0 ]; then
        print_status "SUCCESS" "All S3 security configurations are present"
        return 0
    else
        print_status "ERROR" "$security_issues security configuration(s) missing"
        return 1
    fi
}

# Function to validate CloudFormation template
validate_cloudformation() {
    local cf_dir=${1:-"infrastructure/cloudformation"}
    
    print_status "INFO" "Validating CloudFormation template in $cf_dir"
    
    if [ ! -d "$cf_dir" ]; then
        print_status "ERROR" "CloudFormation directory $cf_dir does not exist"
        return 1
    fi
    
    # Check if AWS CLI is available
    if ! command -v aws &> /dev/null; then
        print_status "ERROR" "AWS CLI is not installed or not in PATH"
        return 1
    fi
    
    local template_file="$cf_dir/s3-security.yml"
    
    if [ ! -f "$template_file" ]; then
        print_status "ERROR" "CloudFormation template $template_file not found"
        return 1
    fi
    
    # Validate CloudFormation template
    print_status "INFO" "Validating CloudFormation template..."
    if aws cloudformation validate-template --template-body "file://$template_file" >/dev/null 2>&1; then
        print_status "SUCCESS" "CloudFormation template is valid"
    else
        print_status "ERROR" "CloudFormation template validation failed"
        aws cloudformation validate-template --template-body "file://$template_file"
        return 1
    fi
    
    # Check for security configurations in the template
    print_status "INFO" "Checking CloudFormation S3 security configurations..."
    
    local security_issues=0
    
    # Check for PublicAccessBlockConfiguration
    if grep -q "PublicAccessBlockConfiguration:" "$template_file"; then
        print_status "SUCCESS" "PublicAccessBlockConfiguration found"
        
        # Check specific settings
        if grep -A10 "PublicAccessBlockConfiguration:" "$template_file" | grep -q "BlockPublicAcls:.*true"; then
            print_status "SUCCESS" "BlockPublicAcls set to true"
        else
            print_status "ERROR" "BlockPublicAcls not set to true"
            ((security_issues++))
        fi
        
        if grep -A10 "PublicAccessBlockConfiguration:" "$template_file" | grep -q "IgnorePublicAcls:.*true"; then
            print_status "SUCCESS" "IgnorePublicAcls set to true"
        else
            print_status "ERROR" "IgnorePublicAcls not set to true"
            ((security_issues++))
        fi
        
        if grep -A10 "PublicAccessBlockConfiguration:" "$template_file" | grep -q "BlockPublicPolicy:.*true"; then
            print_status "SUCCESS" "BlockPublicPolicy set to true"
        else
            print_status "ERROR" "BlockPublicPolicy not set to true"
            ((security_issues++))
        fi
        
        if grep -A10 "PublicAccessBlockConfiguration:" "$template_file" | grep -q "RestrictPublicBuckets:.*true"; then
            print_status "SUCCESS" "RestrictPublicBuckets set to true"
        else
            print_status "ERROR" "RestrictPublicBuckets not set to true"
            ((security_issues++))
        fi
    else
        print_status "ERROR" "No PublicAccessBlockConfiguration found"
        ((security_issues++))
    fi
    
    if [ $security_issues -eq 0 ]; then
        print_status "SUCCESS" "All S3 security configurations are present in CloudFormation template"
        return 0
    else
        print_status "ERROR" "$security_issues security configuration(s) missing in CloudFormation template"
        return 1
    fi
}

# Main function
main() {
    echo "=========================================="
    echo "Infrastructure Security Validation"
    echo "S3.3 - Block Public Write Access"
    echo "=========================================="
    echo
    
    local validation_errors=0
    
    # Validate Terraform
    if ! validate_terraform; then
        ((validation_errors++))
    fi
    
    echo
    
    # Validate CloudFormation
    if ! validate_cloudformation; then
        ((validation_errors++))
    fi
    
    echo
    echo "=========================================="
    echo "Validation Summary"
    echo "=========================================="
    
    if [ $validation_errors -eq 0 ]; then
        print_status "SUCCESS" "All infrastructure configurations passed validation"
        print_status "SUCCESS" "S3.3 security requirements are properly configured"
        exit 0
    else
        print_status "ERROR" "$validation_errors validation(s) failed"
        print_status "ERROR" "Infrastructure configurations need to be fixed"
        exit 1
    fi
}

# Run main function
main "$@"