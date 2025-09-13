# DropBox S3 Storage Infrastructure - Secure Configuration
# Addresses security issue: S3 buckets should block public write access

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

# S3 bucket for DropBox file storage with security hardening
resource "aws_s3_bucket" "dropbox_storage" {
  bucket = var.bucket_name

  tags = {
    Name        = "DropBox File Storage"
    Environment = var.environment
    Project     = "DropBox"
    Security    = "Hardened"
  }
}

# CRITICAL SECURITY FIX: Block all public access to prevent public write access
resource "aws_s3_bucket_public_access_block" "dropbox_storage_pab" {
  bucket = aws_s3_bucket.dropbox_storage.id

  # Block public ACLs - prevents new public ACLs and uploads with public ACLs
  block_public_acls = true
  
  # Block public bucket policies - prevents new public bucket policies
  block_public_policy = true
  
  # Ignore public ACLs - ignores existing public ACLs
  ignore_public_acls = true
  
  # Restrict public buckets - restricts cross-account access via bucket policies
  restrict_public_buckets = true
}

# Bucket policy to explicitly deny public write operations
resource "aws_s3_bucket_policy" "dropbox_storage_policy" {
  bucket = aws_s3_bucket.dropbox_storage.id
  
  # Wait for public access block to be applied first
  depends_on = [aws_s3_bucket_public_access_block.dropbox_storage_pab]

  policy = jsonencode({
    Version = "2012-10-17"
    Id      = "DenyPublicWriteAccess"
    Statement = [
      {
        Sid       = "DenyPublicWriteOperations"
        Effect    = "Deny"
        Principal = "*"
        Action = [
          "s3:PutObject",
          "s3:PutObjectAcl",
          "s3:DeleteObject",
          "s3:DeleteObjectVersion",
          "s3:RestoreObject"
        ]
        Resource = "${aws_s3_bucket.dropbox_storage.arn}/*"
        Condition = {
          Bool = {
            "aws:PrincipalIsAWSService" = "false"
          }
        }
      },
      {
        Sid       = "DenyPublicBucketOperations"
        Effect    = "Deny"
        Principal = "*"
        Action = [
          "s3:PutBucketAcl",
          "s3:PutBucketPolicy",
          "s3:DeleteBucket",
          "s3:PutBucketVersioning",
          "s3:PutLifecycleConfiguration"
        ]
        Resource = aws_s3_bucket.dropbox_storage.arn
        Condition = {
          Bool = {
            "aws:PrincipalIsAWSService" = "false"
          }
        }
      }
    ]
  })
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

# Lifecycle configuration to manage costs
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
      storage_class = "GLACIER_FLEXIBLE_RETRIEVAL"
    }

    noncurrent_version_expiration {
      noncurrent_days = 90
    }
  }
}