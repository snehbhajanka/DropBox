#!/bin/bash

# S3 Bucket Security Validation Script
# This script validates that S3 buckets comply with S3.3 security control requirements

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default values
BUCKET_PREFIX=""
REGION="us-east-1"
VERBOSE=false

# Help function
show_help() {
    cat << EOF
Usage: $0 --bucket-prefix <prefix> [options]

This script validates S3 bucket security configurations for S3.3 compliance.

Required:
  --bucket-prefix <prefix>    Prefix for bucket names to validate

Optional:
  --region <region>          AWS region (default: us-east-1)
  --verbose                  Enable verbose output
  --help                     Show this help message

Examples:
  $0 --bucket-prefix mycompany-dropbox
  $0 --bucket-prefix mycompany-dropbox --region us-west-2 --verbose
EOF
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --bucket-prefix)
            BUCKET_PREFIX="$2"
            shift 2
            ;;
        --region)
            REGION="$2"
            shift 2
            ;;
        --verbose)
            VERBOSE=true
            shift
            ;;
        --help)
            show_help
            exit 0
            ;;
        *)
            echo "Unknown option $1"
            show_help
            exit 1
            ;;
    esac
done

# Validate required parameters
if [[ -z "$BUCKET_PREFIX" ]]; then
    echo -e "${RED}Error: --bucket-prefix is required${NC}"
    show_help
    exit 1
fi

# Logging function
log() {
    local level=$1
    shift
    local message="$*"
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    
    case $level in
        "INFO")
            echo -e "${BLUE}[$timestamp] INFO:${NC} $message"
            ;;
        "SUCCESS")
            echo -e "${GREEN}[$timestamp] SUCCESS:${NC} $message"
            ;;
        "WARNING")
            echo -e "${YELLOW}[$timestamp] WARNING:${NC} $message"
            ;;
        "ERROR")
            echo -e "${RED}[$timestamp] ERROR:${NC} $message"
            ;;
    esac
}

# Verbose logging
vlog() {
    if [[ "$VERBOSE" == "true" ]]; then
        log "INFO" "$*"
    fi
}

# Check if AWS CLI is installed and configured
check_aws_cli() {
    log "INFO" "Checking AWS CLI configuration..."
    
    if ! command -v aws &> /dev/null; then
        log "ERROR" "AWS CLI is not installed. Please install it first."
        exit 1
    fi
    
    # Check if AWS credentials are configured
    if ! aws sts get-caller-identity &> /dev/null; then
        log "ERROR" "AWS credentials are not configured. Please run 'aws configure' first."
        exit 1
    fi
    
    local identity=$(aws sts get-caller-identity --output text --query 'Arn')
    vlog "AWS Identity: $identity"
    log "SUCCESS" "AWS CLI is configured correctly"
}

# Validate S3 bucket public access block configuration
validate_bucket_public_access_block() {
    local bucket_name=$1
    log "INFO" "Validating public access block for bucket: $bucket_name"
    
    # Check if bucket exists
    if ! aws s3api head-bucket --bucket "$bucket_name" --region "$REGION" 2>/dev/null; then
        log "WARNING" "Bucket $bucket_name does not exist or is not accessible"
        return 1
    fi
    
    # Get public access block configuration
    local pab_config
    if ! pab_config=$(aws s3api get-public-access-block --bucket "$bucket_name" --region "$REGION" 2>/dev/null); then
        log "ERROR" "Could not retrieve public access block configuration for $bucket_name"
        return 1
    fi
    
    vlog "Raw PAB config: $pab_config"
    
    # Parse configuration values
    local block_public_acls=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.BlockPublicAcls // false')
    local ignore_public_acls=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.IgnorePublicAcls // false')
    local block_public_policy=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.BlockPublicPolicy // false')
    local restrict_public_buckets=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.RestrictPublicBuckets // false')
    
    # Validation flags
    local all_valid=true
    
    # Check each setting
    if [[ "$block_public_acls" != "true" ]]; then
        log "ERROR" "BlockPublicAcls is not enabled for $bucket_name (Current: $block_public_acls)"
        all_valid=false
    else
        vlog "✓ BlockPublicAcls: $block_public_acls"
    fi
    
    if [[ "$ignore_public_acls" != "true" ]]; then
        log "ERROR" "IgnorePublicAcls is not enabled for $bucket_name (Current: $ignore_public_acls)"
        all_valid=false
    else
        vlog "✓ IgnorePublicAcls: $ignore_public_acls"
    fi
    
    if [[ "$block_public_policy" != "true" ]]; then
        log "ERROR" "BlockPublicPolicy is not enabled for $bucket_name (Current: $block_public_policy)"
        all_valid=false
    else
        vlog "✓ BlockPublicPolicy: $block_public_policy"
    fi
    
    if [[ "$restrict_public_buckets" != "true" ]]; then
        log "ERROR" "RestrictPublicBuckets is not enabled for $bucket_name (Current: $restrict_public_buckets)"
        all_valid=false
    else
        vlog "✓ RestrictPublicBuckets: $restrict_public_buckets"
    fi
    
    if [[ "$all_valid" == "true" ]]; then
        log "SUCCESS" "Bucket $bucket_name is S3.3 compliant - all public access is blocked"
        return 0
    else
        log "ERROR" "Bucket $bucket_name is NOT S3.3 compliant"
        return 1
    fi
}

# Main validation function
main() {
    log "INFO" "Starting S3 bucket security validation"
    log "INFO" "Bucket prefix: $BUCKET_PREFIX"
    log "INFO" "AWS region: $REGION"
    
    # Check AWS CLI
    check_aws_cli
    
    # Define bucket names to validate
    local buckets=(
        "${BUCKET_PREFIX}-dropbox-storage"
        "${BUCKET_PREFIX}-dropbox-temp-uploads"
    )
    
    local total_buckets=${#buckets[@]}
    local compliant_buckets=0
    local validation_errors=()
    
    log "INFO" "Validating $total_buckets buckets..."
    
    # Validate each bucket
    for bucket in "${buckets[@]}"; do
        echo
        if validate_bucket_public_access_block "$bucket"; then
            ((compliant_buckets++))
        else
            validation_errors+=("$bucket")
        fi
    done
    
    echo
    log "INFO" "Validation Summary:"
    log "INFO" "Total buckets checked: $total_buckets"
    log "INFO" "Compliant buckets: $compliant_buckets"
    log "INFO" "Non-compliant buckets: $((total_buckets - compliant_buckets))"
    
    if [[ ${#validation_errors[@]} -eq 0 ]]; then
        log "SUCCESS" "All buckets are S3.3 compliant! ✅"
        echo
        log "SUCCESS" "Security validation PASSED - No public write access detected"
        exit 0
    else
        echo
        log "ERROR" "The following buckets failed validation:"
        for bucket in "${validation_errors[@]}"; do
            log "ERROR" "  - $bucket"
        done
        echo
        log "ERROR" "Security validation FAILED - Public write access vulnerabilities detected"
        exit 1
    fi
}

# Run main function
main "$@"