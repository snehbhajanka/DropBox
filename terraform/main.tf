# Terraform configuration for secure S3 buckets for DropBox application
# This configuration addresses the security misconfiguration S3.3 by blocking public write access

terraform {
  required_version = ">= 1.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

# Data source to get current AWS account ID
data "aws_caller_identity" "current" {}

# S3 buckets for the DropBox application
resource "aws_s3_bucket" "dropbox_buckets" {
  count  = var.bucket_count
  bucket = "${var.bucket_prefix}-${count.index + 1}"

  tags = {
    Name        = "${var.bucket_prefix}-${count.index + 1}"
    Environment = var.environment
    Application = "DropBox"
    ManagedBy   = "Terraform"
  }
}

# Block all public access to S3 buckets - CRITICAL SECURITY CONFIGURATION
resource "aws_s3_bucket_public_access_block" "dropbox_bucket_pab" {
  count  = var.bucket_count
  bucket = aws_s3_bucket.dropbox_buckets[count.index].id

  # Block public ACLs - prevents new public ACLs and uploading of public objects
  block_public_acls = true

  # Ignore public ACLs - ignores all public ACLs on this bucket and any objects it contains
  ignore_public_acls = true

  # Block public bucket policies - prevents users from putting a bucket policy that would grant public access
  block_public_policy = true

  # Restrict public bucket access - restricts access to this bucket to only AWS service principals and authorized users
  restrict_public_buckets = true
}

# Server-side encryption configuration
resource "aws_s3_bucket_server_side_encryption_configuration" "dropbox_bucket_encryption" {
  count  = var.bucket_count
  bucket = aws_s3_bucket.dropbox_buckets[count.index].id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
    bucket_key_enabled = true
  }
}

# Versioning configuration
resource "aws_s3_bucket_versioning" "dropbox_bucket_versioning" {
  count  = var.bucket_count
  bucket = aws_s3_bucket.dropbox_buckets[count.index].id

  versioning_configuration {
    status = var.enable_versioning ? "Enabled" : "Suspended"
  }
}

# Lifecycle configuration for cost optimization
resource "aws_s3_bucket_lifecycle_configuration" "dropbox_bucket_lifecycle" {
  count  = var.bucket_count
  bucket = aws_s3_bucket.dropbox_buckets[count.index].id

  rule {
    id     = "dropbox_lifecycle_rule"
    status = "Enabled"

    filter {
      prefix = ""
    }

    transition {
      days          = 30
      storage_class = "STANDARD_IA"
    }

    transition {
      days          = 90
      storage_class = "GLACIER"
    }

    noncurrent_version_expiration {
      noncurrent_days = 90
    }
  }
}