#!/bin/bash

# S3 Security Validation Script
# This script validates that S3 buckets are properly secured against public write access

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_header() {
    echo -e "${BLUE}[VALIDATION]${NC} $1"
}

# Function to validate a single bucket
validate_bucket() {
    local bucket_name=$1
    local validation_passed=true
    
    print_header "Validating bucket: $bucket_name"
    echo "----------------------------------------"
    
    # Check if bucket exists
    if ! aws s3api head-bucket --bucket "$bucket_name" 2>/dev/null; then
        print_error "❌ Bucket '$bucket_name' does not exist or is not accessible"
        return 1
    fi
    
    # Get public access block configuration
    local pab_config
    if pab_config=$(aws s3api get-public-access-block --bucket "$bucket_name" 2>/dev/null); then
        local block_public_acls=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.BlockPublicAcls // false')
        local ignore_public_acls=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.IgnorePublicAcls // false')
        local block_public_policy=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.BlockPublicPolicy // false')
        local restrict_public_buckets=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.RestrictPublicBuckets // false')
        
        # Validate each setting
        if [ "$block_public_acls" = "true" ]; then
            print_status "✅ Block Public ACLs: $block_public_acls"
        else
            print_error "❌ Block Public ACLs: $block_public_acls (should be true)"
            validation_passed=false
        fi
        
        if [ "$ignore_public_acls" = "true" ]; then
            print_status "✅ Ignore Public ACLs: $ignore_public_acls"
        else
            print_error "❌ Ignore Public ACLs: $ignore_public_acls (should be true)"
            validation_passed=false
        fi
        
        if [ "$block_public_policy" = "true" ]; then
            print_status "✅ Block Public Policy: $block_public_policy"
        else
            print_error "❌ Block Public Policy: $block_public_policy (should be true)"
            validation_passed=false
        fi
        
        if [ "$restrict_public_buckets" = "true" ]; then
            print_status "✅ Restrict Public Buckets: $restrict_public_buckets"
        else
            print_error "❌ Restrict Public Buckets: $restrict_public_buckets (should be true)"
            validation_passed=false
        fi
    else
        print_error "❌ No public access block configuration found"
        validation_passed=false
    fi
    
    # Check bucket ACL for public access
    local bucket_acl
    if bucket_acl=$(aws s3api get-bucket-acl --bucket "$bucket_name" 2>/dev/null); then
        local public_grants=$(echo "$bucket_acl" | jq -r '.Grants[] | select(.Grantee.URI // "" | contains("AllUsers") or contains("AuthenticatedUsers")) | .Permission' 2>/dev/null)
        if [ -z "$public_grants" ]; then
            print_status "✅ No public ACL grants found"
        else
            print_error "❌ Public ACL grants found: $public_grants"
            validation_passed=false
        fi
    else
        print_warning "⚠️  Could not retrieve bucket ACL"
    fi
    
    # Check bucket policy for public access
    local bucket_policy
    if bucket_policy=$(aws s3api get-bucket-policy --bucket "$bucket_name" 2>/dev/null); then
        local public_statements=$(echo "$bucket_policy" | jq -r '.Policy | fromjson | .Statement[] | select(.Principal == "*" or (.Principal.AWS // [] | contains(["*"]))) | .Effect' 2>/dev/null)
        if [ -n "$public_statements" ]; then
            print_warning "⚠️  Bucket policy contains statements with public principal"
            echo "$bucket_policy" | jq '.Policy | fromjson | .Statement[] | select(.Principal == "*" or (.Principal.AWS // [] | contains(["*"])))'
        else
            print_status "✅ No public statements in bucket policy"
        fi
    else
        print_status "✅ No bucket policy (no public access via policy)"
    fi
    
    # Test public access
    print_header "Testing public access (should be denied)..."
    if aws s3 ls "s3://$bucket_name" --no-sign-request &>/dev/null; then
        print_error "❌ CRITICAL: Bucket allows public read access!"
        validation_passed=false
    else
        print_status "✅ Public read access is properly denied"
    fi
    
    echo "----------------------------------------"
    if [ "$validation_passed" = true ]; then
        print_status "✅ Bucket '$bucket_name' passed all security validations"
        return 0
    else
        print_error "❌ Bucket '$bucket_name' FAILED security validation"
        return 1
    fi
}

# Function to validate all buckets in the account
validate_all_buckets() {
    print_header "Validating all S3 buckets in the account"
    echo "=========================================="
    
    local buckets
    if ! buckets=$(aws s3api list-buckets --query 'Buckets[].Name' --output text 2>/dev/null); then
        print_error "Failed to list buckets"
        return 1
    fi
    
    if [ -z "$buckets" ]; then
        print_warning "No buckets found in the account"
        return 0
    fi
    
    local total_buckets=0
    local failed_buckets=0
    
    for bucket in $buckets; do
        total_buckets=$((total_buckets + 1))
        if ! validate_bucket "$bucket"; then
            failed_buckets=$((failed_buckets + 1))
        fi
        echo
    done
    
    echo "=========================================="
    print_header "Validation Summary"
    echo "Total buckets: $total_buckets"
    echo "Failed validations: $failed_buckets"
    echo "Passed validations: $((total_buckets - failed_buckets))"
    
    if [ $failed_buckets -eq 0 ]; then
        print_status "✅ All buckets passed security validation!"
        return 0
    else
        print_error "❌ $failed_buckets bucket(s) failed security validation"
        return 1
    fi
}

# Main function
main() {
    echo "=================================================="
    echo "S3 Security Validation Script"
    echo "Checking compliance with Control ID: S3.3"
    echo "=================================================="
    
    # Check if AWS CLI is installed
    if ! command -v aws &> /dev/null; then
        print_error "AWS CLI is not installed"
        exit 1
    fi
    
    # Check if jq is installed
    if ! command -v jq &> /dev/null; then
        print_error "jq is not installed. Please install jq to use this script."
        exit 1
    fi
    
    # Check AWS credentials
    if ! aws sts get-caller-identity &> /dev/null; then
        print_error "AWS credentials not configured"
        exit 1
    fi
    
    if [ $# -eq 0 ]; then
        # Validate all buckets
        validate_all_buckets
    else
        # Validate specific bucket(s)
        local exit_code=0
        for bucket_name in "$@"; do
            if ! validate_bucket "$bucket_name"; then
                exit_code=1
            fi
            echo
        done
        exit $exit_code
    fi
}

# Show usage if --help is provided
if [ "$1" = "--help" ] || [ "$1" = "-h" ]; then
    echo "Usage: $0 [bucket-name ...]"
    echo ""
    echo "Validates S3 bucket security configuration against public write access."
    echo ""
    echo "Options:"
    echo "  --help, -h    Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                    # Validate all buckets in the account"
    echo "  $0 my-bucket         # Validate a specific bucket"
    echo "  $0 bucket1 bucket2   # Validate multiple specific buckets"
    exit 0
fi

# Run main function with all arguments
main "$@"