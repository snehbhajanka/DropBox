#!/bin/bash

# S3 Bucket Security Deployment Script
# Deploys secure S3 buckets using AWS CLI with proper public access blocking

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
ENVIRONMENT="dev"
DRY_RUN=false

# Help function
show_help() {
    cat << EOF
Usage: $0 --bucket-prefix <prefix> [options]

Deploy secure S3 buckets for DropBox application with S3.3 compliance.

Required:
  --bucket-prefix <prefix>    Prefix for bucket names (must be globally unique)

Optional:
  --region <region>          AWS region (default: us-east-1)
  --environment <env>        Environment name (default: dev)
  --dry-run                  Show what would be created without actually creating
  --help                     Show this help message

Examples:
  $0 --bucket-prefix mycompany-dropbox
  $0 --bucket-prefix mycompany-dropbox --region us-west-2 --environment prod
  $0 --bucket-prefix mycompany-dropbox --dry-run
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
        --environment)
            ENVIRONMENT="$2"
            shift 2
            ;;
        --dry-run)
            DRY_RUN=true
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

# Create S3 bucket with security controls
create_secure_bucket() {
    local bucket_name=$1
    local purpose=$2
    
    log "INFO" "Creating secure S3 bucket: $bucket_name ($purpose)"
    
    if [[ "$DRY_RUN" == "true" ]]; then
        log "INFO" "[DRY RUN] Would create bucket: $bucket_name"
        return 0
    fi
    
    # Check if bucket already exists
    if aws s3api head-bucket --bucket "$bucket_name" --region "$REGION" 2>/dev/null; then
        log "WARNING" "Bucket $bucket_name already exists"
    else
        # Create the bucket
        if [[ "$REGION" == "us-east-1" ]]; then
            aws s3api create-bucket --bucket "$bucket_name" --region "$REGION"
        else
            aws s3api create-bucket --bucket "$bucket_name" --region "$REGION" \
                --create-bucket-configuration LocationConstraint="$REGION"
        fi
        log "SUCCESS" "Created bucket: $bucket_name"
    fi
    
    # Apply Public Access Block (CRITICAL SECURITY CONTROL)
    log "INFO" "Applying public access block to $bucket_name..."
    aws s3api put-public-access-block \
        --bucket "$bucket_name" \
        --public-access-block-configuration \
        BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
    
    log "SUCCESS" "Applied public access block to $bucket_name"
    
    # Enable versioning
    log "INFO" "Enabling versioning for $bucket_name..."
    aws s3api put-bucket-versioning \
        --bucket "$bucket_name" \
        --versioning-configuration Status=Enabled
    
    log "SUCCESS" "Enabled versioning for $bucket_name"
    
    # Enable encryption
    log "INFO" "Enabling encryption for $bucket_name..."
    aws s3api put-bucket-encryption \
        --bucket "$bucket_name" \
        --server-side-encryption-configuration '{
            "Rules": [
                {
                    "ApplyServerSideEncryptionByDefault": {
                        "SSEAlgorithm": "AES256"
                    },
                    "BucketKeyEnabled": true
                }
            ]
        }'
    
    log "SUCCESS" "Enabled encryption for $bucket_name"
    
    # Apply tags
    log "INFO" "Applying tags to $bucket_name..."
    aws s3api put-bucket-tagging \
        --bucket "$bucket_name" \
        --tagging '{
            "TagSet": [
                {"Key": "Environment", "Value": "'$ENVIRONMENT'"},
                {"Key": "Project", "Value": "DropBox"},
                {"Key": "Purpose", "Value": "'$purpose'"},
                {"Key": "Security", "Value": "S3.3-Compliant"},
                {"Key": "ManagedBy", "Value": "DropBox-Deploy-Script"}
            ]
        }'
    
    log "SUCCESS" "Applied tags to $bucket_name"
}

