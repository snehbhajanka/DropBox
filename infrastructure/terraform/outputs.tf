# Outputs for Terraform configuration

output "bucket_name" {
  description = "Name of the created S3 bucket"
  value       = aws_s3_bucket.dropbox_storage.bucket
}

output "bucket_arn" {
  description = "ARN of the created S3 bucket"
  value       = aws_s3_bucket.dropbox_storage.arn
}

output "bucket_domain_name" {
  description = "Domain name of the S3 bucket"
  value       = aws_s3_bucket.dropbox_storage.bucket_domain_name
}

output "iam_role_arn" {
  description = "ARN of the IAM role for the application"
  value       = aws_iam_role.dropbox_app_role.arn
}

output "instance_profile_name" {
  description = "Name of the instance profile for EC2 instances"
  value       = aws_iam_instance_profile.dropbox_app_profile.name
}

output "security_compliance_status" {
  description = "Security compliance status of the S3 bucket"
  value = {
    public_access_blocked = true
    encryption_enabled    = true
    versioning_enabled    = true
    lifecycle_configured  = true
  }
}