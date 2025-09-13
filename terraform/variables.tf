# Variables for the DropBox S3 infrastructure

variable "aws_region" {
  description = "AWS region for the S3 buckets"
  type        = string
  default     = "us-east-1"
}

variable "bucket_count" {
  description = "Number of S3 buckets to create for the DropBox application"
  type        = number
  default     = 13
}

variable "bucket_prefix" {
  description = "Prefix for S3 bucket names"
  type        = string
  default     = "dropbox-storage"
}

variable "environment" {
  description = "Environment name (e.g., dev, staging, prod)"
  type        = string
  default     = "prod"
}

variable "enable_versioning" {
  description = "Enable versioning on S3 buckets"
  type        = bool
  default     = true
}