# Variables for Terraform configuration

variable "aws_region" {
  description = "AWS region for resources"
  type        = string
  default     = "us-east-1"
}

variable "bucket_name" {
  description = "Name of the S3 bucket for DropBox storage"
  type        = string
  default     = "dropbox-secure-storage"
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "production"
}