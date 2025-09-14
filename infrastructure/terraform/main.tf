# Terraform configuration for secure S3 bucket setup
# This configuration addresses the S3.3 security finding by ensuring
# all public write access is blocked on S3 buckets

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

# S3 bucket with secure configuration
resource "aws_s3_bucket" "dropbox_storage" {
  bucket = var.bucket_name

  tags = {
    Name        = "DropBox Storage"
    Environment = var.environment
    Purpose     = "File storage for DropBox application"
    SecurityCompliance = "S3.3-Compliant"
  }
}

# Block all public access to prevent the S3.3 security misconfiguration
resource "aws_s3_bucket_public_access_block" "dropbox_storage_pab" {
  bucket = aws_s3_bucket.dropbox_storage.id

  # Block public ACLs - prevents new public ACLs from being applied
  block_public_acls = true
  
  # Ignore public ACLs - ignores any existing public ACLs
  ignore_public_acls = true
  
  # Block public policy - prevents public bucket policies from being applied
  block_public_policy = true
  
  # Restrict public buckets - restricts access to buckets with public policies
  restrict_public_buckets = true
}

# Server-side encryption configuration
resource "aws_s3_bucket_server_side_encryption_configuration" "dropbox_storage_encryption" {
  bucket = aws_s3_bucket.dropbox_storage.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
    bucket_key_enabled = true
  }
}

# Versioning configuration
resource "aws_s3_bucket_versioning" "dropbox_storage_versioning" {
  bucket = aws_s3_bucket.dropbox_storage.id
  versioning_configuration {
    status = "Enabled"
  }
}

# Lifecycle configuration to manage storage costs
resource "aws_s3_bucket_lifecycle_configuration" "dropbox_storage_lifecycle" {
  bucket = aws_s3_bucket.dropbox_storage.id

  rule {
    id     = "transition_to_ia"
    status = "Enabled"

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

# Bucket logging configuration
resource "aws_s3_bucket_logging" "dropbox_storage_logging" {
  bucket = aws_s3_bucket.dropbox_storage.id

  target_bucket = aws_s3_bucket.dropbox_access_logs.id
  target_prefix = "access-logs/"
}

# Separate bucket for access logs
resource "aws_s3_bucket" "dropbox_access_logs" {
  bucket = "${var.bucket_name}-access-logs"

  tags = {
    Name        = "DropBox Access Logs"
    Environment = var.environment
    Purpose     = "Access logs for DropBox storage bucket"
  }
}

# Block public access for logs bucket as well
resource "aws_s3_bucket_public_access_block" "dropbox_access_logs_pab" {
  bucket = aws_s3_bucket.dropbox_access_logs.id

  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = true
  restrict_public_buckets = true
}