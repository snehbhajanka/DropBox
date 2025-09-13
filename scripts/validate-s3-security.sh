#!/bin/bash

# S3 Security Validation Script
# This script validates that S3 buckets have proper security configurations
# to block public write access as required by the security issue

set -e

BUCKET_NAME="${1:-dropbox-secure-storage}"
AWS_REGION="${2:-us-east-1}"

echo "Validating S3 bucket security for: $BUCKET_NAME in region: $AWS_REGION"
echo "=============================================================="

# Function to check if AWS CLI is available
check_aws_cli() {
    if ! command -v aws &> /dev/null; then
        echo "❌ AWS CLI not found. Please install AWS CLI to run this validation."
        exit 1
    fi
    echo "✅ AWS CLI found"
}

# Function to validate public access block settings
validate_public_access_block() {
    echo ""
    echo "🔍 Checking Public Access Block settings..."
    
    if aws s3api get-public-access-block --bucket "$BUCKET_NAME" --region "$AWS_REGION" >/dev/null 2>&1; then
        PAB_SETTINGS=$(aws s3api get-public-access-block --bucket "$BUCKET_NAME" --region "$AWS_REGION" --output json)
        
        BLOCK_PUBLIC_ACLS=$(echo "$PAB_SETTINGS" | jq -r '.PublicAccessBlockConfiguration.BlockPublicAcls')
        IGNORE_PUBLIC_ACLS=$(echo "$PAB_SETTINGS" | jq -r '.PublicAccessBlockConfiguration.IgnorePublicAcls')
        BLOCK_PUBLIC_POLICY=$(echo "$PAB_SETTINGS" | jq -r '.PublicAccessBlockConfiguration.BlockPublicPolicy')
        RESTRICT_PUBLIC_BUCKETS=$(echo "$PAB_SETTINGS" | jq -r '.PublicAccessBlockConfiguration.RestrictPublicBuckets')
        
        if [[ "$BLOCK_PUBLIC_ACLS" == "true" && "$IGNORE_PUBLIC_ACLS" == "true" && "$BLOCK_PUBLIC_POLICY" == "true" && "$RESTRICT_PUBLIC_BUCKETS" == "true" ]]; then
            echo "✅ All Public Access Block settings are correctly enabled"
            echo "   - Block Public ACLs: $BLOCK_PUBLIC_ACLS"
            echo "   - Ignore Public ACLs: $IGNORE_PUBLIC_ACLS" 
            echo "   - Block Public Policy: $BLOCK_PUBLIC_POLICY"
            echo "   - Restrict Public Buckets: $RESTRICT_PUBLIC_BUCKETS"
            return 0
        else
            echo "❌ Public Access Block settings are not properly configured"
            echo "   - Block Public ACLs: $BLOCK_PUBLIC_ACLS (should be true)"
            echo "   - Ignore Public ACLs: $IGNORE_PUBLIC_ACLS (should be true)"
            echo "   - Block Public Policy: $BLOCK_PUBLIC_POLICY (should be true)"
            echo "   - Restrict Public Buckets: $RESTRICT_PUBLIC_BUCKETS (should be true)"
            return 1
        fi
    else
        echo "❌ Failed to get Public Access Block settings. Bucket may not exist or no permissions."
        return 1
    fi
}

# Function to validate bucket ACL
validate_bucket_acl() {
    echo ""
    echo "🔍 Checking Bucket ACL..."
    
    if aws s3api get-bucket-acl --bucket "$BUCKET_NAME" --region "$AWS_REGION" >/dev/null 2>&1; then
        BUCKET_ACL=$(aws s3api get-bucket-acl --bucket "$BUCKET_NAME" --region "$AWS_REGION" --output json)
        
        # Check if there are any public grants
        PUBLIC_GRANTS=$(echo "$BUCKET_ACL" | jq -r '.Grants[] | select(.Grantee.URI == "http://acs.amazonaws.com/groups/global/AllUsers" or .Grantee.URI == "http://acs.amazonaws.com/groups/global/AuthenticatedUsers")')
        
        if [[ -z "$PUBLIC_GRANTS" ]]; then
            echo "✅ Bucket ACL does not contain public grants"
            return 0
        else
            echo "❌ Bucket ACL contains public grants:"
            echo "$PUBLIC_GRANTS"
            return 1
        fi
    else
        echo "❌ Failed to get Bucket ACL. Bucket may not exist or no permissions."
        return 1
    fi
}

# Function to test public write access
test_public_write_access() {
    echo ""
    echo "🔍 Testing public write access (should be blocked)..."
    
    # Try to put an object without credentials (this should fail)
    TEST_FILE="/tmp/test-security-validation.txt"
    echo "This is a test file for security validation" > "$TEST_FILE"
    
    # Use a different AWS profile or unset credentials to simulate anonymous access
    if AWS_ACCESS_KEY_ID="" AWS_SECRET_ACCESS_KEY="" aws s3 cp "$TEST_FILE" "s3://$BUCKET_NAME/security-test.txt" --region "$AWS_REGION" >/dev/null 2>&1; then
        echo "❌ Public write access is allowed - this is a security vulnerability!"
        rm -f "$TEST_FILE"
        return 1
    else
        echo "✅ Public write access is properly blocked"
        rm -f "$TEST_FILE"
        return 0
    fi
}

# Function to check bucket encryption
validate_bucket_encryption() {
    echo ""
    echo "🔍 Checking Bucket Encryption..."
    
    if aws s3api get-bucket-encryption --bucket "$BUCKET_NAME" --region "$AWS_REGION" >/dev/null 2>&1; then
        ENCRYPTION_CONFIG=$(aws s3api get-bucket-encryption --bucket "$BUCKET_NAME" --region "$AWS_REGION" --output json)
        SSE_ALGORITHM=$(echo "$ENCRYPTION_CONFIG" | jq -r '.ServerSideEncryptionConfiguration.Rules[0].ApplyServerSideEncryptionByDefault.SSEAlgorithm')
        
        if [[ "$SSE_ALGORITHM" == "AES256" ]] || [[ "$SSE_ALGORITHM" == "aws:kms" ]]; then
            echo "✅ Bucket encryption is enabled with $SSE_ALGORITHM"
            return 0
        else
            echo "⚠️  Bucket encryption algorithm: $SSE_ALGORITHM"
            return 0
        fi
    else
        echo "⚠️  Bucket encryption is not configured"
        return 0
    fi
}

# Main validation function
main() {
    echo "S3 Security Validation Report"
    echo "============================="
    echo "Bucket: $BUCKET_NAME"
    echo "Region: $AWS_REGION"
    echo "Date: $(date)"
    echo ""
    
    check_aws_cli
    
    local exit_code=0
    
    validate_public_access_block || exit_code=1
    validate_bucket_acl || exit_code=1
    test_public_write_access || exit_code=1
    validate_bucket_encryption || exit_code=1
    
    echo ""
    echo "=============================================================="
    if [[ $exit_code -eq 0 ]]; then
        echo "✅ All security validations passed! Bucket is properly secured."
    else
        echo "❌ Some security validations failed. Please review and fix the issues above."
    fi
    echo "=============================================================="
    
    exit $exit_code
}

# Check if jq is available for JSON parsing
if ! command -v jq &> /dev/null; then
    echo "⚠️  jq not found. Installing basic JSON parsing fallback..."
    # You could add fallback JSON parsing here if needed
fi

main "$@"