#!/bin/bash

# S3 Security Validation Script
# This script validates that S3 buckets are properly configured to block public write access
# Addresses S3.3 security misconfiguration

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Default values
BUCKET_NAME=""
REGION="us-east-1"

# Function to print colored output
print_status() {
    local status=$1
    local message=$2
    case $status in
        "SUCCESS")
            echo -e "${GREEN}✅ SUCCESS:${NC} $message"
            ;;
        "ERROR")
            echo -e "${RED}❌ ERROR:${NC} $message"
            ;;
        "WARNING")
            echo -e "${YELLOW}⚠️  WARNING:${NC} $message"
            ;;
        "INFO")
            echo -e "${YELLOW}ℹ️  INFO:${NC} $message"
            ;;
    esac
}

# Function to show usage
usage() {
    echo "Usage: $0 -b <bucket-name> [-r <region>]"
    echo "  -b: S3 bucket name to validate (required)"
    echo "  -r: AWS region (default: us-east-1)"
    echo "  -h: Show this help message"
    echo
    echo "Example: $0 -b my-dropbox-bucket -r us-west-2"
    exit 1
}

# Parse command line arguments
while getopts "b:r:h" opt; do
    case $opt in
        b)
            BUCKET_NAME="$OPTARG"
            ;;
        r)
            REGION="$OPTARG"
            ;;
        h)
            usage
            ;;
        \?)
            echo "Invalid option: -$OPTARG" >&2
            usage
            ;;
    esac
done

# Check if bucket name is provided
if [[ -z "$BUCKET_NAME" ]]; then
    print_status "ERROR" "Bucket name is required"
    usage
fi

# Check if AWS CLI is installed
if ! command -v aws &> /dev/null; then
    print_status "ERROR" "AWS CLI is not installed or not in PATH"
    exit 1
fi

# Check AWS credentials
if ! aws sts get-caller-identity &> /dev/null; then
    print_status "ERROR" "AWS credentials not configured or invalid"
    exit 1
fi

print_status "INFO" "Starting S3 security validation for bucket: $BUCKET_NAME"
echo "=============================================================================="

# Validate bucket exists
print_status "INFO" "Checking if bucket exists..."
if ! aws s3api head-bucket --bucket "$BUCKET_NAME" --region "$REGION" 2>/dev/null; then
    print_status "ERROR" "Bucket '$BUCKET_NAME' does not exist or is not accessible"
    exit 1
fi
print_status "SUCCESS" "Bucket exists and is accessible"

# Check Public Access Block settings
print_status "INFO" "Checking Public Access Block configuration..."
PAB_OUTPUT=$(aws s3api get-public-access-block --bucket "$BUCKET_NAME" --region "$REGION" 2>/dev/null || echo "")

if [[ -z "$PAB_OUTPUT" ]]; then
    print_status "ERROR" "No Public Access Block configuration found"
    exit 1
fi

# Parse Public Access Block settings
BLOCK_PUBLIC_ACLS=$(echo "$PAB_OUTPUT" | jq -r '.PublicAccessBlockConfiguration.BlockPublicAcls // false')
IGNORE_PUBLIC_ACLS=$(echo "$PAB_OUTPUT" | jq -r '.PublicAccessBlockConfiguration.IgnorePublicAcls // false')
BLOCK_PUBLIC_POLICY=$(echo "$PAB_OUTPUT" | jq -r '.PublicAccessBlockConfiguration.BlockPublicPolicy // false')
RESTRICT_PUBLIC_BUCKETS=$(echo "$PAB_OUTPUT" | jq -r '.PublicAccessBlockConfiguration.RestrictPublicBuckets // false')

# Validate each setting
VALIDATION_PASSED=true

if [[ "$BLOCK_PUBLIC_ACLS" == "true" ]]; then
    print_status "SUCCESS" "BlockPublicAcls: true"
else
    print_status "ERROR" "BlockPublicAcls: false (should be true)"
    VALIDATION_PASSED=false
fi

if [[ "$IGNORE_PUBLIC_ACLS" == "true" ]]; then
    print_status "SUCCESS" "IgnorePublicAcls: true"
else
    print_status "ERROR" "IgnorePublicAcls: false (should be true)"
    VALIDATION_PASSED=false
fi

if [[ "$BLOCK_PUBLIC_POLICY" == "true" ]]; then
    print_status "SUCCESS" "BlockPublicPolicy: true"
else
    print_status "ERROR" "BlockPublicPolicy: false (should be true)"
    VALIDATION_PASSED=false
fi

if [[ "$RESTRICT_PUBLIC_BUCKETS" == "true" ]]; then
    print_status "SUCCESS" "RestrictPublicBuckets: true"
else
    print_status "ERROR" "RestrictPublicBuckets: false (should be true)"
    VALIDATION_PASSED=false
