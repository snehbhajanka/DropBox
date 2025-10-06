# AWS S3 Bucket Configuration with Security Best Practices
# This configuration addresses the security finding: "S3 general purpose buckets should block public write access"

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
    Name        = "DropBox Storage"
    Environment = var.environment
    Purpose     = "File storage for DropBox application"
  }
}

# Block all public access to the S3 bucket
resource "aws_s3_bucket_public_access_block" "dropbox_storage_pab" {
  bucket = aws_s3_bucket.dropbox_storage.id

  # Block public ACLs
  block_public_acls = true
  
  # Block public bucket policies
  block_public_policy = true
  
  # Ignore public ACLs
  ignore_public_acls = true
  
  # Restrict public bucket policies
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

# Lifecycle configuration to manage storage costs
resource "aws_s3_bucket_lifecycle_configuration" "dropbox_storage_lifecycle" {
  bucket = aws_s3_bucket.dropbox_storage.id

  rule {
    id     = "transition_to_ia"
    status = "Enabled"

    transition {
      days          = 30
      storage_class = "STANDARD_INFREQUENT_ACCESS"
    }

    transition {
      days          = 90
      storage_class = "GLACIER"
    }

    expiration {
      days = 365
    }

    noncurrent_version_expiration {
      noncurrent_days = 30
    }
  }
}

# IAM role for application to access S3 bucket
resource "aws_iam_role" "dropbox_app_role" {
  name = "${var.bucket_name}-app-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Name        = "DropBox Application Role"
    Environment = var.environment
  }
}

# IAM policy for S3 access (read/write only, no public access)
resource "aws_iam_role_policy" "dropbox_s3_access" {
  name = "${var.bucket_name}-s3-access"
  role = aws_iam_role.dropbox_app_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
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
}

# Instance profile for EC2 instances
resource "aws_iam_instance_profile" "dropbox_app_profile" {
  name = "${var.bucket_name}-app-profile"
  role = aws_iam_role.dropbox_app_role.name
}