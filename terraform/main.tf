# Terraform configuration for secure S3 bucket
# This addresses the security issue: [SECURITY] Action Required: Block Public Write Access - S3 Buckets

terraform {
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
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}

variable "bucket_name" {
  description = "S3 bucket name for secure dropbox storage"
  type        = string
  default     = "secure-dropbox-storage"
}

# S3 Bucket with secure configuration
resource "aws_s3_bucket" "secure_dropbox_bucket" {
  bucket = var.bucket_name

  tags = {
    Name        = "Secure DropBox Storage"
    Environment = "Production"
    Security    = "HighSecurity"
    Purpose     = "File Storage with Blocked Public Access"
  }
}

# Block all public access to the S3 bucket - This is the main security fix
resource "aws_s3_bucket_public_access_block" "secure_dropbox_bucket_pab" {
  bucket = aws_s3_bucket.secure_dropbox_bucket.id

  # Block public ACLs - prevents new public ACLs and uploading objects with public ACLs
  block_public_acls = true

  # Ignore public ACLs - ignore all public ACLs on bucket and objects
  ignore_public_acls = true

  # Block public bucket policies - reject calls to PUT bucket policy if it would grant public access
  block_public_policy = true

  # Restrict public buckets - restrict access to bucket with public policies
  restrict_public_buckets = true
}

# Server-side encryption configuration
resource "aws_s3_bucket_server_side_encryption_configuration" "secure_dropbox_bucket_encryption" {
  bucket = aws_s3_bucket.secure_dropbox_bucket.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Versioning configuration
resource "aws_s3_bucket_versioning" "secure_dropbox_bucket_versioning" {
  bucket = aws_s3_bucket.secure_dropbox_bucket.id
  versioning_configuration {
    status = "Enabled"
  }
}

# Block public read access by default
resource "aws_s3_bucket_acl" "secure_dropbox_bucket_acl" {
  depends_on = [aws_s3_bucket_ownership_controls.secure_dropbox_bucket_acl_ownership]

  bucket = aws_s3_bucket.secure_dropbox_bucket.id
  acl    = "private"
}

resource "aws_s3_bucket_ownership_controls" "secure_dropbox_bucket_acl_ownership" {
  bucket = aws_s3_bucket.secure_dropbox_bucket.id

  rule {
    object_ownership = "BucketOwnerPreferred"
  }
}

# Output the bucket name for reference
output "bucket_name" {
  description = "Name of the secure S3 bucket"
  value       = aws_s3_bucket.secure_dropbox_bucket.id
}

output "bucket_arn" {
  description = "ARN of the secure S3 bucket"
  value       = aws_s3_bucket.secure_dropbox_bucket.arn
}

output "public_access_block_status" {
  description = "Public access block configuration status"
  value = {
    block_public_acls       = aws_s3_bucket_public_access_block.secure_dropbox_bucket_pab.block_public_acls
    ignore_public_acls      = aws_s3_bucket_public_access_block.secure_dropbox_bucket_pab.ignore_public_acls
    block_public_policy     = aws_s3_bucket_public_access_block.secure_dropbox_bucket_pab.block_public_policy
    restrict_public_buckets = aws_s3_bucket_public_access_block.secure_dropbox_bucket_pab.restrict_public_buckets
  }
}