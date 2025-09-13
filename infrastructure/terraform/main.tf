# Terraform configuration for secure S3 buckets
# This configuration implements all required security controls for S3.3 compliance

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

# S3 Bucket for DropBox application file storage
resource "aws_s3_bucket" "dropbox_storage" {
  bucket = "${var.bucket_prefix}-dropbox-storage"

  tags = {
    Environment = var.environment
    Project     = "DropBox"
    Purpose     = "File Storage"
    Security    = "S3.3-Compliant"
  }
}

# S3 Bucket versioning
resource "aws_s3_bucket_versioning" "dropbox_storage_versioning" {
  bucket = aws_s3_bucket.dropbox_storage.id
  versioning_configuration {
    status = "Enabled"
  }
}

# S3 Bucket encryption
resource "aws_s3_bucket_server_side_encryption_configuration" "dropbox_storage_encryption" {
  bucket = aws_s3_bucket.dropbox_storage.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
    bucket_key_enabled = true
  }
}

# S3 Bucket Public Access Block - CRITICAL SECURITY CONTROL
# This is the primary security control addressing the S3.3 compliance requirement
resource "aws_s3_bucket_public_access_block" "dropbox_storage_pab" {
  bucket = aws_s3_bucket.dropbox_storage.id

  # Block public ACLs - prevents new public ACLs and uploads with public ACLs
  block_public_acls = true

  # Ignore public ACLs - ignores all public ACLs on bucket and objects
  ignore_public_acls = true

  # Block public policy - blocks putting bucket policies that allow public access
  block_public_policy = true

  # Restrict public buckets - restricts access to buckets with public policies
  restrict_public_buckets = true
}

# S3 Bucket lifecycle configuration
resource "aws_s3_bucket_lifecycle_configuration" "dropbox_storage_lifecycle" {
  bucket = aws_s3_bucket.dropbox_storage.id

  rule {
    id     = "file_lifecycle"
    status = "Enabled"

    # Move to IA after 30 days
    transition {
      days          = 30
      storage_class = "STANDARD_IA"
    }

    # Move to Glacier after 90 days
    transition {
      days          = 90
      storage_class = "GLACIER"
    }

    # Delete after 2 years
    expiration {
      days = 730
    }
  }
}

# Additional bucket for temporary uploads (also secured)
resource "aws_s3_bucket" "dropbox_temp_uploads" {
  bucket = "${var.bucket_prefix}-dropbox-temp-uploads"

  tags = {
    Environment = var.environment
    Project     = "DropBox"
    Purpose     = "Temporary Upload Storage"
    Security    = "S3.3-Compliant"
  }
}

# Public Access Block for temp uploads bucket
resource "aws_s3_bucket_public_access_block" "dropbox_temp_uploads_pab" {
  bucket = aws_s3_bucket.dropbox_temp_uploads.id

  # Same critical security settings
  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = true
  restrict_public_buckets = true
}

# Lifecycle for temp uploads - shorter retention
resource "aws_s3_bucket_lifecycle_configuration" "dropbox_temp_uploads_lifecycle" {
  bucket = aws_s3_bucket.dropbox_temp_uploads.id

  rule {
    id     = "temp_cleanup"
    status = "Enabled"

    # Delete temporary files after 7 days
    expiration {
      days = 7
    }

    # Clean up incomplete multipart uploads after 1 day
    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }
}

# Encryption for temp uploads
resource "aws_s3_bucket_server_side_encryption_configuration" "dropbox_temp_uploads_encryption" {
  bucket = aws_s3_bucket.dropbox_temp_uploads.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
    bucket_key_enabled = true
  }
}