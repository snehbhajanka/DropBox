#!/bin/bash

# S3 Security Validation Script
# This script validates that S3 buckets have proper security configuration
# to prevent the S3.3 misconfiguration (public write access)

set -e

echo "🔒 S3 Security Validation Script"
echo "================================"

# Function to check if AWS CLI is available
check_aws_cli() {
    if ! command -v aws &> /dev/null; then
        echo "❌ AWS CLI is not installed or not in PATH"
        echo "Please install AWS CLI to run security validation"
        exit 1
    fi
    echo "✅ AWS CLI is available"
}

# Function to validate bucket security settings
validate_bucket_security() {
    local bucket_name=$1
    
    echo "🔍 Validating security for bucket: $bucket_name"
    
    # Check if bucket exists
    if ! aws s3api head-bucket --bucket "$bucket_name" 2>/dev/null; then
        echo "⚠️  Bucket $bucket_name does not exist or is not accessible"
        return 1
    fi
    
    # Get public access block configuration
    echo "   Checking Block Public Access settings..."
    
    local pab_config
    if pab_config=$(aws s3api get-public-access-block --bucket "$bucket_name" --output json 2>/dev/null); then
        
        local block_public_acls=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.BlockPublicAcls')
        local ignore_public_acls=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.IgnorePublicAcls')
        local block_public_policy=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.BlockPublicPolicy')
        local restrict_public_buckets=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.RestrictPublicBuckets')
        
        echo "   - BlockPublicAcls: $block_public_acls"
        echo "   - IgnorePublicAcls: $ignore_public_acls"
        echo "   - BlockPublicPolicy: $block_public_policy"
        echo "   - RestrictPublicBuckets: $restrict_public_buckets"
        
        # Validate all settings are true
        if [[ "$block_public_acls" == "true" && 
              "$ignore_public_acls" == "true" && 
              "$block_public_policy" == "true" && 
              "$restrict_public_buckets" == "true" ]]; then
            echo "✅ Bucket $bucket_name has proper security configuration"
            return 0
        else
            echo "❌ Bucket $bucket_name has insecure configuration - S3.3 misconfiguration detected"
            return 1
        fi
    else
        echo "❌ Could not retrieve public access block configuration for $bucket_name"
        return 1
    fi
}

# Function to test public write access (should fail)
test_public_write_prevention() {
    local bucket_name=$1
    local test_file="/tmp/security-test-file.txt"
    
    echo "🧪 Testing public write access prevention for bucket: $bucket_name"
    
    # Create a test file
    echo "This is a security test file" > "$test_file"
    
    # Attempt to upload without credentials (should fail)
    echo "   Attempting anonymous upload (should fail)..."
    
    if aws s3 cp "$test_file" "s3://$bucket_name/security-test.txt" --no-sign-request 2>/dev/null; then
        echo "❌ SECURITY ISSUE: Anonymous upload succeeded - public write access is enabled"
        rm -f "$test_file"
        return 1
    else
        echo "✅ Anonymous upload properly blocked - public write access is disabled"
        rm -f "$test_file"
        return 0
    fi
}

# Function to generate security report
generate_security_report() {
    local bucket_name=$1
    local report_file="s3-security-report-$(date +%Y%m%d-%H%M%S).json"
    
    echo "📋 Generating security report: $report_file"
    
    cat > "$report_file" << EOF
{
  "report_type": "S3 Security Validation",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "bucket_name": "$bucket_name",
  "misconfiguration_id": "S3.3",
  "misconfiguration_type": "Block Public Write Access",
  "severity": "CRITICAL"
}
EOF

    if validate_bucket_security "$bucket_name" > /dev/null 2>&1; then
        jq '. + {"status": "SECURE", "compliance": true, "public_write_blocked": true}' "$report_file" > "${report_file}.tmp" && mv "${report_file}.tmp" "$report_file"
    else
        jq '. + {"status": "INSECURE", "compliance": false, "public_write_blocked": false, "remediation_required": true}' "$report_file" > "${report_file}.tmp" && mv "${report_file}.tmp" "$report_file"
    fi
    
    echo "   Report saved to: $report_file"
}

# Main validation function
main() {
    local bucket_name=${1:-"dropbox-secure-storage"}
    
    echo "Target bucket: $bucket_name"
    echo ""
    
    # Check prerequisites
    check_aws_cli
    
    if ! command -v jq &> /dev/null; then
        echo "⚠️  jq is not available - some features may be limited"
    fi
    
    echo ""
    
    # Validate bucket security
    local security_valid=false
    if validate_bucket_security "$bucket_name"; then
        security_valid=true
    fi
    
    echo ""
    
    # Test public write prevention
    local write_blocked=false
    if test_public_write_prevention "$bucket_name"; then
        write_blocked=true
    fi
    
    echo ""
    
    # Generate report if jq is available
    if command -v jq &> /dev/null; then
        generate_security_report "$bucket_name"
        echo ""
    fi
    
    # Final status
    echo "🏁 Validation Summary"
    echo "===================="
    
    if [[ "$security_valid" == true && "$write_blocked" == true ]]; then
        echo "✅ SECURE: Bucket $bucket_name properly configured"
        echo "✅ S3.3 misconfiguration RESOLVED"
        echo "✅ Public write access BLOCKED"
        exit 0
    else
        echo "❌ INSECURE: Bucket $bucket_name requires remediation"
        echo "❌ S3.3 misconfiguration DETECTED"
        echo "❌ Immediate action required"
        exit 1
    fi
}

# Show usage if no arguments provided
if [[ $# -eq 0 ]]; then
    echo "Usage: $0 <bucket-name>"
    echo ""
    echo "This script validates S3 bucket security configuration to prevent"
    echo "the S3.3 misconfiguration (public write access)."
    echo ""
    echo "Example:"
    echo "  $0 my-secure-bucket"
    echo ""
    exit 1
fi

main "$@"