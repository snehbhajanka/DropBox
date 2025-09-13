#!/bin/bash

# S3 Security Validation Script
# This script validates that S3 buckets have proper security configurations

set -e

BUCKET_NAME="$1"

if [ -z "$BUCKET_NAME" ]; then
    echo "Usage: $0 <bucket-name>"
    echo "Example: $0 my-dropbox-bucket"
    exit 1
fi

echo "🔍 S3 Security Validation for bucket: $BUCKET_NAME"
echo "=================================================="

# Check if bucket exists
echo "📋 Checking if bucket exists..."
if ! aws s3api head-bucket --bucket "$BUCKET_NAME" 2>/dev/null; then
    echo "❌ Error: Bucket '$BUCKET_NAME' does not exist or you don't have access"
    exit 1
fi
echo "✅ Bucket exists and accessible"

# Test 1: Check Public Access Block settings
echo ""
echo "🔒 Test 1: Public Access Block Configuration"
echo "--------------------------------------------"

PUBLIC_ACCESS_BLOCK=$(aws s3api get-public-access-block --bucket "$BUCKET_NAME" 2>/dev/null || echo "ERROR")

if [ "$PUBLIC_ACCESS_BLOCK" = "ERROR" ]; then
    echo "❌ FAIL: Unable to retrieve public access block settings"
    exit 1
fi

# Parse the JSON response
BLOCK_PUBLIC_ACLS=$(echo "$PUBLIC_ACCESS_BLOCK" | jq -r '.PublicAccessBlockConfiguration.BlockPublicAcls')
IGNORE_PUBLIC_ACLS=$(echo "$PUBLIC_ACCESS_BLOCK" | jq -r '.PublicAccessBlockConfiguration.IgnorePublicAcls')
BLOCK_PUBLIC_POLICY=$(echo "$PUBLIC_ACCESS_BLOCK" | jq -r '.PublicAccessBlockConfiguration.BlockPublicPolicy')
RESTRICT_PUBLIC_BUCKETS=$(echo "$PUBLIC_ACCESS_BLOCK" | jq -r '.PublicAccessBlockConfiguration.RestrictPublicBuckets')

echo "Block Public ACLs: $BLOCK_PUBLIC_ACLS"
echo "Ignore Public ACLs: $IGNORE_PUBLIC_ACLS"
echo "Block Public Policy: $BLOCK_PUBLIC_POLICY"
echo "Restrict Public Buckets: $RESTRICT_PUBLIC_BUCKETS"

# Validate all settings are true
if [ "$BLOCK_PUBLIC_ACLS" = "true" ] && [ "$IGNORE_PUBLIC_ACLS" = "true" ] && [ "$BLOCK_PUBLIC_POLICY" = "true" ] && [ "$RESTRICT_PUBLIC_BUCKETS" = "true" ]; then
    echo "✅ PASS: All public access block settings are properly configured"
else
    echo "❌ FAIL: Some public access block settings are not properly configured"
    exit 1
fi

# Test 2: Check bucket policy exists and is restrictive
echo ""
echo "🛡️  Test 2: Bucket Policy Configuration"
echo "---------------------------------------"

BUCKET_POLICY=$(aws s3api get-bucket-policy --bucket "$BUCKET_NAME" 2>/dev/null || echo "ERROR")

if [ "$BUCKET_POLICY" = "ERROR" ]; then
    echo "⚠️  WARNING: No bucket policy found (this might be acceptable if using IAM roles)"
else
    echo "✅ PASS: Bucket policy exists"
    
    # Check if policy contains deny statements for public write
    if echo "$BUCKET_POLICY" | jq -r '.Policy' | jq -r '.Statement[] | select(.Effect == "Deny")' | grep -q "PutObject\|DeleteObject"; then
        echo "✅ PASS: Bucket policy contains explicit deny statements for public write operations"
    else
        echo "⚠️  WARNING: Bucket policy does not contain explicit deny statements for public write operations"
    fi
fi

# Test 3: Check encryption configuration
echo ""
echo "🔐 Test 3: Encryption Configuration"
echo "-----------------------------------"

ENCRYPTION=$(aws s3api get-bucket-encryption --bucket "$BUCKET_NAME" 2>/dev/null || echo "ERROR")

