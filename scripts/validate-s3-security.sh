#!/bin/bash

# S3 Security Validation Script
# This script validates that S3 buckets have proper security configurations
# to address the S3.3 security issue - Block Public Write Access

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

# Function to validate a single bucket's public access block settings
validate_bucket_security() {
    local bucket_name=$1
    
    print_status "INFO" "Validating bucket: $bucket_name"
    
    # Check if bucket exists
    if ! aws s3api head-bucket --bucket "$bucket_name" >/dev/null 2>&1; then
        print_status "ERROR" "Bucket $bucket_name does not exist or is not accessible"
        return 1
    fi
    
    # Get public access block configuration
    local pab_config
    pab_config=$(aws s3api get-public-access-block --bucket "$bucket_name" --output json 2>/dev/null)
    
    if [ $? -ne 0 ]; then
        print_status "ERROR" "Failed to get public access block configuration for $bucket_name"
        return 1
    fi
    
    # Parse the configuration
    local block_public_acls
    local ignore_public_acls
    local block_public_policy
    local restrict_public_buckets
    
    block_public_acls=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.BlockPublicAcls')
    ignore_public_acls=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.IgnorePublicAcls')
    block_public_policy=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.BlockPublicPolicy')
    restrict_public_buckets=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.RestrictPublicBuckets')
    
    # Validate each setting
    local all_secure=true
    
    if [ "$block_public_acls" = "true" ]; then
        print_status "SUCCESS" "  BlockPublicAcls: $block_public_acls"
    else
        print_status "ERROR" "  BlockPublicAcls: $block_public_acls (should be true)"
        all_secure=false
    fi
    
    if [ "$ignore_public_acls" = "true" ]; then
        print_status "SUCCESS" "  IgnorePublicAcls: $ignore_public_acls"
    else
        print_status "ERROR" "  IgnorePublicAcls: $ignore_public_acls (should be true)"
        all_secure=false
    fi
    
    if [ "$block_public_policy" = "true" ]; then
        print_status "SUCCESS" "  BlockPublicPolicy: $block_public_policy"
    else
        print_status "ERROR" "  BlockPublicPolicy: $block_public_policy (should be true)"
        all_secure=false
    fi
    
    if [ "$restrict_public_buckets" = "true" ]; then
        print_status "SUCCESS" "  RestrictPublicBuckets: $restrict_public_buckets"
    else
        print_status "ERROR" "  RestrictPublicBuckets: $restrict_public_buckets (should be true)"
        all_secure=false
    fi
    
    if [ "$all_secure" = true ]; then
        print_status "SUCCESS" "Bucket $bucket_name is properly secured"
        return 0
    else
        print_status "ERROR" "Bucket $bucket_name has security issues"
        return 1
    fi
}

# Function to test unauthorized access
test_unauthorized_access() {
    local bucket_name=$1
    local test_file="/tmp/test_upload_$(date +%s).txt"
    
    print_status "INFO" "Testing unauthorized access to bucket: $bucket_name"
    
    # Create a test file
    echo "Test file for unauthorized access check" > "$test_file"
    
    # Try to upload without authentication (this should fail)
    if aws s3 cp "$test_file" "s3://$bucket_name/" --no-sign-request 2>/dev/null; then
        print_status "ERROR" "Unauthorized upload succeeded - bucket is not secure!"
        rm -f "$test_file"
        return 1
    else
        print_status "SUCCESS" "Unauthorized upload blocked - bucket is secure"
        rm -f "$test_file"
        return 0
    fi
}

# Main validation function
main() {
    echo "=========================================="
    echo "S3 Security Validation Script"
    echo "Addresses Security Issue S3.3"
    echo "=========================================="
    echo
    
    # Check if AWS CLI is available
    if ! command -v aws &> /dev/null; then
        print_status "ERROR" "AWS CLI is not installed or not in PATH"
        exit 1
    fi
    
    # Check if jq is available
    if ! command -v jq &> /dev/null; then
        print_status "ERROR" "jq is not installed or not in PATH"
        exit 1
    fi
    
    # Check AWS credentials
    if ! aws sts get-caller-identity >/dev/null 2>&1; then
        print_status "ERROR" "AWS credentials are not configured"
        exit 1
    fi
    
    print_status "SUCCESS" "Prerequisites check passed"
    echo
    
    # Get bucket names from command line arguments or use default pattern
    local buckets=()
    if [ $# -eq 0 ]; then
        print_status "INFO" "No bucket names provided. Looking for DropBox buckets..."
        # Try to find buckets with common DropBox patterns
        mapfile -t buckets < <(aws s3api list-buckets --query 'Buckets[?contains(Name, `dropbox`) || contains(Name, `drop-box`)].Name' --output text 2>/dev/null || true)
        
        if [ ${#buckets[@]} -eq 0 ]; then
            print_status "WARNING" "No DropBox buckets found. Please provide bucket names as arguments."
            echo "Usage: $0 <bucket1> <bucket2> ..."
            exit 1
        fi
    else
        buckets=("$@")
    fi
    
    print_status "INFO" "Validating ${#buckets[@]} bucket(s)"
    echo
    
    local failed_validations=0
    
    # Validate each bucket
    for bucket in "${buckets[@]}"; do
        if [ -n "$bucket" ]; then  # Skip empty bucket names
            if ! validate_bucket_security "$bucket"; then
                ((failed_validations++))
            fi
            
            # Test unauthorized access
            if ! test_unauthorized_access "$bucket"; then
                ((failed_validations++))
            fi
            
            echo
        fi
    done
    
    # Summary
    echo "=========================================="
    echo "Validation Summary"
    echo "=========================================="
    
    if [ $failed_validations -eq 0 ]; then
        print_status "SUCCESS" "All buckets passed security validation"
        print_status "SUCCESS" "S3.3 security requirement is satisfied"
        exit 0
    else
        print_status "ERROR" "$failed_validations validation(s) failed"
        print_status "ERROR" "S3.3 security requirement is NOT satisfied"
        exit 1
    fi
}

# Run main function with all arguments
main "$@"