fi

# Check bucket ACL
print_status "INFO" "Checking bucket ACL for public write permissions..."
ACL_OUTPUT=$(aws s3api get-bucket-acl --bucket "$BUCKET_NAME" --region "$REGION" 2>/dev/null)
PUBLIC_WRITE_FOUND=false

if echo "$ACL_OUTPUT" | jq -r '.Grants[]' | grep -q "AllUsers\|AuthenticatedUsers"; then
    PUBLIC_WRITE_GRANTS=$(echo "$ACL_OUTPUT" | jq -r '.Grants[] | select(.Grantee.Type == "Group" and (.Grantee.URI | contains("AllUsers") or contains("AuthenticatedUsers"))) | select(.Permission == "WRITE" or .Permission == "FULL_CONTROL")')
    if [[ -n "$PUBLIC_WRITE_GRANTS" ]]; then
        print_status "ERROR" "Found public write permissions in ACL"
        PUBLIC_WRITE_FOUND=true
        VALIDATION_PASSED=false
    else
        print_status "SUCCESS" "No public write permissions found in ACL"
    fi
else
    print_status "SUCCESS" "No public permissions found in ACL"
fi

# Check bucket policy for public write access
print_status "INFO" "Checking bucket policy for public write permissions..."
POLICY_OUTPUT=$(aws s3api get-bucket-policy --bucket "$BUCKET_NAME" --region "$REGION" 2>/dev/null || echo "")

if [[ -n "$POLICY_OUTPUT" ]]; then
    # Check for dangerous policy statements
    POLICY_CONTENT=$(echo "$POLICY_OUTPUT" | jq -r '.Policy')
    if echo "$POLICY_CONTENT" | jq -r '.Statement[]' | grep -q "Allow.*\*.*s3:Put\|Allow.*\*.*s3:Delete\|Allow.*\*.*s3:\*"; then
        print_status "WARNING" "Bucket policy may contain public permissions - manual review recommended"
    else
        print_status "SUCCESS" "Bucket policy appears secure"
    fi
else
    print_status "SUCCESS" "No bucket policy found (recommended for security)"
fi

# Test public write access
print_status "INFO" "Testing public write access (should fail)..."
TEST_FILE="/tmp/s3-security-test-$(date +%s).txt"
echo "This is a test file for S3 security validation" > "$TEST_FILE"

if aws s3 cp "$TEST_FILE" "s3://$BUCKET_NAME/security-test.txt" --no-sign-request --region "$REGION" >/dev/null 2>&1; then
    print_status "ERROR" "Public write access is allowed - this is a CRITICAL security issue!"
    VALIDATION_PASSED=false
    # Try to clean up the test file
    aws s3 rm "s3://$BUCKET_NAME/security-test.txt" --region "$REGION" >/dev/null 2>&1 || true
else
    print_status "SUCCESS" "Public write access properly blocked"
fi

# Clean up test file
rm -f "$TEST_FILE"

# Check encryption settings
print_status "INFO" "Checking server-side encryption configuration..."
ENCRYPTION_OUTPUT=$(aws s3api get-bucket-encryption --bucket "$BUCKET_NAME" --region "$REGION" 2>/dev/null || echo "")
if [[ -n "$ENCRYPTION_OUTPUT" ]]; then
    print_status "SUCCESS" "Server-side encryption is configured"
else
    print_status "WARNING" "Server-side encryption not configured (recommended for security)"
fi

# Check versioning
print_status "INFO" "Checking versioning configuration..."
VERSIONING_OUTPUT=$(aws s3api get-bucket-versioning --bucket "$BUCKET_NAME" --region "$REGION" 2>/dev/null)
VERSIONING_STATUS=$(echo "$VERSIONING_OUTPUT" | jq -r '.Status // "not configured"')
if [[ "$VERSIONING_STATUS" == "Enabled" ]]; then
    print_status "SUCCESS" "Versioning is enabled"
else
    print_status "WARNING" "Versioning is not enabled (recommended for data protection)"
fi

# Final validation result
echo
echo "=============================================================================="
if [[ "$VALIDATION_PASSED" == "true" ]]; then
    print_status "SUCCESS" "S3.3 Security validation PASSED - All critical security settings are properly configured"
    echo -e "${GREEN}✅ COMPLIANT:${NC} Bucket is protected against public write access"
    exit 0
else
    print_status "ERROR" "S3.3 Security validation FAILED - Critical security issues found"
    echo -e "${RED}❌ NON-COMPLIANT:${NC} Bucket has security vulnerabilities that must be addressed"
    echo
    echo "To fix these issues:"
    echo "1. Apply the provided Terraform or CloudFormation templates"
    echo "2. Manually configure Public Access Block settings in AWS Console"
    echo "3. Review and update bucket policies and ACLs"
    exit 1
fi