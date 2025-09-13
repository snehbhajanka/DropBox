# Terraform configuration for secure S3 buckets
# This addresses the S3.3 security misconfiguration - Block Public Write Access

terraform {
  required_version = ">= 1.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

# Variables
variable "aws_region" {
  description = "AWS region for resources"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "production"
}

variable "bucket_prefix" {
  description = "Prefix for S3 bucket names"
  type        = string
  default     = "dropbox-secure"
}

# Data source for current AWS account
data "aws_caller_identity" "current" {}

# S3 bucket for file storage with secure configuration
resource "aws_s3_bucket" "dropbox_storage" {
  bucket = "${var.bucket_prefix}-storage-${var.environment}-${random_id.bucket_suffix.hex}"

  tags = {
    Name        = "DropBox Storage Bucket"
    Environment = var.environment
    Purpose     = "File storage with security controls"
  }
}

# Random ID for bucket naming uniqueness
resource "random_id" "bucket_suffix" {
  byte_length = 4
}

# CRITICAL SECURITY: Block all public access to prevent S3.3 misconfiguration
resource "aws_s3_bucket_public_access_block" "dropbox_storage" {
  bucket = aws_s3_bucket.dropbox_storage.id

  # Block public ACLs - prevents setting public ACLs on bucket/objects
  block_public_acls = true

  # Ignore public ACLs - treats public ACLs as non-public
  ignore_public_acls = true

  # Block public bucket policies - prevents setting public bucket policies
  block_public_policy = true

  # Restrict public buckets - restricts cross-account access to buckets with public policies
  restrict_public_buckets = true
}

# Bucket versioning for data protection
resource "aws_s3_bucket_versioning" "dropbox_storage" {
  bucket = aws_s3_bucket.dropbox_storage.id
  versioning_configuration {
    status = "Enabled"
  }
}

# Server-side encryption
resource "aws_s3_bucket_server_side_encryption_configuration" "dropbox_storage" {
  bucket = aws_s3_bucket.dropbox_storage.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
    bucket_key_enabled = true
  }
}

# Lifecycle configuration to manage storage costs
resource "aws_s3_bucket_lifecycle_configuration" "dropbox_storage" {
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

    expiration {
      days = 365
    }
  }
}

# Bucket policy - explicitly deny public access
resource "aws_s3_bucket_policy" "dropbox_storage" {
  bucket = aws_s3_bucket.dropbox_storage.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "DenyPublicAccess"
        Effect    = "Deny"
        Principal = "*"
        Action    = "s3:*"
        Resource = [
          aws_s3_bucket.dropbox_storage.arn,
          "${aws_s3_bucket.dropbox_storage.arn}/*"
        ]
        Condition = {
          StringNotEquals = {
            "aws:PrincipalAccount" = data.aws_caller_identity.current.account_id
          }
        }
      }
    ]
  })

  depends_on = [aws_s3_bucket_public_access_block.dropbox_storage]
}

# Additional bucket for backup/archival
resource "aws_s3_bucket" "dropbox_backup" {
  bucket = "${var.bucket_prefix}-backup-${var.environment}-${random_id.backup_suffix.hex}"

  tags = {
    Name        = "DropBox Backup Bucket"
    Environment = var.environment
    Purpose     = "Backup and archival with security controls"
  }
}

resource "random_id" "backup_suffix" {
  byte_length = 4
}

# CRITICAL SECURITY: Block all public access for backup bucket
resource "aws_s3_bucket_public_access_block" "dropbox_backup" {
  bucket = aws_s3_bucket.dropbox_backup.id

  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = true
  restrict_public_buckets = true
}

# Encryption for backup bucket
resource "aws_s3_bucket_server_side_encryption_configuration" "dropbox_backup" {
  bucket = aws_s3_bucket.dropbox_backup.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
    bucket_key_enabled = true
  }
}

# Outputs
output "storage_bucket_name" {
  description = "Name of the main storage bucket"
  value       = aws_s3_bucket.dropbox_storage.bucket
}

output "backup_bucket_name" {
  description = "Name of the backup bucket"
  value       = aws_s3_bucket.dropbox_backup.bucket
}

output "storage_bucket_arn" {
  description = "ARN of the main storage bucket"
  value       = aws_s3_bucket.dropbox_storage.arn
}

output "backup_bucket_arn" {
  description = "ARN of the backup bucket"
  value       = aws_s3_bucket.dropbox_backup.arn
}