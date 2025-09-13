#!/bin/bash

# S3 Security Validation Script
# This script validates that S3 buckets have proper security configurations
# to prevent public write access as required by security misconfiguration S3.3

set -e

# Configuration
BUCKET_NAME="${1:-dropbox-secure-storage-dev}"
AWS_REGION="${2:-us-east-1}"

echo "=== S3 Security Validation Script ==="
echo "Bucket: $BUCKET_NAME"
echo "Region: $AWS_REGION"
echo "======================================="

# Check if AWS CLI is available
if ! command -v aws &> /dev/null; then
    echo "❌ AWS CLI is not installed or not in PATH"
    echo "Please install AWS CLI to run this validation"
    exit 1
fi

# Check AWS credentials
if ! aws sts get-caller-identity &> /dev/null; then
    echo "❌ AWS credentials not configured"
    echo "Please configure AWS credentials using 'aws configure' or environment variables"
    exit 1
fi

echo "✅ AWS CLI configured and credentials available"

# Function to check if bucket exists
check_bucket_exists() {
    if aws s3api head-bucket --bucket "$BUCKET_NAME" --region "$AWS_REGION" 2>/dev/null; then
        echo "✅ Bucket $BUCKET_NAME exists"
        return 0
    else
        echo "❌ Bucket $BUCKET_NAME does not exist or is not accessible"
        return 1
    fi
}

# Function to validate public access block settings
validate_public_access_block() {
    echo ""
    echo "🔍 Checking Public Access Block settings..."
    
    local response
    response=$(aws s3api get-public-access-block --bucket "$BUCKET_NAME" --region "$AWS_REGION" 2>/dev/null || echo "ERROR")
    
    if [ "$response" = "ERROR" ]; then
        echo "❌ Failed to get public access block settings"
        return 1
    fi
    
    local block_public_acls
    local ignore_public_acls
    local block_public_policy
    local restrict_public_buckets
    
    block_public_acls=$(echo "$response" | grep -o '"BlockPublicAcls": *[^,}]*' | awk -F': *' '{print $2}')
    ignore_public_acls=$(echo "$response" | grep -o '"IgnorePublicAcls": *[^,}]*' | awk -F': *' '{print $2}')
    block_public_policy=$(echo "$response" | grep -o '"BlockPublicPolicy": *[^,}]*' | awk -F': *' '{print $2}')
    restrict_public_buckets=$(echo "$response" | grep -o '"RestrictPublicBuckets": *[^,}]*' | awk -F': *' '{print $2}')
    
    local all_secure=true
    
    if [ "$block_public_acls" = "true" ]; then
        echo "✅ BlockPublicAcls: true"
    else
        echo "❌ BlockPublicAcls: $block_public_acls (should be true)"
        all_secure=false
    fi
    
    if [ "$ignore_public_acls" = "true" ]; then
        echo "✅ IgnorePublicAcls: true"
    else
        echo "❌ IgnorePublicAcls: $ignore_public_acls (should be true)"
        all_secure=false
    fi
    
    if [ "$block_public_policy" = "true" ]; then
        echo "✅ BlockPublicPolicy: true"
    else
        echo "❌ BlockPublicPolicy: $block_public_policy (should be true)"
        all_secure=false
    fi
    
    if [ "$restrict_public_buckets" = "true" ]; then
        echo "✅ RestrictPublicBuckets: true"
    else
        echo "❌ RestrictPublicBuckets: $restrict_public_buckets (should be true)"
        all_secure=false
    fi
    
    if [ "$all_secure" = "true" ]; then
        echo "✅ All public access block settings are secure"
        return 0
    else
        echo "❌ Some public access block settings are not secure"
        return 1
    fi
}

