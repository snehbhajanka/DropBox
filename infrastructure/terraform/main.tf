# S3 Bucket Security Configuration
# This configuration ensures S3 buckets are properly secured against public write access
# Addresses Security Issue S3.3 - Block Public Write Access

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

# Local variable for common tags
locals {
  common_tags = {
    Environment = var.environment
    Project     = "DropBox"
    ManagedBy   = "Terraform"
    SecurityControl = "S3.3-BlockPublicAccess"
  }
}

# S3 Bucket for DropBox application with secure configuration
resource "aws_s3_bucket" "dropbox_storage" {
  bucket = var.bucket_name
  tags   = local.common_tags
}

# Block all public access to the S3 bucket
resource "aws_s3_bucket_public_access_block" "dropbox_storage_pab" {
  bucket = aws_s3_bucket.dropbox_storage.id

  # Block public ACLs - prevents new ACLs that allow public access
  block_public_acls = true
  
  # Ignore public ACLs - ignores existing ACLs that allow public access
  ignore_public_acls = true
  
  # Block public bucket policies - prevents bucket policies that allow public access
  block_public_policy = true
  
  # Restrict public buckets - restricts access to buckets with public policies
  restrict_public_buckets = true
}

# Enable versioning for data protection
resource "aws_s3_bucket_versioning" "dropbox_storage_versioning" {
  bucket = aws_s3_bucket.dropbox_storage.id
  versioning_configuration {
    status = "Enabled"
  }
}

# Enable server-side encryption
resource "aws_s3_bucket_server_side_encryption_configuration" "dropbox_storage_encryption" {
  bucket = aws_s3_bucket.dropbox_storage.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
    bucket_key_enabled = true
  }
}

# Private ACL (explicit setting, though public access is blocked above)
resource "aws_s3_bucket_acl" "dropbox_storage_acl" {
  depends_on = [aws_s3_bucket_ownership_controls.dropbox_storage_ownership]
  bucket     = aws_s3_bucket.dropbox_storage.id
  acl        = "private"
}

# Bucket ownership controls
resource "aws_s3_bucket_ownership_controls" "dropbox_storage_ownership" {
  bucket = aws_s3_bucket.dropbox_storage.id

  rule {
    object_ownership = "BucketOwnerPreferred"
  }
}

# Example of additional buckets that might be created for the DropBox application
# All following the same security pattern

resource "aws_s3_bucket" "dropbox_backups" {
  bucket = "${var.bucket_name}-backups"
  tags   = merge(local.common_tags, { Purpose = "Backup" })
}

resource "aws_s3_bucket_public_access_block" "dropbox_backups_pab" {
  bucket                  = aws_s3_bucket.dropbox_backups.id
  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket" "dropbox_logs" {
  bucket = "${var.bucket_name}-logs"
  tags   = merge(local.common_tags, { Purpose = "Logging" })
}

resource "aws_s3_bucket_public_access_block" "dropbox_logs_pab" {
  bucket                  = aws_s3_bucket.dropbox_logs.id
  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = true
  restrict_public_buckets = true
}