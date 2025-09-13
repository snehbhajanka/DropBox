#!/bin/bash

# S3 Security Validation Script
# This script validates that S3 buckets have proper security configurations

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    if [ "$2" = "SUCCESS" ]; then
        echo -e "${GREEN}✓ $1${NC}"
    elif [ "$2" = "FAIL" ]; then
        echo -e "${RED}✗ $1${NC}"
        exit 1
    else
        echo -e "${YELLOW}! $1${NC}"
    fi
}

# Check if bucket name is provided
if [ -z "$1" ]; then
    print_status "Usage: $0 <bucket-name>" "FAIL"
fi

BUCKET_NAME=$1

echo "Validating S3 bucket security for: $BUCKET_NAME"
echo "================================================"

# Check if AWS CLI is installed
if ! command -v aws &> /dev/null; then
    print_status "AWS CLI is not installed" "FAIL"
fi

# Check if bucket exists
if ! aws s3api head-bucket --bucket "$BUCKET_NAME" 2>/dev/null; then
    print_status "Bucket $BUCKET_NAME does not exist or is not accessible" "FAIL"
fi

print_status "Bucket exists and is accessible" "SUCCESS"

# Check public access block configuration
echo ""
echo "Checking Public Access Block Configuration..."
echo "--------------------------------------------"

PUBLIC_ACCESS_BLOCK=$(aws s3api get-public-access-block --bucket "$BUCKET_NAME" 2>/dev/null)

if [ $? -ne 0 ]; then
    print_status "No public access block configuration found" "FAIL"
fi

# Parse the JSON response
BLOCK_PUBLIC_ACLS=$(echo "$PUBLIC_ACCESS_BLOCK" | jq -r '.PublicAccessBlockConfiguration.BlockPublicAcls')
IGNORE_PUBLIC_ACLS=$(echo "$PUBLIC_ACCESS_BLOCK" | jq -r '.PublicAccessBlockConfiguration.IgnorePublicAcls')
BLOCK_PUBLIC_POLICY=$(echo "$PUBLIC_ACCESS_BLOCK" | jq -r '.PublicAccessBlockConfiguration.BlockPublicPolicy')
RESTRICT_PUBLIC_BUCKETS=$(echo "$PUBLIC_ACCESS_BLOCK" | jq -r '.PublicAccessBlockConfiguration.RestrictPublicBuckets')

# Validate each setting
if [ "$BLOCK_PUBLIC_ACLS" = "true" ]; then
    print_status "BlockPublicAcls: true" "SUCCESS"
else
    print_status "BlockPublicAcls: $BLOCK_PUBLIC_ACLS (should be true)" "FAIL"
fi

if [ "$IGNORE_PUBLIC_ACLS" = "true" ]; then
    print_status "IgnorePublicAcls: true" "SUCCESS"
else
    print_status "IgnorePublicAcls: $IGNORE_PUBLIC_ACLS (should be true)" "FAIL"
fi

if [ "$BLOCK_PUBLIC_POLICY" = "true" ]; then
    print_status "BlockPublicPolicy: true" "SUCCESS"
else
    print_status "BlockPublicPolicy: $BLOCK_PUBLIC_POLICY (should be true)" "FAIL"
fi

if [ "$RESTRICT_PUBLIC_BUCKETS" = "true" ]; then
    print_status "RestrictPublicBuckets: true" "SUCCESS"
else
    print_status "RestrictPublicBuckets: $RESTRICT_PUBLIC_BUCKETS (should be true)" "FAIL"
fi

# Check bucket ACL
echo ""
echo "Checking Bucket ACL..."
echo "----------------------"

BUCKET_ACL=$(aws s3api get-bucket-acl --bucket "$BUCKET_NAME" 2>/dev/null)

if [ $? -ne 0 ]; then
    print_status "Could not retrieve bucket ACL" "FAIL"
fi

# Check for public read/write permissions
PUBLIC_READ=$(echo "$BUCKET_ACL" | jq -r '.Grants[] | select(.Grantee.URI=="http://acs.amazonaws.com/groups/global/AllUsers") | .Permission')
PUBLIC_READ_ACP=$(echo "$BUCKET_ACL" | jq -r '.Grants[] | select(.Grantee.URI=="http://acs.amazonaws.com/groups/global/AuthenticatedUsers") | .Permission')

if [ -z "$PUBLIC_READ" ] && [ -z "$PUBLIC_READ_ACP" ]; then
    print_status "No public ACL permissions found" "SUCCESS"
else
    print_status "Public ACL permissions detected: $PUBLIC_READ $PUBLIC_READ_ACP" "FAIL"
fi

# Check bucket encryption
echo ""
echo "Checking Bucket Encryption..."
echo "-----------------------------"

ENCRYPTION=$(aws s3api get-bucket-encryption --bucket "$BUCKET_NAME" 2>/dev/null)

if [ $? -eq 0 ]; then
    print_status "Server-side encryption is enabled" "SUCCESS"
else
    print_status "Server-side encryption is not configured" "WARN"
fi

# Check versioning
echo ""
echo "Checking Bucket Versioning..."
echo "-----------------------------"

VERSIONING=$(aws s3api get-bucket-versioning --bucket "$BUCKET_NAME" 2>/dev/null)
VERSION_STATUS=$(echo "$VERSIONING" | jq -r '.Status')

if [ "$VERSION_STATUS" = "Enabled" ]; then
    print_status "Versioning is enabled" "SUCCESS"
else
    print_status "Versioning is $VERSION_STATUS" "WARN"
fi

echo ""
echo "================================================"
print_status "S3 bucket security validation completed successfully!" "SUCCESS"
echo ""
echo "Summary:"
echo "- All public access is blocked"
echo "- No public ACL permissions"
echo "- Bucket is secure from unauthorized access"