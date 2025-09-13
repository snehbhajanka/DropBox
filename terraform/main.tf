# Terraform configuration for DropBox application S3 buckets
# This configuration addresses the security requirement to block public write access

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

# S3 bucket for DropBox application file storage
resource "aws_s3_bucket" "dropbox_storage" {
  bucket = var.bucket_name

  tags = {
    Name        = "DropBox File Storage"
    Environment = var.environment
    Purpose     = "File Storage"
    Security    = "PublicAccessBlocked"
  }
}

# Block all public access to the S3 bucket
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

# Bucket versioning
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
    bucket_key_enabled = true
  }
}

# Bucket policy to ensure secure access
resource "aws_s3_bucket_policy" "dropbox_storage_policy" {
  bucket = aws_s3_bucket.dropbox_storage.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "DenyPublicWrite"
        Effect = "Deny"
        Principal = "*"
        Action = [
          "s3:PutObject",
          "s3:PutObjectAcl",
          "s3:DeleteObject"
        ]
        Resource = "${aws_s3_bucket.dropbox_storage.arn}/*"
        Condition = {
          StringNotEquals = {
            "aws:PrincipalServiceName" = [
              "ec2.amazonaws.com",
              "lambda.amazonaws.com"
            ]
          }
        }
      },
      {
        Sid    = "AllowApplicationAccess"
        Effect = "Allow"
        Principal = {
          AWS = var.application_role_arn
        }
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject",
          "s3:ListBucket"
        ]
        Resource = [
          aws_s3_bucket.dropbox_storage.arn,
          "${aws_s3_bucket.dropbox_storage.arn}/*"
        ]
      }
    ]
  })

  depends_on = [aws_s3_bucket_public_access_block.dropbox_storage_pab]
}

# Bucket notification configuration (optional)
resource "aws_s3_bucket_notification" "dropbox_storage_notification" {
  bucket = aws_s3_bucket.dropbox_storage.id
  
  # This can be extended to include SNS/SQS notifications for file operations
}

# Lifecycle configuration for cost optimization
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

    transition {
      days          = 365
      storage_class = "DEEP_ARCHIVE"
    }
  }

  rule {
    id     = "delete_incomplete_multipart_uploads"
    status = "Enabled"

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}