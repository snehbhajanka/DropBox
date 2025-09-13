# Terraform configuration for secure S3 buckets
# This configuration implements the security recommendations from the issue

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

# S3 bucket for the DropBox application
resource "aws_s3_bucket" "dropbox_storage" {
  bucket = var.bucket_name

  tags = {
    Name        = "DropBox Storage"
    Environment = var.environment
    Security    = "BlockPublicAccess"
  }
}

# Block all public access to the S3 bucket - CRITICAL SECURITY SETTING
resource "aws_s3_bucket_public_access_block" "dropbox_storage_pab" {
  bucket = aws_s3_bucket.dropbox_storage.id

  # Block public ACLs
  block_public_acls = true
  
  # Ignore public ACLs
  ignore_public_acls = true
  
  # Block public bucket policies
  block_public_policy = true
  
  # Restrict public buckets
  restrict_public_buckets = true
}

# Ensure bucket ACL is private
resource "aws_s3_bucket_acl" "dropbox_storage_acl" {
  depends_on = [aws_s3_bucket_ownership_controls.dropbox_storage_ownership]
  
  bucket = aws_s3_bucket.dropbox_storage.id
  acl    = "private"
}

# Set bucket ownership controls
resource "aws_s3_bucket_ownership_controls" "dropbox_storage_ownership" {
  bucket = aws_s3_bucket.dropbox_storage.id

  rule {
    object_ownership = "BucketOwnerPreferred"
  }
}

# Enable versioning for data protection
resource "aws_s3_bucket_versioning" "dropbox_storage_versioning" {
  bucket = aws_s3_bucket.dropbox_storage.id
  
  versioning_configuration {
    status = "Enabled"
  }
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