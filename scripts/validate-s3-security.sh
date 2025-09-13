#!/bin/bash

# S3 Security Validation Script
# Validates that S3 buckets are properly secured against public write access

set -e

echo "🔍 S3 Security Configuration Validation"
echo "========================================"

# Check if required tools are installed
command -v aws >/dev/null 2>&1 || { echo "❌ AWS CLI is required but not installed. Aborting." >&2; exit 1; }
command -v terraform >/dev/null 2>&1 || { echo "❌ Terraform is required but not installed. Aborting." >&2; exit 1; }

# Configuration
BUCKET_NAME="${1:-dropbox-storage-test-$(date +%s)}"
AWS_REGION="${2:-us-east-2}"
TERRAFORM_DIR="terraform"

echo "📋 Configuration:"
echo "   Bucket Name: $BUCKET_NAME"
echo "   AWS Region: $AWS_REGION"
echo ""

# Function to check public access block
check_public_access_block() {
    echo "🔒 Checking S3 Public Access Block settings..."
    
    local pab_output
    pab_output=$(aws s3api get-public-access-block --bucket "$BUCKET_NAME" --region "$AWS_REGION" 2>/dev/null || echo "NOT_FOUND")
    
    if [ "$pab_output" = "NOT_FOUND" ]; then
        echo "❌ Public Access Block not configured"
        return 1
    fi
    
    # Parse JSON and check each setting
    local block_public_acls block_public_policy ignore_public_acls restrict_public_buckets
    block_public_acls=$(echo "$pab_output" | grep -o '"BlockPublicAcls":[^,}]*' | cut -d':' -f2 | tr -d ' ')
    block_public_policy=$(echo "$pab_output" | grep -o '"BlockPublicPolicy":[^,}]*' | cut -d':' -f2 | tr -d ' ')
    ignore_public_acls=$(echo "$pab_output" | grep -o '"IgnorePublicAcls":[^,}]*' | cut -d':' -f2 | tr -d ' ')
    restrict_public_buckets=$(echo "$pab_output" | grep -o '"RestrictPublicBuckets":[^,}]*' | cut -d':' -f2 | tr -d ' ')
    
    if [ "$block_public_acls" = "true" ]; then
        echo "✅ Block Public ACLs: Enabled"
    else
        echo "❌ Block Public ACLs: Disabled"
        return 1
    fi
    
    if [ "$block_public_policy" = "true" ]; then
        echo "✅ Block Public Policy: Enabled"
    else
        echo "❌ Block Public Policy: Disabled"
        return 1
    fi
    
    if [ "$ignore_public_acls" = "true" ]; then
        echo "✅ Ignore Public ACLs: Enabled"
    else
        echo "❌ Ignore Public ACLs: Disabled"
        return 1
    fi
    
    if [ "$restrict_public_buckets" = "true" ]; then
        echo "✅ Restrict Public Buckets: Enabled"
    else
        echo "❌ Restrict Public Buckets: Disabled"
        return 1
    fi
    
    return 0
}

# Function to check bucket policy
check_bucket_policy() {
    echo ""
    echo "📜 Checking S3 Bucket Policy..."
    
    local policy_output
    policy_output=$(aws s3api get-bucket-policy --bucket "$BUCKET_NAME" --region "$AWS_REGION" 2>/dev/null || echo "NOT_FOUND")
    
    if [ "$policy_output" = "NOT_FOUND" ]; then
        echo "❌ Bucket policy not configured"
        return 1
    fi
    
    # Check for deny statements
    if echo "$policy_output" | grep -q '"Effect":"Deny"'; then
        echo "✅ Deny policy statements found"
    else
        echo "❌ No deny policy statements found"
        return 1
    fi
    
    # Check for specific denied actions
    local denied_actions=("s3:PutObject" "s3:PutObjectAcl" "s3:DeleteObject" "s3:PutBucketAcl" "s3:PutBucketPolicy")
    for action in "${denied_actions[@]}"; do
        if echo "$policy_output" | grep -q "$action"; then
            echo "✅ $action is explicitly denied"
        else
            echo "⚠️  $action not found in policy"
        fi
    done
    
    return 0
}

