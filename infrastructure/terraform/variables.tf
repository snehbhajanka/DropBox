# Variables for Terraform configuration

variable "aws_region" {
  description = "AWS region for resource deployment"
  type        = string
  default     = "us-east-1"
  
  validation {
    condition = can(regex("^[a-z0-9-]+$", var.aws_region))
    error_message = "AWS region must be a valid region identifier."
  }
}

variable "environment" {
  description = "Environment name (e.g., dev, staging, prod)"
  type        = string
  default     = "dev"
  
  validation {
    condition = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be one of: dev, staging, prod."
  }
}

variable "bucket_prefix" {
  description = "Prefix for S3 bucket names to ensure uniqueness"
  type        = string
  
  validation {
    condition = can(regex("^[a-z0-9-]+$", var.bucket_prefix)) && length(var.bucket_prefix) >= 3 && length(var.bucket_prefix) <= 20
    error_message = "Bucket prefix must be 3-20 characters, lowercase letters, numbers, and hyphens only."
  }
}

variable "enable_versioning" {
  description = "Enable S3 bucket versioning"
  type        = bool
  default     = true
}

variable "enable_lifecycle" {
  description = "Enable S3 bucket lifecycle management"
  type        = bool
  default     = true
}

variable "tags" {
  description = "Additional tags to apply to all resources"
  type        = map(string)
  default     = {}
}