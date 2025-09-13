# Test configuration for validating S3 security settings
# This validates that the security misconfiguration S3.3 is properly addressed

terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# Call the main module
module "dropbox_s3" {
  source = "../"
  
  bucket_name = "test-dropbox-secure-bucket-${random_id.bucket_suffix.hex}"
  environment = "test"
  aws_region  = "us-east-1"
}

# Generate random suffix for unique bucket name in tests
resource "random_id" "bucket_suffix" {
  byte_length = 4
}

# Data source to validate the public access block configuration
data "aws_s3_bucket_public_access_block" "test_validation" {
  bucket = module.dropbox_s3.bucket_name
  
  depends_on = [module.dropbox_s3]
}

# Validation checks using local values and outputs
locals {
  # These checks ensure all security requirements are met
  security_checks = {
    block_public_acls_enabled       = data.aws_s3_bucket_public_access_block.test_validation.block_public_acls
    ignore_public_acls_enabled      = data.aws_s3_bucket_public_access_block.test_validation.ignore_public_acls
    block_public_policy_enabled     = data.aws_s3_bucket_public_access_block.test_validation.block_public_policy
    restrict_public_buckets_enabled = data.aws_s3_bucket_public_access_block.test_validation.restrict_public_buckets
  }
  
  # All security checks must pass (all values must be true)
  all_security_checks_passed = alltrue([
    local.security_checks.block_public_acls_enabled,
    local.security_checks.ignore_public_acls_enabled,
    local.security_checks.block_public_policy_enabled,
    local.security_checks.restrict_public_buckets_enabled
  ])
}

# Output validation results
output "security_validation_results" {
  description = "Results of security validation tests"
  value = {
    bucket_name                   = module.dropbox_s3.bucket_name
    bucket_arn                    = module.dropbox_s3.bucket_arn
    security_checks               = local.security_checks
    all_security_checks_passed    = local.all_security_checks_passed
    public_access_block_config    = module.dropbox_s3.public_access_block_configuration
  }
}

# Assert that all security checks pass
output "security_assertion" {
  description = "Assertion that all security requirements are met"
  value = local.all_security_checks_passed ? "✅ PASS: All S3 security requirements are met" : "❌ FAIL: S3 security requirements not met"
}