# Apply lifecycle policy
apply_lifecycle_policy() {
    local bucket_name=$1
    local policy_type=$2
    
    log "INFO" "Applying lifecycle policy ($policy_type) to $bucket_name..."
    
    if [[ "$DRY_RUN" == "true" ]]; then
        log "INFO" "[DRY RUN] Would apply lifecycle policy to: $bucket_name"
        return 0
    fi
    
    case $policy_type in
        "standard")
            aws s3api put-bucket-lifecycle-configuration \
                --bucket "$bucket_name" \
                --lifecycle-configuration '{
                    "Rules": [
                        {
                            "ID": "FileLifecycle",
                            "Status": "Enabled",
                            "Transitions": [
                                {
                                    "Days": 30,
                                    "StorageClass": "STANDARD_IA"
                                },
                                {
                                    "Days": 90,
                                    "StorageClass": "GLACIER"
                                }
                            ],
                            "Expiration": {
                                "Days": 730
                            }
                        }
                    ]
                }'
            ;;
        "temp")
            aws s3api put-bucket-lifecycle-configuration \
                --bucket "$bucket_name" \
                --lifecycle-configuration '{
                    "Rules": [
                        {
                            "ID": "TempCleanup",
                            "Status": "Enabled",
                            "Expiration": {
                                "Days": 7
                            },
                            "AbortIncompleteMultipartUpload": {
                                "DaysAfterInitiation": 1
                            }
                        }
                    ]
                }'
            ;;
    esac
    
    log "SUCCESS" "Applied lifecycle policy to $bucket_name"
}

# Verify bucket security
verify_bucket_security() {
    local bucket_name=$1
    
    log "INFO" "Verifying security configuration for $bucket_name..."
    
    if [[ "$DRY_RUN" == "true" ]]; then
        log "INFO" "[DRY RUN] Would verify security for: $bucket_name"
        return 0
    fi
    
    # Get public access block configuration
    local pab_config=$(aws s3api get-public-access-block --bucket "$bucket_name" --region "$REGION")
    
    # Parse and verify each setting
    local block_public_acls=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.BlockPublicAcls')
    local ignore_public_acls=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.IgnorePublicAcls')
    local block_public_policy=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.BlockPublicPolicy')
    local restrict_public_buckets=$(echo "$pab_config" | jq -r '.PublicAccessBlockConfiguration.RestrictPublicBuckets')
    
    if [[ "$block_public_acls" == "true" && "$ignore_public_acls" == "true" && 
          "$block_public_policy" == "true" && "$restrict_public_buckets" == "true" ]]; then
        log "SUCCESS" "Security verification passed for $bucket_name - S3.3 compliant ✅"
        return 0
    else
        log "ERROR" "Security verification failed for $bucket_name"
        return 1
    fi
}

# Main deployment function
main() {
    log "INFO" "Starting S3 bucket security deployment"
    log "INFO" "Bucket prefix: $BUCKET_PREFIX"
    log "INFO" "AWS region: $REGION"
    log "INFO" "Environment: $ENVIRONMENT"
    
    if [[ "$DRY_RUN" == "true" ]]; then
        log "WARNING" "DRY RUN MODE - No resources will be created"
    fi
    
    # Check AWS CLI
    if ! command -v aws &> /dev/null; then
        log "ERROR" "AWS CLI is not installed"
        exit 1
    fi
    
    if ! aws sts get-caller-identity &> /dev/null; then
        log "ERROR" "AWS credentials are not configured"
        exit 1
    fi
    
    # Define buckets to create
    local primary_bucket="${BUCKET_PREFIX}-dropbox-storage"
    local temp_bucket="${BUCKET_PREFIX}-dropbox-temp-uploads"
    
    # Create and configure primary storage bucket
    echo
    create_secure_bucket "$primary_bucket" "File Storage"
    apply_lifecycle_policy "$primary_bucket" "standard"
    verify_bucket_security "$primary_bucket"
    
    # Create and configure temporary uploads bucket
    echo
    create_secure_bucket "$temp_bucket" "Temporary Upload Storage"
    apply_lifecycle_policy "$temp_bucket" "temp"
    verify_bucket_security "$temp_bucket"
    
    echo
    log "SUCCESS" "Deployment completed successfully! 🎉"
    echo
    log "INFO" "Created buckets:"
    log "INFO" "  - $primary_bucket (primary storage)"
    log "INFO" "  - $temp_bucket (temporary uploads)"
    echo
    log "INFO" "All buckets are S3.3 compliant with public access blocked."
    log "INFO" "To validate security: ./scripts/validate-s3-security.sh --bucket-prefix $BUCKET_PREFIX --region $REGION"
}

# Run main function
main "$@"