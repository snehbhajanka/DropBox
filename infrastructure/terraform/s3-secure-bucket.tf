# Terraform configuration for secure S3 buckets
# This configuration ensures S3 buckets block public write access

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

variable "aws_region" {
  description = "AWS region for S3 bucket"
  type        = string
  default     = "us-east-1"
}

variable "bucket_name" {
  description = "Name of the S3 bucket"
  type        = string
}

variable "environment" {
  description = "Environment (dev, staging, prod)"
  type        = string
  default     = "dev"
}

# S3 Bucket with secure configuration
resource "aws_s3_bucket" "dropbox_storage" {
  bucket = var.bucket_name

  tags = {
    Name        = "DropBox Storage"
    Environment = var.environment
    Purpose     = "File Storage"
  }
}

# Block all public access - CRITICAL SECURITY SETTING
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

# Bucket ACL - private only
resource "aws_s3_bucket_acl" "dropbox_storage_acl" {
  bucket = aws_s3_bucket.dropbox_storage.id
  acl    = "private"
  
  depends_on = [aws_s3_bucket_ownership_controls.s3_bucket_acl_ownership]
}

# Bucket ownership controls
resource "aws_s3_bucket_ownership_controls" "s3_bucket_acl_ownership" {
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

# Server-side encryption
resource "aws_s3_bucket_server_side_encryption_configuration" "dropbox_storage_encryption" {
  bucket = aws_s3_bucket.dropbox_storage.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Outputs
output "bucket_name" {
  description = "Name of the created S3 bucket"
  value       = aws_s3_bucket.dropbox_storage.id
}

output "bucket_arn" {
  description = "ARN of the created S3 bucket"
  value       = aws_s3_bucket.dropbox_storage.arn
}

output "bucket_domain_name" {
  description = "Domain name of the S3 bucket"
  value       = aws_s3_bucket.dropbox_storage.bucket_domain_name
}