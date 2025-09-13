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

# Create S3 bucket with secure configuration
resource "aws_s3_bucket" "dropbox_bucket" {
  bucket = var.bucket_name

  tags = {
    Name        = "DropBox Secure Bucket"
    Environment = var.environment
    Purpose     = "File Storage"
    Security    = "Enhanced"
  }
}

# Block all public access to the bucket
resource "aws_s3_bucket_public_access_block" "dropbox_bucket_pab" {
  bucket = aws_s3_bucket.dropbox_bucket.id

  # Block public ACLs
  block_public_acls = true
  
  # Ignore public ACLs
  ignore_public_acls = true
  
  # Block public bucket policies
  block_public_policy = true
  
  # Restrict public buckets
  restrict_public_buckets = true
}

# Enable versioning for better data protection
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
        Sid       = "DenyPublicWrite"
        Effect    = "Deny"
        Principal = "*"
        Action = [
          "s3:PutObject",
          "s3:PutObjectAcl",
          "s3:DeleteObject",
          "s3:DeleteObjectVersion",
          "s3:RestoreObject"
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
        Sid       = "DenyPublicBucketActions"
        Effect    = "Deny"
        Principal = "*"
        Action = [
          "s3:PutBucketAcl",
          "s3:PutBucketPolicy",
          "s3:DeleteBucket",
          "s3:PutBucketPublicAccessBlock"
        ]
        Resource = aws_s3_bucket.dropbox_bucket.arn
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
          AWS = "arn:aws:iam::${var.account_id}:root"
        }
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject",
          "s3:ListBucket"
        ]
        Resource = [
          aws_s3_bucket.dropbox_bucket.arn,
          "${aws_s3_bucket.dropbox_bucket.arn}/*"
        ]
        Condition = {
          StringEquals = {
            "s3:ExistingObjectTag/Application" = "DropBox"
          }
        }
      }
    ]
  })

  depends_on = [aws_s3_bucket_public_access_block.dropbox_bucket_pab]
}

# Enable bucket logging for audit purposes
resource "aws_s3_bucket_logging" "dropbox_bucket_logging" {
  bucket = aws_s3_bucket.dropbox_bucket.id

  target_bucket = aws_s3_bucket.dropbox_logs_bucket.id
  target_prefix = "access-logs/"
}

# Separate bucket for access logs
resource "aws_s3_bucket" "dropbox_logs_bucket" {
  bucket = "${var.bucket_name}-logs"

  tags = {
    Name        = "DropBox Access Logs"
    Environment = var.environment
    Purpose     = "Access Logging"
  }
}

# Block public access for logs bucket too
resource "aws_s3_bucket_public_access_block" "dropbox_logs_bucket_pab" {
  bucket = aws_s3_bucket.dropbox_logs_bucket.id

  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = true
  restrict_public_buckets = true
}

# Create IAM role for application access
resource "aws_iam_role" "dropbox_app_role" {
  name = "dropbox-app-role"

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
    Name = "DropBox Application Role"
  }
}

# IAM policy for secure S3 access
resource "aws_iam_policy" "dropbox_s3_policy" {
  name        = "dropbox-s3-policy"
  description = "Secure S3 access policy for DropBox application"

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
          aws_s3_bucket.dropbox_bucket.arn,
          "${aws_s3_bucket.dropbox_bucket.arn}/*"
        ]
      }
    ]
  })
}

# Attach policy to role
resource "aws_iam_role_policy_attachment" "dropbox_s3_policy_attachment" {
  role       = aws_iam_role.dropbox_app_role.name
  policy_arn = aws_iam_policy.dropbox_s3_policy.arn
}