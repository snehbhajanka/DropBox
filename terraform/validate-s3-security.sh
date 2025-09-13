#!/bin/bash

# S3 Security Validation Script
# This script validates that S3 buckets have proper public access blocking configured

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}=== S3 Security Validation Script ===${NC}"
echo "This script validates S3 bucket security configurations"
echo

# Check if AWS CLI is installed
if ! command -v aws &> /dev/null; then
    echo -e "${RED}ERROR: AWS CLI is not installed. Please install it first.${NC}"
    exit 1
fi

# Check if AWS credentials are configured
if ! aws sts get-caller-identity &> /dev/null; then
    echo -e "${RED}ERROR: AWS credentials are not configured. Please run 'aws configure' first.${NC}"
    exit 1
fi

# Function to check bucket public access block
check_bucket_public_access() {
    local bucket_name=$1
    echo -n "Checking bucket: $bucket_name ... "
    
    if aws s3api head-bucket --bucket "$bucket_name" &> /dev/null; then
        local pab_config=$(aws s3api get-public-access-block --bucket "$bucket_name" 2>/dev/null)
        
        if [ $? -eq 0 ]; then
            local block_public_acls=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.BlockPublicAcls')
            local ignore_public_acls=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.IgnorePublicAcls')
            local block_public_policy=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.BlockPublicPolicy')
            local restrict_public_buckets=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.RestrictPublicBuckets')
            
            if [ "$block_public_acls" = "true" ] && [ "$ignore_public_acls" = "true" ] && [ "$block_public_policy" = "true" ] && [ "$restrict_public_buckets" = "true" ]; then
                echo -e "${GREEN}SECURE${NC}"
                return 0
            else
                echo -e "${RED}INSECURE${NC}"
                echo "  - BlockPublicAcls: $block_public_acls"
                echo "  - IgnorePublicAcls: $ignore_public_acls"
                echo "  - BlockPublicPolicy: $block_public_policy"
                echo "  - RestrictPublicBuckets: $restrict_public_buckets"
                return 1
            fi
        else
            echo -e "${RED}NO PUBLIC ACCESS BLOCK CONFIGURED${NC}"
            return 1
        fi
    else
        echo -e "${YELLOW}BUCKET DOES NOT EXIST${NC}"
        return 2
    fi
}

# Function to test unauthorized access
test_unauthorized_access() {
    local bucket_name=$1
    echo -n "Testing unauthorized access to bucket: $bucket_name ... "
    
    # Create a temporary file
    local temp_file="/tmp/test-file-$(date +%s).txt"
    echo "This is a test file for unauthorized access validation" > "$temp_file"
    
    # Try to upload without credentials (should fail)
    if aws s3 cp "$temp_file" "s3://$bucket_name/test-file.txt" --no-sign-request &> /dev/null; then
        echo -e "${RED}FAILED - Unauthorized upload succeeded${NC}"
        rm -f "$temp_file"
        return 1
    else
        echo -e "${GREEN}PASSED - Unauthorized upload blocked${NC}"
        rm -f "$temp_file"
        return 0
    fi
}

# Main validation
main() {
    local bucket_prefix=${1:-"dropbox-storage"}
    local bucket_count=${2:-13}
    local failed_buckets=0
    local total_buckets=0
    
    echo "Validating $bucket_count buckets with prefix: $bucket_prefix"
    echo

    for i in $(seq 1 $bucket_count); do
        local bucket_name="$bucket_prefix-$i"
        total_buckets=$((total_buckets + 1))
        
        if ! check_bucket_public_access "$bucket_name"; then
            failed_buckets=$((failed_buckets + 1))
        fi
    done

    echo
    echo "=== VALIDATION SUMMARY ==="
    echo "Total buckets checked: $total_buckets"
    echo "Secure buckets: $((total_buckets - failed_buckets))"
    echo "Insecure buckets: $failed_buckets"
    
    if [ $failed_buckets -eq 0 ]; then
        echo -e "${GREEN}✓ All buckets are properly secured!${NC}"
        exit 0
    else
        echo -e "${RED}✗ $failed_buckets bucket(s) need attention!${NC}"
        exit 1
    fi
}

# Check if jq is installed
if ! command -v jq &> /dev/null; then
    echo -e "${RED}ERROR: jq is not installed. Please install it first.${NC}"
    echo "On Ubuntu/Debian: sudo apt-get install jq"
    echo "On macOS: brew install jq"
    exit 1
fi

# Parse command line arguments
BUCKET_PREFIX=${1:-"dropbox-storage"}
BUCKET_COUNT=${2:-13}

echo "Usage: $0 [bucket-prefix] [bucket-count]"
echo "Example: $0 dropbox-storage 13"
echo

# Run main validation
main "$BUCKET_PREFIX" "$BUCKET_COUNT"