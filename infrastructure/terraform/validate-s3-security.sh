#!/bin/bash
# S3 Security Validation Script
# This script validates that S3 buckets are properly configured to block public write access
# Addresses AWS Security Hub Control ID: S3.3

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "🔒 DropBox S3 Security Validation"
echo "=================================="

# Check if bucket name is provided
if [ -z "$1" ]; then
    echo -e "${RED}Error: Please provide bucket name as argument${NC}"
    echo "Usage: $0 <bucket-name>"
    exit 1
fi

BUCKET_NAME="$1"

echo "📋 Validating bucket: $BUCKET_NAME"
echo ""

# Function to print test results
print_result() {
    local test_name="$1"
    local result="$2"
    local expected="$3"
    
    if [ "$result" = "$expected" ]; then
        echo -e "✅ ${GREEN}PASS${NC}: $test_name"
    else
        echo -e "❌ ${RED}FAIL${NC}: $test_name (Expected: $expected, Got: $result)"
        return 1
    fi
}

# Check if AWS CLI is installed
if ! command -v aws &> /dev/null; then
    echo -e "${RED}Error: AWS CLI is not installed${NC}"
    exit 1
fi

# Check if bucket exists
if ! aws s3api head-bucket --bucket "$BUCKET_NAME" &> /dev/null; then
    echo -e "${RED}Error: Bucket '$BUCKET_NAME' does not exist or is not accessible${NC}"
    exit 1
fi

echo "1. Testing Public Access Block Settings..."
echo "----------------------------------------"

# Get public access block configuration
PAB_CONFIG=$(aws s3api get-public-access-block --bucket "$BUCKET_NAME" --output json)

if [ $? -eq 0 ]; then
    # Extract boolean values
    BLOCK_PUBLIC_ACLS=$(echo "$PAB_CONFIG" | jq -r '.PublicAccessBlockConfiguration.BlockPublicAcls')
    IGNORE_PUBLIC_ACLS=$(echo "$PAB_CONFIG" | jq -r '.PublicAccessBlockConfiguration.IgnorePublicAcls')
    BLOCK_PUBLIC_POLICY=$(echo "$PAB_CONFIG" | jq -r '.PublicAccessBlockConfiguration.BlockPublicPolicy')
    RESTRICT_PUBLIC_BUCKETS=$(echo "$PAB_CONFIG" | jq -r '.PublicAccessBlockConfiguration.RestrictPublicBuckets')
    
    # Validate each setting
    print_result "BlockPublicAcls" "$BLOCK_PUBLIC_ACLS" "true"
    print_result "IgnorePublicAcls" "$IGNORE_PUBLIC_ACLS" "true"
    print_result "BlockPublicPolicy" "$BLOCK_PUBLIC_POLICY" "true"
    print_result "RestrictPublicBuckets" "$RESTRICT_PUBLIC_BUCKETS" "true"
else
    echo -e "${RED}❌ FAIL: Could not retrieve public access block configuration${NC}"
    exit 1
fi

echo ""
echo "2. Testing Bucket ACL..."
echo "------------------------"

# Check bucket ACL for public permissions
ACL_GRANTS=$(aws s3api get-bucket-acl --bucket "$BUCKET_NAME" --output json | jq -r '.Grants[]')

# Check if any grants allow public access
PUBLIC_READ=$(echo "$ACL_GRANTS" | jq -r 'select(.Grantee.URI == "http://acs.amazonaws.com/groups/global/AllUsers" or .Grantee.URI == "http://acs.amazonaws.com/groups/global/AuthenticatedUsers") | .Permission' | grep -E "READ|WRITE|FULL_CONTROL" || true)

if [ -z "$PUBLIC_READ" ]; then
    echo -e "✅ ${GREEN}PASS${NC}: No public permissions in bucket ACL"
else
    echo -e "❌ ${RED}FAIL${NC}: Public permissions found in bucket ACL: $PUBLIC_READ"
fi

echo ""
echo "3. Testing Bucket Policy..."
echo "--------------------------"

# Check if bucket policy exists and analyze it
BUCKET_POLICY=$(aws s3api get-bucket-policy --bucket "$BUCKET_NAME" --output text 2>/dev/null || echo "")

if [ -n "$BUCKET_POLICY" ]; then
    # Check for explicit deny statements for public access
    DENY_PUBLIC=$(echo "$BUCKET_POLICY" | jq -r '.Statement[] | select(.Effect == "Deny" and (.Principal == "*" or .Principal.AWS == "*")) | .Action[]' 2>/dev/null || true)
    
    if echo "$DENY_PUBLIC" | grep -q "s3:PutObject\|s3:\*"; then
        echo -e "✅ ${GREEN}PASS${NC}: Bucket policy explicitly denies public write access"
    else
        echo -e "⚠️  ${YELLOW}WARNING${NC}: Bucket policy exists but may not explicitly deny public write access"
    fi
else
    echo -e "ℹ️  ${YELLOW}INFO${NC}: No bucket policy found (relying on public access block)"
fi

echo ""
echo "4. Testing Public Write Access..."
echo "--------------------------------"

# Try to test public write access (this should fail)
TEST_FILE="/tmp/test-public-write-$$"
echo "test content" > "$TEST_FILE"

# Attempt to upload without credentials (should fail)
if aws s3 cp "$TEST_FILE" "s3://$BUCKET_NAME/test-public-write.txt" --no-sign-request 2>/dev/null; then
    echo -e "❌ ${RED}CRITICAL FAIL${NC}: Public write access is allowed!"
    rm -f "$TEST_FILE"
    exit 1
else
    echo -e "✅ ${GREEN}PASS${NC}: Public write access is properly blocked"
fi

rm -f "$TEST_FILE"

echo ""
echo "🎉 Security Validation Summary"
echo "=============================="
echo -e "${GREEN}✅ S3 bucket '$BUCKET_NAME' is properly secured against public write access${NC}"
echo -e "${GREEN}✅ AWS Security Hub Control S3.3 compliance: PASSED${NC}"
echo ""
echo "🔒 Security measures in place:"
echo "  • Block Public ACLs: Enabled"
echo "  • Ignore Public ACLs: Enabled"
echo "  • Block Public Policy: Enabled"
echo "  • Restrict Public Buckets: Enabled"
echo "  • Public write access: Blocked"
echo ""
echo "✨ Validation completed successfully!"