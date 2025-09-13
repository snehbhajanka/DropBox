#!/bin/bash

# S3 Bucket Security Validation Script
# This script validates that S3 buckets are properly configured to block public write access

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
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
        print_status $RED "❌ AWS CLI is not installed. Please install it first."
        exit 1
    fi
    print_status $GREEN "✅ AWS CLI is available"
}

# Function to validate bucket exists
validate_bucket_exists() {
    local bucket_name=$1
    
    if aws s3api head-bucket --bucket "$bucket_name" 2>/dev/null; then
        print_status $GREEN "✅ Bucket '$bucket_name' exists and is accessible"
        return 0
    else
        print_status $RED "❌ Bucket '$bucket_name' does not exist or is not accessible"
        return 1
    fi
}

# Function to check public access block settings
check_public_access_block() {
    local bucket_name=$1
    
    print_status $YELLOW "🔍 Checking Public Access Block settings for bucket: $bucket_name"
    
    local pab_config
    pab_config=$(aws s3api get-public-access-block --bucket "$bucket_name" --output json 2>/dev/null)
    
    if [ $? -eq 0 ]; then
        local block_public_acls=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.BlockPublicAcls')
        local ignore_public_acls=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.IgnorePublicAcls')
        local block_public_policy=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.BlockPublicPolicy')
        local restrict_public_buckets=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.RestrictPublicBuckets')
        
        echo "Public Access Block Configuration:"
        echo "  BlockPublicAcls: $block_public_acls"
        echo "  IgnorePublicAcls: $ignore_public_acls"
        echo "  BlockPublicPolicy: $block_public_policy"
        echo "  RestrictPublicBuckets: $restrict_public_buckets"
        
        if [ "$block_public_acls" = "true" ] && [ "$ignore_public_acls" = "true" ] && [ "$block_public_policy" = "true" ] && [ "$restrict_public_buckets" = "true" ]; then
            print_status $GREEN "✅ All Public Access Block settings are correctly configured"
            return 0
        else
            print_status $RED "❌ Public Access Block settings are not properly configured"
            return 1
        fi
    else
        print_status $RED "❌ Failed to retrieve Public Access Block settings"
        return 1
    fi
}

# Function to test public write access (should fail)
test_public_write_access() {
    local bucket_name=$1
    
    print_status $YELLOW "🔍 Testing public write access (should be denied)..."
    
    # Create a temporary test file
    local test_file=$(mktemp)
    echo "This is a test file to verify public write access is blocked" > "$test_file"
    
    # Attempt to upload without authentication (should fail)
    if aws s3 cp "$test_file" "s3://$bucket_name/public-write-test.txt" --no-sign-request 2>/dev/null; then
        print_status $RED "❌ SECURITY RISK: Public write access is allowed!"
        rm "$test_file"
        return 1
    else
        print_status $GREEN "✅ Public write access is correctly blocked"
        rm "$test_file"
        return 0
    fi
}

# Function to check bucket encryption
check_bucket_encryption() {
    local bucket_name=$1
    
    print_status $YELLOW "🔍 Checking bucket encryption settings..."
    
    local encryption_config
    encryption_config=$(aws s3api get-bucket-encryption --bucket "$bucket_name" --output json 2>/dev/null)
    
    if [ $? -eq 0 ]; then
        local sse_algorithm=$(echo "$encryption_config" | jq -r '.ServerSideEncryptionConfiguration.Rules[0].ApplyServerSideEncryptionByDefault.SSEAlgorithm')
        print_status $GREEN "✅ Bucket encryption is enabled (Algorithm: $sse_algorithm)"
        return 0
    else
        print_status $YELLOW "⚠️  Bucket encryption configuration not found"
        return 1
    fi
}

# Function to check bucket versioning
check_bucket_versioning() {
    local bucket_name=$1
    
    print_status $YELLOW "🔍 Checking bucket versioning settings..."
    
    local versioning_config
    versioning_config=$(aws s3api get-bucket-versioning --bucket "$bucket_name" --output json 2>/dev/null)
    
    if [ $? -eq 0 ]; then
        local status=$(echo "$versioning_config" | jq -r '.Status // "Disabled"')
        if [ "$status" = "Enabled" ]; then
            print_status $GREEN "✅ Bucket versioning is enabled"
            return 0
        else
            print_status $YELLOW "⚠️  Bucket versioning is not enabled"
            return 1
        fi
    else
        print_status $RED "❌ Failed to retrieve bucket versioning configuration"
        return 1
    fi
}

# Main validation function
run_security_validation() {
    local bucket_name=$1
    
    if [ -z "$bucket_name" ]; then
        print_status $RED "❌ Usage: $0 <bucket-name>"
        exit 1
    fi
    
    print_status $YELLOW "🔒 Starting S3 Security Validation for bucket: $bucket_name"
    echo "=================================================="
    
    local total_checks=0
    local passed_checks=0
    
    # Check AWS CLI
    check_aws_cli
    
    # Validate bucket exists
    if validate_bucket_exists "$bucket_name"; then
        ((total_checks++))
        ((passed_checks++))
    else
        ((total_checks++))
        print_status $RED "❌ Cannot proceed with validation - bucket not accessible"
        exit 1
    fi
    
    # Check Public Access Block
    if check_public_access_block "$bucket_name"; then
        ((passed_checks++))
    fi
    ((total_checks++))
    
    # Test public write access
    if test_public_write_access "$bucket_name"; then
        ((passed_checks++))
    fi
    ((total_checks++))
    
    # Check encryption
    if check_bucket_encryption "$bucket_name"; then
        ((passed_checks++))
    fi
    ((total_checks++))
    
    # Check versioning
    if check_bucket_versioning "$bucket_name"; then
        ((passed_checks++))
    fi
    ((total_checks++))
    
    echo "=================================================="
    print_status $YELLOW "📊 Validation Summary:"
    echo "  Total checks: $total_checks"
    echo "  Passed checks: $passed_checks"
    echo "  Failed checks: $((total_checks - passed_checks))"
    
    if [ $passed_checks -eq $total_checks ]; then
        print_status $GREEN "🎉 All security validations passed! Bucket is properly secured."
        exit 0
    elif [ $passed_checks -ge 3 ]; then
        print_status $YELLOW "⚠️  Most security validations passed, but some improvements are recommended."
        exit 0
    else
        print_status $RED "❌ Critical security issues found. Please review and fix the configuration."
        exit 1
    fi
}

# Check if jq is available for JSON parsing
if ! command -v jq &> /dev/null; then
    print_status $RED "❌ jq is required for JSON parsing. Please install it first."
    exit 1
fi

# Run the validation
run_security_validation "$1"