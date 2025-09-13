# Terraform configuration for secure S3 buckets
# This addresses the security misconfiguration S3.3 - Block Public Write Access

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

# S3 bucket for the DropBox application with secure configuration
resource "aws_s3_bucket" "dropbox_bucket" {
  bucket = var.bucket_name

  tags = {
    Name        = "DropBox Application Bucket"
    Environment = var.environment
    Purpose     = "File storage for DropBox application"
    Security    = "Public access blocked"
  }
}

# Block all public access to the S3 bucket
# This addresses the CRITICAL security misconfiguration S3.3
resource "aws_s3_bucket_public_access_block" "dropbox_bucket_pab" {
  bucket = aws_s3_bucket.dropbox_bucket.id

  # Block public ACLs - prevents new public ACLs from being applied
  block_public_acls = true

  # Ignore public ACLs - ignores any existing public ACLs
  ignore_public_acls = true

  # Block public bucket policies - prevents public bucket policies
  block_public_policy = true

  # Restrict public buckets - restricts access to buckets with public policies
  restrict_public_buckets = true
}

# Server-side encryption configuration
resource "aws_s3_bucket_server_side_encryption_configuration" "dropbox_bucket_encryption" {
  bucket = aws_s3_bucket.dropbox_bucket.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
    bucket_key_enabled = true
  }
}

# Versioning configuration
resource "aws_s3_bucket_versioning" "dropbox_bucket_versioning" {
  bucket = aws_s3_bucket.dropbox_bucket.id
  versioning_configuration {
    status = "Enabled"
  }
}

# Lifecycle configuration to manage costs
resource "aws_s3_bucket_lifecycle_configuration" "dropbox_bucket_lifecycle" {
  bucket = aws_s3_bucket.dropbox_bucket.id

  rule {
    id     = "delete_old_versions"
    status = "Enabled"
    
    filter {
      prefix = ""
    }

    noncurrent_version_expiration {
      noncurrent_days = 90
    }
  }
}