# Function to check bucket policy for public write denial
validate_bucket_policy() {
    echo ""
    echo "🔍 Checking bucket policy for public write access denial..."
    
    local policy
    policy=$(aws s3api get-bucket-policy --bucket "$BUCKET_NAME" --region "$AWS_REGION" --query 'Policy' --output text 2>/dev/null || echo "NO_POLICY")
    
    if [ "$policy" = "NO_POLICY" ] || [ "$policy" = "None" ]; then
        echo "⚠️  No bucket policy found"
        echo "ℹ️  With public access block enabled, this may still be secure"
        return 0
    fi
    
    # Check if policy contains deny statements for public write access
    if echo "$policy" | grep -q '"Effect": *"Deny"' && echo "$policy" | grep -q '"Principal": *"\*"'; then
        echo "✅ Bucket policy contains public access denial"
        
        # Check for specific write actions
        local write_actions_denied=false
        if echo "$policy" | grep -q "s3:PutObject\|s3:DeleteObject"; then
            write_actions_denied=true
        fi
        
        if [ "$write_actions_denied" = "true" ]; then
            echo "✅ Bucket policy denies public write operations"
        else
            echo "⚠️  Bucket policy may not specifically deny all write operations"
        fi
    else
        echo "⚠️  Bucket policy does not contain explicit public access denial"
    fi
    
    return 0
}

# Function to test public write access (should fail)
test_public_write_access() {
    echo ""
    echo "🔍 Testing public write access (should be blocked)..."
    
    # Create a temporary test file
    local test_file="/tmp/s3-test-file-$(date +%s).txt"
    echo "test content" > "$test_file"
    
    # Try to upload without credentials (should fail)
    if aws s3 cp "$test_file" "s3://$BUCKET_NAME/test-public-write.txt" --no-sign-request 2>/dev/null; then
        echo "❌ SECURITY RISK: Public write access is ALLOWED"
        rm -f "$test_file"
        return 1
    else
        echo "✅ Public write access is properly BLOCKED"
        rm -f "$test_file"
        return 0
    fi
}

# Function to check bucket encryption
check_bucket_encryption() {
    echo ""
    echo "🔍 Checking bucket encryption settings..."
    
    local encryption
    encryption=$(aws s3api get-bucket-encryption --bucket "$BUCKET_NAME" --region "$AWS_REGION" 2>/dev/null || echo "NO_ENCRYPTION")
    
    if [ "$encryption" = "NO_ENCRYPTION" ]; then
        echo "⚠️  No server-side encryption configured"
    else
        echo "✅ Server-side encryption is configured"
    fi
}

# Function to check bucket versioning
check_bucket_versioning() {
    echo ""
    echo "🔍 Checking bucket versioning..."
    
    local versioning
    versioning=$(aws s3api get-bucket-versioning --bucket "$BUCKET_NAME" --region "$AWS_REGION" --query 'Status' --output text 2>/dev/null || echo "None")
    
    if [ "$versioning" = "Enabled" ]; then
        echo "✅ Bucket versioning is enabled"
    else
        echo "ℹ️  Bucket versioning is not enabled"
    fi
}

# Main validation flow
main() {
    local overall_status=0
    
    # Check if bucket exists
    if ! check_bucket_exists; then
        echo ""
        echo "❌ Cannot proceed with validation - bucket not accessible"
        exit 1
    fi
    
    # Validate public access block settings
    if ! validate_public_access_block; then
        overall_status=1
    fi
    
    # Validate bucket policy
    validate_bucket_policy
    
    # Test public write access
    if ! test_public_write_access; then
        overall_status=1
    fi
    
    # Additional security checks
    check_bucket_encryption
    check_bucket_versioning
    
    echo ""
    echo "======================================="
    if [ $overall_status -eq 0 ]; then
        echo "✅ S3 SECURITY VALIDATION PASSED"
        echo "✅ Public write access is properly blocked"
        echo "✅ Bucket meets security requirements for S3.3"
    else
        echo "❌ S3 SECURITY VALIDATION FAILED"
        echo "❌ Security issues found that need to be addressed"
        echo "❌ Bucket does not meet security requirements for S3.3"
    fi
    echo "======================================="
    
    exit $overall_status
}

# Show usage if no arguments provided
if [ $# -eq 0 ]; then
    echo "Usage: $0 [bucket-name] [aws-region]"
    echo "Example: $0 dropbox-secure-storage-dev us-east-1"
    echo ""
    echo "Environment variables:"
    echo "  AWS_PROFILE - AWS profile to use"
    echo "  AWS_REGION  - AWS region (default: us-east-1)"
    echo ""
fi

# Run main validation
main "$@"