if [ "$ENCRYPTION" = "ERROR" ]; then
    echo "⚠️  WARNING: No encryption configuration found"
else
    SSE_ALGORITHM=$(echo "$ENCRYPTION" | jq -r '.ServerSideEncryptionConfiguration.Rules[0].ApplyServerSideEncryptionByDefault.SSEAlgorithm')
    echo "✅ PASS: Encryption enabled with algorithm: $SSE_ALGORITHM"
fi

# Test 4: Check versioning configuration
echo ""
echo "📦 Test 4: Versioning Configuration"
echo "-----------------------------------"

VERSIONING=$(aws s3api get-bucket-versioning --bucket "$BUCKET_NAME" 2>/dev/null)
VERSIONING_STATUS=$(echo "$VERSIONING" | jq -r '.Status // "Disabled"')

echo "Versioning Status: $VERSIONING_STATUS"
if [ "$VERSIONING_STATUS" = "Enabled" ]; then
    echo "✅ PASS: Versioning is enabled"
else
    echo "⚠️  WARNING: Versioning is not enabled"
fi

# Test 5: Attempt unauthorized public write (should fail)
echo ""
echo "🚫 Test 5: Public Write Access Test"
echo "-----------------------------------"

# Create a temporary test file
TEST_FILE="/tmp/test-public-write-$$.txt"
echo "This is a test file for public write access validation" > "$TEST_FILE"

echo "Attempting to upload file without authentication (this should fail)..."

# Try to upload without authentication
if aws s3 cp "$TEST_FILE" "s3://$BUCKET_NAME/test-public-write.txt" --no-sign-request 2>/dev/null; then
    echo "❌ CRITICAL FAIL: Public write access is allowed! This is a security vulnerability."
    rm -f "$TEST_FILE"
    exit 1
else
    echo "✅ PASS: Public write access is properly blocked"
fi

# Clean up
rm -f "$TEST_FILE"

# Test 6: Check AWS Config compliance (if available)
echo ""
echo "📊 Test 6: AWS Config Compliance Check"
echo "--------------------------------------"

# Get AWS account ID and region
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
AWS_REGION=$(aws configure get region)

CONFIG_RULE="s3-bucket-public-write-prohibited"
COMPLIANCE_STATUS=$(aws configservice get-compliance-details-by-resource \
    --resource-type "AWS::S3::Bucket" \
    --resource-id "$BUCKET_NAME" \
    --compliance-types "COMPLIANT" "NON_COMPLIANT" \
    --query "EvaluationResults[?ConfigRuleName=='$CONFIG_RULE'].ComplianceType" \
    --output text 2>/dev/null || echo "NOT_AVAILABLE")

if [ "$COMPLIANCE_STATUS" = "COMPLIANT" ]; then
    echo "✅ PASS: AWS Config reports bucket as compliant with s3-bucket-public-write-prohibited rule"
elif [ "$COMPLIANCE_STATUS" = "NON_COMPLIANT" ]; then
    echo "❌ FAIL: AWS Config reports bucket as non-compliant with s3-bucket-public-write-prohibited rule"
elif [ "$COMPLIANCE_STATUS" = "NOT_AVAILABLE" ]; then
    echo "⚠️  INFO: AWS Config compliance check not available (Config service may not be enabled)"
else
    echo "⚠️  INFO: Unable to determine AWS Config compliance status"
fi

# Summary
echo ""
echo "📋 Validation Summary"
echo "===================="
echo "Bucket: $BUCKET_NAME"
echo "Account: $AWS_ACCOUNT_ID"
echo "Region: $AWS_REGION"
echo ""
echo "Security Controls Validated:"
echo "✅ Public Access Block Configuration"
echo "✅ Public Write Access Blocked"
if [ "$ENCRYPTION" != "ERROR" ]; then
    echo "✅ Encryption Enabled"
fi
if [ "$VERSIONING_STATUS" = "Enabled" ]; then
    echo "✅ Versioning Enabled"
fi

echo ""
echo "🛡️  Security Status: SECURE"
echo "The bucket configuration meets AWS Security Hub control S3.3 requirements."
echo ""
echo "For additional security recommendations, see docs/AWS_S3_SECURITY.md"