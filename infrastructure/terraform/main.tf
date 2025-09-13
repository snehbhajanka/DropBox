# AWS Provider configuration
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

# S3 Bucket for DropBox application with secure configuration
resource "aws_s3_bucket" "dropbox_storage" {
  bucket = var.bucket_name

  tags = {
    Name        = "DropBox Application Storage"
    Environment = var.environment
    Purpose     = "File Storage"
    Security    = "High"
  }
}

# Block ALL public access to the bucket
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

# Bucket versioning for data protection
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

# Bucket policy to deny public access and unauthorized write operations
resource "aws_s3_bucket_policy" "dropbox_storage_policy" {
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
            "aws:PrincipalServiceName" = [
              "ec2.amazonaws.com",
              "lambda.amazonaws.com"
            ]
          }
          StringNotLike = {
            "aws:PrincipalArn" = [
              "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/DropBox-*",
              "arn:aws:iam::${data.aws_caller_identity.current.account_id}:user/DropBox-*"
            ]
          }
        }
      },
      {
        Sid    = "DenyInsecureConnections"
        Effect = "Deny"
        Principal = "*"
        Action = "s3:*"
        Resource = [
          aws_s3_bucket.dropbox_storage.arn,
          "${aws_s3_bucket.dropbox_storage.arn}/*"
        ]
        Condition = {
          Bool = {
            "aws:SecureTransport" = "false"
          }
        }
      }
    ]
  })

  depends_on = [aws_s3_bucket_public_access_block.dropbox_storage_pab]
}

# Get current AWS account ID
data "aws_caller_identity" "current" {}

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
    Purpose     = "Access Logging"
  }
}

# Block public access for logs bucket too
resource "aws_s3_bucket_public_access_block" "dropbox_access_logs_pab" {
  bucket = aws_s3_bucket.dropbox_access_logs.id

  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = true
  restrict_public_buckets = true
}

# Lifecycle configuration to manage costs
resource "aws_s3_bucket_lifecycle_configuration" "dropbox_storage_lifecycle" {
  bucket = aws_s3_bucket.dropbox_storage.id

  rule {
    id     = "manage_storage_classes"
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
      noncurrent_days = 365
    }
  }
}