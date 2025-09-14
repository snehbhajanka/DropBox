#!/bin/bash

# S3 Bucket Security Validation Script
# This script validates that all S3 buckets have proper public access block settings
# as required by security misconfiguration S3.3

set -e

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Bucket names to validate
BUCKET_NAMES=(
    "dropbox-app-storage-bucket-1"
    "dropbox-app-storage-bucket-2"
    "dropbox-app-storage-bucket-3"
    "dropbox-app-backup-bucket-1"
    "dropbox-app-backup-bucket-2"
    "dropbox-app-logs-bucket-1"
    "dropbox-app-logs-bucket-2"
    "dropbox-app-temp-bucket-1"
    "dropbox-app-temp-bucket-2"
    "dropbox-app-uploads-bucket-1"
    "dropbox-app-uploads-bucket-2"
    "dropbox-app-config-bucket-1"
    "dropbox-app-archive-bucket-1"
)

echo -e "${YELLOW}S3 Bucket Security Validation - S3.3 Compliance Check${NC}"
echo "========================================================="
echo

# Function to check if AWS CLI is installed
check_aws_cli() {
    if ! command -v aws &> /dev/null; then
        echo -e "${RED}Error: AWS CLI is not installed. Please install AWS CLI first.${NC}"
        exit 1
    fi
}

# Function to check AWS credentials
check_aws_credentials() {
    if ! aws sts get-caller-identity &> /dev/null; then
        echo -e "${RED}Error: AWS credentials not configured. Please configure AWS credentials.${NC}"
        exit 1
    fi
}

# Function to validate public access block settings
validate_public_access_block() {
    local bucket_name=$1
    echo -n "Checking bucket: $bucket_name ... "
    
    # Get public access block configuration
    if ! aws s3api get-public-access-block --bucket "$bucket_name" --output json 2>/dev/null; then
        echo -e "${RED}FAILED - Bucket not found or no access${NC}"
        return 1
    fi
    
    local pab_config=$(aws s3api get-public-access-block --bucket "$bucket_name" --output json 2>/dev/null)
    
    if [ $? -ne 0 ]; then
        echo -e "${RED}FAILED - Cannot get public access block settings${NC}"
        return 1
    fi
    
    # Parse the configuration
    local block_public_acls=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.BlockPublicAcls')
    local ignore_public_acls=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.IgnorePublicAcls')
    local block_public_policy=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.BlockPublicPolicy')
    local restrict_public_buckets=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.RestrictPublicBuckets')
    
    # Validate all settings are true
    if [[ "$block_public_acls" == "true" && "$ignore_public_acls" == "true" && 
          "$block_public_policy" == "true" && "$restrict_public_buckets" == "true" ]]; then
        echo -e "${GREEN}PASSED${NC}"
        return 0
    else
        echo -e "${RED}FAILED${NC}"
        echo "  BlockPublicAcls: $block_public_acls (should be true)"
        echo "  IgnorePublicAcls: $ignore_public_acls (should be true)"
        echo "  BlockPublicPolicy: $block_public_policy (should be true)"
        echo "  RestrictPublicBuckets: $restrict_public_buckets (should be true)"
        return 1
    fi
}

# Function to test unauthorized access
test_unauthorized_access() {
    local bucket_name=$1
    echo -n "Testing unauthorized access for $bucket_name ... "
    
    # Try to list bucket contents without credentials (should fail)
    if aws s3 ls "s3://$bucket_name" --no-sign-request 2>/dev/null; then
        echo -e "${RED}FAILED - Bucket allows unauthorized read access${NC}"
        return 1
    else
        echo -e "${GREEN}PASSED - Unauthorized access blocked${NC}"
        return 0
    fi
}

# Main validation function
main() {
    echo "Starting S3 bucket security validation..."
    echo
    
    # Check prerequisites
    check_aws_cli
    check_aws_credentials
    
    local total_buckets=${#BUCKET_NAMES[@]}
    local passed_buckets=0
    local failed_buckets=0
    
    echo "Validating public access block settings for $total_buckets buckets:"
    echo
    
    # Validate each bucket
    for bucket_name in "${BUCKET_NAMES[@]}"; do
        if validate_public_access_block "$bucket_name"; then
            ((passed_buckets++))
        else
            ((failed_buckets++))
        fi
    done
    
    echo
    echo "Testing unauthorized access prevention:"
    echo
    
    # Test unauthorized access for a few buckets
    for bucket_name in "${BUCKET_NAMES[@]:0:3}"; do
        test_unauthorized_access "$bucket_name"
    done
    
    echo
    echo "========================================================="
    echo "Validation Summary:"
    echo "  Total buckets checked: $total_buckets"
    echo -e "  Buckets passed: ${GREEN}$passed_buckets${NC}"
    echo -e "  Buckets failed: ${RED}$failed_buckets${NC}"
    
    if [ $failed_buckets -eq 0 ]; then
        echo -e "${GREEN}✓ All buckets are properly secured (S3.3 compliant)${NC}"
        echo -e "${GREEN}✓ Public write access is blocked on all buckets${NC}"
        exit 0
    else
        echo -e "${RED}✗ Some buckets are not properly secured${NC}"
        echo -e "${RED}✗ Please review and fix the failed buckets${NC}"
        exit 1
    fi
}

# Show help
show_help() {
    echo "Usage: $0 [OPTIONS]"
    echo
    echo "S3 Bucket Security Validation Script"
    echo "Validates S3 buckets for proper public access block settings"
    echo
    echo "Options:"
    echo "  -h, --help     Show this help message"
    echo "  -v, --verbose  Enable verbose output"
    echo
    echo "This script checks all 13 DropBox application buckets for:"
    echo "  - BlockPublicAcls = true"
    echo "  - IgnorePublicAcls = true"
    echo "  - BlockPublicPolicy = true"
    echo "  - RestrictPublicBuckets = true"
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
        *)
            echo "Unknown option: $1"
            show_help
            exit 1
            ;;
    esac
done

# Run main function
main