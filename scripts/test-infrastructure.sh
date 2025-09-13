#!/bin/bash

# Infrastructure validation test script
# This script validates that all infrastructure files are correctly configured

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

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

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

log "INFO" "Starting infrastructure validation tests"
log "INFO" "Project root: $PROJECT_ROOT"

# Test 1: Check if all infrastructure files exist
test_files_exist() {
    log "INFO" "Test 1: Checking if all infrastructure files exist..."
    
    local files=(
        "infrastructure/terraform/main.tf"
        "infrastructure/terraform/variables.tf" 
        "infrastructure/terraform/outputs.tf"
        "infrastructure/terraform/terraform.tfvars.example"
        "infrastructure/cloudformation/s3-secure-buckets.yaml"
        "infrastructure/README.md"
        "scripts/validate-s3-security.sh"
        "scripts/deploy-s3-security.sh"
    )
    
    local missing_files=()
    
    for file in "${files[@]}"; do
        if [[ -f "$PROJECT_ROOT/$file" ]]; then
            log "SUCCESS" "✓ Found: $file"
        else
            log "ERROR" "✗ Missing: $file"
            missing_files+=("$file")
        fi
    done
    
    if [[ ${#missing_files[@]} -eq 0 ]]; then
        log "SUCCESS" "All infrastructure files exist"
        return 0
    else
        log "ERROR" "Missing ${#missing_files[@]} files"
        return 1
    fi
}

# Test 2: Validate Terraform syntax
test_terraform_syntax() {
    log "INFO" "Test 2: Validating Terraform syntax..."
    
    if ! command -v terraform &> /dev/null; then
        log "WARNING" "Terraform not installed - skipping syntax validation"
        return 0
    fi
    
    cd "$PROJECT_ROOT/infrastructure/terraform"
    
    # Initialize terraform (this downloads providers but doesn't create resources)
    if terraform init -backend=false &> /tmp/terraform_init.log; then
        log "SUCCESS" "Terraform init successful"
    else
        log "ERROR" "Terraform init failed:"
        cat /tmp/terraform_init.log
        return 1
    fi
    
    # Validate syntax
    if terraform validate &> /tmp/terraform_validate.log; then
        log "SUCCESS" "Terraform syntax is valid"
        return 0
    else
        log "ERROR" "Terraform validation failed:"
        cat /tmp/terraform_validate.log
        return 1
    fi
}

# Test 3: Validate CloudFormation template
test_cloudformation_syntax() {
    log "INFO" "Test 3: Validating CloudFormation template..."
    
    if ! command -v aws &> /dev/null; then
        log "WARNING" "AWS CLI not installed - skipping CloudFormation validation"
        return 0
    fi
    
    local template="$PROJECT_ROOT/infrastructure/cloudformation/s3-secure-buckets.yaml"
    
    if aws cloudformation validate-template --template-body "file://$template" &> /tmp/cf_validate.log; then
        log "SUCCESS" "CloudFormation template is valid"
        return 0
    else
        log "ERROR" "CloudFormation validation failed:"
        cat /tmp/cf_validate.log
        return 1
    fi
}

# Test 4: Check script permissions
test_script_permissions() {
    log "INFO" "Test 4: Checking script permissions..."
    
    local scripts=(
        "scripts/validate-s3-security.sh"
        "scripts/deploy-s3-security.sh"
    )
    
    local non_executable=()
    
    for script in "${scripts[@]}"; do
        if [[ -x "$PROJECT_ROOT/$script" ]]; then
            log "SUCCESS" "✓ Executable: $script"
        else
            log "ERROR" "✗ Not executable: $script"
            non_executable+=("$script")
        fi
    done
    
    if [[ ${#non_executable[@]} -eq 0 ]]; then
        log "SUCCESS" "All scripts are executable"
        return 0
    else
        log "ERROR" "${#non_executable[@]} scripts are not executable"
        return 1
    fi
}

# Test 5: Validate security configuration in Terraform
test_security_configuration() {
    log "INFO" "Test 5: Validating security configuration..."
    
    local main_tf="$PROJECT_ROOT/infrastructure/terraform/main.tf"
    local security_checks=(
        "block_public_acls.*=.*true"
        "ignore_public_acls.*=.*true"
        "block_public_policy.*=.*true"
        "restrict_public_buckets.*=.*true"
    )
    
    local missing_checks=()
    
    for check in "${security_checks[@]}"; do
        if grep -q "$check" "$main_tf"; then
            log "SUCCESS" "✓ Found security control: $(echo "$check" | cut -d'=' -f1)"
        else
            log "ERROR" "✗ Missing security control: $(echo "$check" | cut -d'=' -f1)"
            missing_checks+=("$check")
        fi
    done
    
    if [[ ${#missing_checks[@]} -eq 0 ]]; then
        log "SUCCESS" "All S3.3 security controls are configured"
        return 0
    else
        log "ERROR" "Missing ${#missing_checks[@]} security controls"
        return 1
    fi
}

# Test 6: Validate CloudFormation security configuration
test_cloudformation_security() {
    log "INFO" "Test 6: Validating CloudFormation security configuration..."
    
    local cf_template="$PROJECT_ROOT/infrastructure/cloudformation/s3-secure-buckets.yaml"
    local security_checks=(
        "BlockPublicAcls:.*true"
        "IgnorePublicAcls:.*true"
        "BlockPublicPolicy:.*true"
        "RestrictPublicBuckets:.*true"
    )
    
    local missing_checks=()
    
    for check in "${security_checks[@]}"; do
        if grep -q "$check" "$cf_template"; then
            log "SUCCESS" "✓ Found security control: $(echo "$check" | cut -d':' -f1)"
        else
            log "ERROR" "✗ Missing security control: $(echo "$check" | cut -d':' -f1)"
            missing_checks+=("$check")
        fi
    done
    
    if [[ ${#missing_checks[@]} -eq 0 ]]; then
        log "SUCCESS" "All S3.3 security controls are configured in CloudFormation"
        return 0
    else
        log "ERROR" "Missing ${#missing_checks[@]} security controls in CloudFormation"
        return 1
    fi
}

# Run all tests
main() {
    local test_results=()
    
    test_files_exist && test_results+=("FILES_EXIST:PASS") || test_results+=("FILES_EXIST:FAIL")
    echo
    
    test_terraform_syntax && test_results+=("TERRAFORM_SYNTAX:PASS") || test_results+=("TERRAFORM_SYNTAX:FAIL")
    echo
    
    test_cloudformation_syntax && test_results+=("CLOUDFORMATION_SYNTAX:PASS") || test_results+=("CLOUDFORMATION_SYNTAX:FAIL")
    echo
    
    test_script_permissions && test_results+=("SCRIPT_PERMISSIONS:PASS") || test_results+=("SCRIPT_PERMISSIONS:FAIL")
    echo
    
    test_security_configuration && test_results+=("TERRAFORM_SECURITY:PASS") || test_results+=("TERRAFORM_SECURITY:FAIL")
    echo
    
    test_cloudformation_security && test_results+=("CLOUDFORMATION_SECURITY:PASS") || test_results+=("CLOUDFORMATION_SECURITY:FAIL")
    echo
    
    # Summary
    log "INFO" "=== Test Results Summary ==="
    local total_tests=${#test_results[@]}
    local passed_tests=0
    
    for result in "${test_results[@]}"; do
        local test_name=$(echo "$result" | cut -d':' -f1)
        local test_status=$(echo "$result" | cut -d':' -f2)
        
        if [[ "$test_status" == "PASS" ]]; then
            log "SUCCESS" "$test_name: PASSED"
            ((passed_tests++))
        else
            log "ERROR" "$test_name: FAILED"
        fi
    done
    
    echo
    log "INFO" "Tests passed: $passed_tests/$total_tests"
    
    if [[ $passed_tests -eq $total_tests ]]; then
        log "SUCCESS" "All infrastructure validation tests PASSED! ✅"
        exit 0
    else
        log "ERROR" "Some infrastructure validation tests FAILED! ❌"
        exit 1
    fi
}

# Run main function
main "$@"