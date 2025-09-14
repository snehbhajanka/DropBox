# S3 Bucket Security Configuration
# This configuration ensures S3 buckets are secure and block public write access
# as required by security misconfiguration S3.3

terraform {
  required_version = ">= 1.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# Variables for bucket configuration
variable "bucket_names" {
  description = "List of S3 bucket names to secure"
  type        = list(string)
  default     = [
    "dropbox-app-storage-bucket-1",
    "dropbox-app-storage-bucket-2",
    "dropbox-app-storage-bucket-3",
    "dropbox-app-backup-bucket-1",
    "dropbox-app-backup-bucket-2",
    "dropbox-app-logs-bucket-1",
    "dropbox-app-logs-bucket-2",
    "dropbox-app-temp-bucket-1",
    "dropbox-app-temp-bucket-2",
    "dropbox-app-uploads-bucket-1",
    "dropbox-app-uploads-bucket-2",
    "dropbox-app-config-bucket-1",
    "dropbox-app-archive-bucket-1"
  ]
}

variable "environment" {
  description = "Environment (dev, staging, prod)"
  type        = string
  default     = "prod"
}

# S3 Buckets with secure configuration
resource "aws_s3_bucket" "secure_buckets" {
  count  = length(var.bucket_names)
  bucket = var.bucket_names[count.index]

  tags = {
    Name        = var.bucket_names[count.index]
    Environment = var.environment
    Purpose     = "DropBox Application Storage"
    Security    = "Hardened"
    Compliance  = "S3.3-Remediated"
  }
}

# Block all public access for each bucket
resource "aws_s3_bucket_public_access_block" "secure_bucket_pab" {
  count  = length(var.bucket_names)
  bucket = aws_s3_bucket.secure_buckets[count.index].id

  # Block public ACLs - prevents public read/write access via ACLs
  block_public_acls = true

  # Ignore public ACLs - ignores all public ACLs on this bucket and objects
  ignore_public_acls = true

  # Block public bucket policies - prevents public access via bucket policies
  block_public_policy = true

  # Restrict public buckets - restricts access to buckets with public policies
  restrict_public_buckets = true
}

# Set bucket ACL to private (additional security layer)
resource "aws_s3_bucket_acl" "secure_bucket_acl" {
  count      = length(var.bucket_names)
  bucket     = aws_s3_bucket.secure_buckets[count.index].id
  acl        = "private"
  depends_on = [aws_s3_bucket_ownership_controls.s3_bucket_acl_ownership]
}

# Bucket ownership controls
resource "aws_s3_bucket_ownership_controls" "s3_bucket_acl_ownership" {
  count  = length(var.bucket_names)
  bucket = aws_s3_bucket.secure_buckets[count.index].id

  rule {
    object_ownership = "BucketOwnerPreferred"
  }
}

# Enable versioning for data protection
resource "aws_s3_bucket_versioning" "secure_bucket_versioning" {
  count  = length(var.bucket_names)
  bucket = aws_s3_bucket.secure_buckets[count.index].id

  versioning_configuration {
    status = "Enabled"
  }
}

# Enable server-side encryption
resource "aws_s3_bucket_server_side_encryption_configuration" "secure_bucket_encryption" {
  count  = length(var.bucket_names)
  bucket = aws_s3_bucket.secure_buckets[count.index].id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
    bucket_key_enabled = true
  }
}

# Outputs for verification
output "secure_bucket_names" {
  description = "Names of the secured S3 buckets"
  value       = aws_s3_bucket.secure_buckets[*].bucket
}

output "secure_bucket_arns" {
  description = "ARNs of the secured S3 buckets"
  value       = aws_s3_bucket.secure_buckets[*].arn
}

output "public_access_block_status" {
  description = "Status of public access block for all buckets"
  value = {
    for idx, bucket in aws_s3_bucket.secure_buckets :
    bucket.bucket => {
      block_public_acls       = aws_s3_bucket_public_access_block.secure_bucket_pab[idx].block_public_acls
      ignore_public_acls      = aws_s3_bucket_public_access_block.secure_bucket_pab[idx].ignore_public_acls
      block_public_policy     = aws_s3_bucket_public_access_block.secure_bucket_pab[idx].block_public_policy
      restrict_public_buckets = aws_s3_bucket_public_access_block.secure_bucket_pab[idx].restrict_public_buckets
    }
  }
}