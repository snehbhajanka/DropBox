variable "aws_region" {
  description = "AWS region for DropBox infrastructure"
  type        = string
  default     = "us-east-2"
  
  validation {
    condition     = can(regex("^[a-z0-9-]+$", var.aws_region))
    error_message = "AWS region must be a valid region identifier."
  }
}

variable "bucket_name" {
  description = "Name of the S3 bucket for DropBox file storage"
  type        = string
  
  validation {
    condition     = can(regex("^[a-z0-9.-]+$", var.bucket_name))
    error_message = "Bucket name must be a valid S3 bucket name (lowercase letters, numbers, dots, and hyphens only)."
  }
}

variable "environment" {
  description = "Environment name (e.g., dev, staging, prod)"
  type        = string
  default     = "dev"
  
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be one of: dev, staging, prod."
  }
}