# Terraform configuration for secure S3 bucket
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
  description = "AWS region for resources"
  type        = string
  default     = "us-east-2"
}

variable "bucket_name" {
  description = "Name of the S3 bucket"
  type        = string
  default     = "dropbox-secure-bucket"
}

# S3 bucket with security best practices
resource "aws_s3_bucket" "dropbox_bucket" {
  bucket = var.bucket_name

  tags = {
    Name        = "DropBox Secure Storage"
    Environment = "production"
    Security    = "high"
  }
}

# Block all public access to prevent security misconfiguration
resource "aws_s3_bucket_public_access_block" "dropbox_bucket_pab" {
  bucket = aws_s3_bucket.dropbox_bucket.id

  # Block all public access - this addresses the security issue
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Enable versioning for data protection
resource "aws_s3_bucket_versioning" "dropbox_bucket_versioning" {
  bucket = aws_s3_bucket.dropbox_bucket.id
  versioning_configuration {
    status = "Enabled"
  }
}

# Enable server-side encryption
resource "aws_s3_bucket_server_side_encryption_configuration" "dropbox_bucket_encryption" {
  bucket = aws_s3_bucket.dropbox_bucket.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
    bucket_key_enabled = true
  }
}

# Bucket policy to explicitly deny public write access
resource "aws_s3_bucket_policy" "dropbox_bucket_policy" {
  bucket = aws_s3_bucket.dropbox_bucket.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "DenyPublicWriteAccess"
        Effect    = "Deny"
        Principal = "*"
        Action = [
          "s3:PutObject",
          "s3:PutObjectAcl",
          "s3:DeleteObject",
          "s3:DeleteObjectVersion"
        ]
        Resource = "${aws_s3_bucket.dropbox_bucket.arn}/*"
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
        Sid       = "DenyPublicReadAccess"
        Effect    = "Deny"
        Principal = "*"
        Action = [
          "s3:GetObject",
          "s3:GetObjectVersion"
        ]
        Resource = "${aws_s3_bucket.dropbox_bucket.arn}/*"
        Condition = {
          Bool = {
            "aws:SecureTransport" = "false"
          }
        }
      }
    ]
  })

  depends_on = [aws_s3_bucket_public_access_block.dropbox_bucket_pab]
}

# Enable logging for security monitoring
resource "aws_s3_bucket_logging" "dropbox_bucket_logging" {
  bucket = aws_s3_bucket.dropbox_bucket.id

  target_bucket = aws_s3_bucket.dropbox_access_logs.id
  target_prefix = "access-logs/"
}

# Separate bucket for access logs
resource "aws_s3_bucket" "dropbox_access_logs" {
  bucket = "${var.bucket_name}-access-logs"

  tags = {
    Name        = "DropBox Access Logs"
    Environment = "production"
    Purpose     = "logging"
  }
}

# Block public access for logs bucket
resource "aws_s3_bucket_public_access_block" "dropbox_logs_pab" {
  bucket = aws_s3_bucket.dropbox_access_logs.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Lifecycle configuration to manage costs
resource "aws_s3_bucket_lifecycle_configuration" "dropbox_bucket_lifecycle" {
  bucket = aws_s3_bucket.dropbox_bucket.id

  rule {
    id     = "delete_old_versions"
    status = "Enabled"

    noncurrent_version_expiration {
      noncurrent_days = 90
    }
  }

  rule {
    id     = "transition_to_ia"
    status = "Enabled"

    transition {
      days          = 30
      storage_class = "STANDARD_IA"
    }

    transition {
      days          = 60
      storage_class = "GLACIER"
    }
  }
}

# Outputs
output "bucket_name" {
  description = "Name of the created S3 bucket"
  value       = aws_s3_bucket.dropbox_bucket.id
}

output "bucket_arn" {
  description = "ARN of the created S3 bucket"
  value       = aws_s3_bucket.dropbox_bucket.arn
}

output "bucket_domain_name" {
  description = "Bucket domain name"
  value       = aws_s3_bucket.dropbox_bucket.bucket_domain_name
}