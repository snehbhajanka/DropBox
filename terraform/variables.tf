# Variables for Terraform configuration

variable "aws_region" {
  description = "AWS region for resources"
  type        = string
  default     = "us-east-1"
}

variable "bucket_name" {
  description = "Name of the S3 bucket for DropBox application"
  type        = string
  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9-]*[a-z0-9]$", var.bucket_name))
    error_message = "Bucket name must follow S3 naming conventions."
  }
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = "dev"
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be one of: dev, staging, prod."
  }
}

variable "application_role_arn" {
  description = "ARN of the IAM role/user that the application will use to access S3"
  type        = string
  validation {
    condition     = can(regex("^arn:aws:iam::", var.application_role_arn))
    error_message = "Application role ARN must be a valid IAM ARN."
  }
}

variable "enable_versioning" {
  description = "Enable S3 bucket versioning"
  type        = bool
  default     = true
}

variable "enable_encryption" {
  description = "Enable S3 bucket server-side encryption"
  type        = bool
  default     = true
}

variable "lifecycle_transition_ia_days" {
  description = "Number of days after which objects transition to IA storage class"
  type        = number
  default     = 30
}

variable "lifecycle_transition_glacier_days" {
  description = "Number of days after which objects transition to Glacier storage class"
  type        = number
  default     = 90
}

variable "lifecycle_transition_deep_archive_days" {
  description = "Number of days after which objects transition to Deep Archive storage class"
  type        = number
  default     = 365
}