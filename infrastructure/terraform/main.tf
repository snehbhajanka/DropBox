# Main Terraform configuration for DropBox S3 Infrastructure
# This configuration ensures S3 buckets are secured against public write access
# Addresses AWS Security Hub Control ID: S3.3

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

# S3 bucket for file storage with secure configuration
resource "aws_s3_bucket" "dropbox_files" {
  bucket = var.bucket_name

  tags = {
    Name        = "DropBox File Storage"
    Environment = var.environment
    Project     = "DropBox"
    Security    = "Secured"
  }
}

# Block all public access to the S3 bucket
# This addresses the S3.3 control requirement
resource "aws_s3_bucket_public_access_block" "dropbox_files_pab" {
  bucket = aws_s3_bucket.dropbox_files.id

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
resource "aws_s3_bucket_versioning" "dropbox_files_versioning" {
  bucket = aws_s3_bucket.dropbox_files.id
  versioning_configuration {
    status = "Enabled"
  }
}

# Server-side encryption configuration
resource "aws_s3_bucket_server_side_encryption_configuration" "dropbox_files_encryption" {
  bucket = aws_s3_bucket.dropbox_files.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
    bucket_key_enabled = true
  }
}

# Bucket policy to explicitly deny public access and enforce secure access
resource "aws_s3_bucket_policy" "dropbox_files_policy" {
  bucket = aws_s3_bucket.dropbox_files.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "DenyPublicAccess"
        Effect    = "Deny"
        Principal = "*"
        Action    = "s3:*"
        Resource = [
          aws_s3_bucket.dropbox_files.arn,
          "${aws_s3_bucket.dropbox_files.arn}/*"
        ]
        Condition = {
          Bool = {
            "aws:SecureTransport" = "false"
          }
        }
      },
      {
        Sid       = "DenyPublicWrite"
        Effect    = "Deny"
        Principal = "*"
        Action = [
          "s3:PutObject",
          "s3:PutObjectAcl",
          "s3:DeleteObject",
          "s3:PutBucketAcl"
        ]
        Resource = [
          aws_s3_bucket.dropbox_files.arn,
          "${aws_s3_bucket.dropbox_files.arn}/*"
        ]
        Condition = {
          StringNotEquals = {
            "aws:PrincipalServiceName" = [
              "ec2.amazonaws.com",
              "lambda.amazonaws.com"
            ]
          }
        }
      }
    ]
  })

  depends_on = [aws_s3_bucket_public_access_block.dropbox_files_pab]
}

# IAM role for DropBox application to access S3
resource "aws_iam_role" "dropbox_app_role" {
  name = "dropbox-app-role-${var.environment}"

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
    Project     = "DropBox"
  }
}

# IAM policy for S3 access with least privilege
resource "aws_iam_role_policy" "dropbox_s3_policy" {
  name = "dropbox-s3-access-policy"
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
          aws_s3_bucket.dropbox_files.arn,
          "${aws_s3_bucket.dropbox_files.arn}/*"
        ]
      }
    ]
  })
}

# Instance profile for EC2 instances
resource "aws_iam_instance_profile" "dropbox_profile" {
  name = "dropbox-instance-profile-${var.environment}"
  role = aws_iam_role.dropbox_app_role.name
}