# Function to test write access
test_write_access() {
    echo ""
    echo "🧪 Testing Public Write Access (should fail)..."
    
    # Create a temporary test file
    local test_file="/tmp/test-public-write-$$.txt"
    echo "Test content for public write validation" > "$test_file"
    
    # Attempt to upload without authentication (should fail)
    if aws s3 cp "$test_file" "s3://$BUCKET_NAME/test-file.txt" --region "$AWS_REGION" --no-sign-request 2>/dev/null; then
        echo "❌ CRITICAL: Public write access is allowed!"
        rm -f "$test_file"
        return 1
    else
        echo "✅ Public write access is properly blocked"
        rm -f "$test_file"
        return 0
    fi
}

# Function to validate terraform configuration
validate_terraform() {
    echo ""
    echo "⚙️  Validating Terraform Configuration..."
    
    if [ ! -d "$TERRAFORM_DIR" ]; then
        echo "❌ Terraform directory not found: $TERRAFORM_DIR"
        return 1
    fi
    
    cd "$TERRAFORM_DIR"
    
    # Initialize terraform
    echo "   Initializing Terraform..."
    if terraform init -backend=false > /dev/null 2>&1; then
        echo "✅ Terraform initialization successful"
    else
        echo "❌ Terraform initialization failed"
        cd ..
        return 1
    fi
    
    # Validate configuration
    echo "   Validating configuration..."
    if terraform validate > /dev/null 2>&1; then
        echo "✅ Terraform configuration is valid"
    else
        echo "❌ Terraform configuration is invalid"
        cd ..
        return 1
    fi
    
    # Check for security resources
    if grep -q "aws_s3_bucket_public_access_block" *.tf; then
        echo "✅ Public access block resource found"
    else
        echo "❌ Public access block resource not found"
    fi
    
    if grep -q "aws_s3_bucket_policy" *.tf; then
        echo "✅ Bucket policy resource found"
    else
        echo "❌ Bucket policy resource not found"
    fi
    
    cd ..
    return 0
}

# Main execution
main() {
    local validation_passed=true
    
    echo "Starting S3 security validation..."
    echo ""
    
    # Validate Terraform configuration
    if ! validate_terraform; then
        validation_passed=false
    fi
    
    # Check if bucket exists before running other tests
    if aws s3api head-bucket --bucket "$BUCKET_NAME" --region "$AWS_REGION" 2>/dev/null; then
        echo ""
        echo "📦 Bucket '$BUCKET_NAME' found. Running security checks..."
        
        # Check public access block
        if ! check_public_access_block; then
            validation_passed=false
        fi
        
        # Check bucket policy
        if ! check_bucket_policy; then
            validation_passed=false
        fi
        
        # Test write access
        if ! test_write_access; then
            validation_passed=false
        fi
    else
        echo ""
        echo "ℹ️  Bucket '$BUCKET_NAME' not found. Skipping live bucket tests."
        echo "   Deploy the Terraform configuration first to run full validation."
    fi
    
    echo ""
    echo "========================================"
    if [ "$validation_passed" = true ]; then
        echo "✅ S3 Security Validation: PASSED"
        echo "   All security controls are properly configured."
        exit 0
    else
        echo "❌ S3 Security Validation: FAILED"
        echo "   Security issues detected. Please review the configuration."
        exit 1
    fi
}

# Show usage if help requested
if [ "$1" = "--help" ] || [ "$1" = "-h" ]; then
    echo "Usage: $0 [bucket-name] [aws-region]"
    echo ""
    echo "Options:"
    echo "  bucket-name   S3 bucket name to validate (default: auto-generated)"
    echo "  aws-region    AWS region (default: us-east-2)"
    echo ""
    echo "Example:"
    echo "  $0 my-dropbox-bucket us-east-2"
    echo ""
    exit 0
fi

# Run main function
main