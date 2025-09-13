# Secure S3 bucket configuration with all public access blocked
# This configuration addresses the security vulnerability by ensuring
# no public write access is possible on S3 buckets

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

# S3 bucket with secure configuration
resource "aws_s3_bucket" "dropbox_storage" {
  bucket = var.bucket_name

  tags = {
    Name        = "DropBox Storage"
    Environment = var.environment
    Security    = "HighSecurity"
    Purpose     = "FileStorage"
  }
}

# Block ALL public access to the S3 bucket
# This addresses the security vulnerability mentioned in the issue
resource "aws_s3_bucket_public_access_block" "dropbox_storage_pab" {
  bucket = aws_s3_bucket.dropbox_storage.id

  # Block public ACLs - prevents new public ACLs and existing ones from being applied
  block_public_acls = true
  
  # Ignore public ACLs - ignores all public ACLs on bucket and objects
  ignore_public_acls = true
  
  # Block public bucket policies - blocks public access granted by bucket policies
  block_public_policy = true
  
  # Restrict public buckets - restricts access to buckets with public policies
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

# Bucket policy to further restrict access (optional additional security)
resource "aws_s3_bucket_policy" "dropbox_storage_policy" {
  bucket = aws_s3_bucket.dropbox_storage.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "DenyDirectPublicAccess"
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
              "cloudformation.amazonaws.com",
              "ec2.amazonaws.com"
            ]
          }
          Bool = {
            "aws:ViaAWSService" = "false"
          }
        }
      }
    ]
  })

  depends_on = [aws_s3_bucket_public_access_block.dropbox_storage_pab]
}