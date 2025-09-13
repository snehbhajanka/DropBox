#!/bin/bash

# S3 Security Verification Script
# This script validates that S3 buckets have proper security configurations

set -e

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    local color=$1
    local message=$2
    echo -e "${color}${message}${NC}"
}

# Function to check if AWS CLI is installed
check_aws_cli() {
    if ! command -v aws &> /dev/null; then
        print_status $RED "ERROR: AWS CLI is not installed. Please install it first."
        exit 1
    fi
    print_status $GREEN "✓ AWS CLI is available"
}

# Function to validate bucket security settings
validate_bucket_security() {
    local bucket_name=$1
    
    print_status $YELLOW "Checking security settings for bucket: $bucket_name"
    
    # Check if bucket exists
    if ! aws s3 ls "s3://$bucket_name" &> /dev/null; then
        print_status $RED "ERROR: Bucket $bucket_name does not exist or is not accessible"
        return 1
    fi
    
    # Get public access block configuration
    local pab_config=$(aws s3api get-public-access-block --bucket "$bucket_name" 2>/dev/null || echo "NOTFOUND")
    
    if [ "$pab_config" = "NOTFOUND" ]; then
        print_status $RED "ERROR: Public Access Block not configured for $bucket_name"
        return 1
    fi
    
    # Parse the configuration
    local block_public_acls=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.BlockPublicAcls // false')
    local ignore_public_acls=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.IgnorePublicAcls // false')
    local block_public_policy=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.BlockPublicPolicy // false')
    local restrict_public_buckets=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.RestrictPublicBuckets // false')
    
    # Validate each setting
    local security_compliant=true
    
    if [ "$block_public_acls" != "true" ]; then
        print_status $RED "✗ BlockPublicAcls is not enabled"
        security_compliant=false
    else
        print_status $GREEN "✓ BlockPublicAcls is enabled"
    fi
    
    if [ "$ignore_public_acls" != "true" ]; then
        print_status $RED "✗ IgnorePublicAcls is not enabled"
        security_compliant=false
    else
        print_status $GREEN "✓ IgnorePublicAcls is enabled"
    fi
    
    if [ "$block_public_policy" != "true" ]; then
        print_status $RED "✗ BlockPublicPolicy is not enabled"
        security_compliant=false
    else
        print_status $GREEN "✓ BlockPublicPolicy is enabled"
    fi
    
    if [ "$restrict_public_buckets" != "true" ]; then
        print_status $RED "✗ RestrictPublicBuckets is not enabled"
        security_compliant=false
    else
        print_status $GREEN "✓ RestrictPublicBuckets is enabled"
    fi
    
    if [ "$security_compliant" = "true" ]; then
        print_status $GREEN "✓ Bucket $bucket_name is security compliant"
        return 0
    else
        print_status $RED "✗ Bucket $bucket_name has security issues"
        return 1
    fi
}

# Function to apply security settings to a bucket
apply_security_settings() {
    local bucket_name=$1
    
    print_status $YELLOW "Applying security settings to bucket: $bucket_name"
    
    # Apply Public Access Block settings
    aws s3api put-public-access-block \
        --bucket "$bucket_name" \
        --public-access-block-configuration \
        BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
    
    if [ $? -eq 0 ]; then
        print_status $GREEN "✓ Security settings applied successfully to $bucket_name"
    else
        print_status $RED "✗ Failed to apply security settings to $bucket_name"
        return 1
    fi
}

# Function to test unauthorized access (should be blocked)
test_unauthorized_access() {
    local bucket_name=$1
    local test_file="test-unauthorized-access.txt"
    
    print_status $YELLOW "Testing unauthorized access to bucket: $bucket_name"
    
    # Create a temporary test file
    echo "This is a test file for unauthorized access testing" > "/tmp/$test_file"
    
    # Try to upload the file (this should fail)
    if aws s3 cp "/tmp/$test_file" "s3://$bucket_name/$test_file" &> /dev/null; then
        print_status $RED "WARNING: Unauthorized upload succeeded! Bucket may have public write access."
        # Clean up the uploaded file
        aws s3 rm "s3://$bucket_name/$test_file" &> /dev/null
        rm -f "/tmp/$test_file"
        return 1
    else
        print_status $GREEN "✓ Unauthorized access is properly blocked"
        rm -f "/tmp/$test_file"
        return 0
    fi
}

# Main execution
main() {
    print_status $YELLOW "Starting S3 Security Validation..."
    
    # Check prerequisites
    check_aws_cli
    
    # Check if jq is available for JSON parsing
    if ! command -v jq &> /dev/null; then
        print_status $RED "ERROR: jq is not installed. Please install it for JSON parsing."
        exit 1
    fi
    
    # Get bucket name from argument or use default
    local bucket_name=${1:-"dropbox-secure-storage"}
    
    if [ "$#" -eq 0 ]; then
        print_status $YELLOW "No bucket name provided. Using default: $bucket_name"
        print_status $YELLOW "Usage: $0 <bucket-name> [action]"
        print_status $YELLOW "Actions: validate (default), apply, test"
    fi
    
    local action=${2:-"validate"}
    
    case $action in
        "validate")
            validate_bucket_security "$bucket_name"
            ;;
        "apply")
            apply_security_settings "$bucket_name"
            ;;
        "test")
            test_unauthorized_access "$bucket_name"
            ;;
        *)
            print_status $RED "Invalid action: $action"
            print_status $YELLOW "Valid actions: validate, apply, test"
            exit 1
            ;;
    esac
}

# Run main function with all arguments
main "$@"