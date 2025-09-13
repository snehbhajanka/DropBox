#!/bin/bash

# Script to apply secure S3 bucket configuration via AWS CLI
# This script addresses the S3.3 security control by blocking all public access

set -e

# Configuration
BUCKET_NAME=""
AWS_REGION="us-east-1"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
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

# Function to check if AWS CLI is installed
check_aws_cli() {
    if ! command -v aws &> /dev/null; then
        print_error "AWS CLI is not installed. Please install it first."
        exit 1
    fi
    print_status "AWS CLI is installed"
}

# Function to check AWS credentials
check_aws_credentials() {
    if ! aws sts get-caller-identity &> /dev/null; then
        print_error "AWS credentials not configured. Please run 'aws configure' first."
        exit 1
    fi
    print_status "AWS credentials are configured"
}

# Function to apply public access block settings
apply_public_access_block() {
    local bucket_name=$1
    
    print_status "Applying public access block settings to bucket: $bucket_name"
    
    aws s3api put-public-access-block \
        --bucket "$bucket_name" \
        --public-access-block-configuration \
        BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
    
    if [ $? -eq 0 ]; then
        print_status "Public access block settings applied successfully"
    else
        print_error "Failed to apply public access block settings"
        exit 1
    fi
}

# Function to verify the settings
verify_settings() {
    local bucket_name=$1
    
    print_status "Verifying public access block settings for bucket: $bucket_name"
    
    local result=$(aws s3api get-public-access-block --bucket "$bucket_name" --output json)
    
    echo "Current public access block configuration:"
    echo "$result" | jq '.PublicAccessBlockConfiguration'
    
    # Check if all settings are true
    local block_public_acls=$(echo "$result" | jq -r '.PublicAccessBlockConfiguration.BlockPublicAcls')
    local ignore_public_acls=$(echo "$result" | jq -r '.PublicAccessBlockConfiguration.IgnorePublicAcls')
    local block_public_policy=$(echo "$result" | jq -r '.PublicAccessBlockConfiguration.BlockPublicPolicy')
    local restrict_public_buckets=$(echo "$result" | jq -r '.PublicAccessBlockConfiguration.RestrictPublicBuckets')
    
    if [[ "$block_public_acls" == "true" && "$ignore_public_acls" == "true" && 
          "$block_public_policy" == "true" && "$restrict_public_buckets" == "true" ]]; then
        print_status "✅ All public access block settings are correctly configured"
        print_status "✅ S3.3 security control compliance achieved"
    else
        print_error "❌ Some public access block settings are not configured correctly"
        exit 1
    fi
}

# Function to test public access (should fail)
test_public_access() {
    local bucket_name=$1
    
    print_status "Testing that public access is blocked (this should fail)..."
    
    # Try to list bucket contents without credentials (should fail)
    if aws s3 ls "s3://$bucket_name" --no-sign-request &> /dev/null; then
        print_error "❌ WARNING: Bucket is still publicly accessible!"
        exit 1
    else
        print_status "✅ Public access is properly blocked"
    fi
}

# Main function
main() {
    echo "=================================================="
    echo "S3 Security Remediation Script"
    echo "Addressing Control ID: S3.3"
    echo "=================================================="
    
    # Check if bucket name is provided
    if [ -z "$1" ]; then
        print_error "Usage: $0 <bucket-name> [aws-region]"
        print_error "Example: $0 my-dropbox-bucket us-east-1"
        exit 1
    fi
    
    BUCKET_NAME=$1
    if [ ! -z "$2" ]; then
        AWS_REGION=$2
    fi
    
    print_status "Target bucket: $BUCKET_NAME"
    print_status "AWS region: $AWS_REGION"
    
    # Pre-flight checks
    check_aws_cli
    check_aws_credentials
    
    # Check if bucket exists
    if ! aws s3api head-bucket --bucket "$BUCKET_NAME" 2>/dev/null; then
        print_error "Bucket '$BUCKET_NAME' does not exist or you don't have access to it"
        exit 1
    fi
    
    # Apply security settings
    apply_public_access_block "$BUCKET_NAME"
    
    # Wait a moment for changes to propagate
    sleep 2
    
    # Verify settings
    verify_settings "$BUCKET_NAME"
    
    # Test public access
    test_public_access "$BUCKET_NAME"
    
    print_status "=================================================="
    print_status "✅ S3 bucket security remediation completed successfully!"
    print_status "✅ Public write access has been blocked"
    print_status "✅ Compliance with S3.3 control achieved"
    print_status "=================================================="
}

# Run main function with all arguments
main "$@"