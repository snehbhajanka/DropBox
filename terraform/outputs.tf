output "bucket_name" {
  description = "Name of the created S3 bucket"
  value       = aws_s3_bucket.dropbox_bucket.bucket
}

output "bucket_arn" {
  description = "ARN of the created S3 bucket"
  value       = aws_s3_bucket.dropbox_bucket.arn
}

output "bucket_domain_name" {
  description = "Domain name of the S3 bucket"
  value       = aws_s3_bucket.dropbox_bucket.bucket_domain_name
}

output "bucket_regional_domain_name" {
  description = "Regional domain name of the S3 bucket"
  value       = aws_s3_bucket.dropbox_bucket.bucket_regional_domain_name
}

output "public_access_block_status" {
  description = "Public access block configuration"
  value = {
    block_public_acls       = aws_s3_bucket_public_access_block.dropbox_bucket_pab.block_public_acls
    ignore_public_acls      = aws_s3_bucket_public_access_block.dropbox_bucket_pab.ignore_public_acls
    block_public_policy     = aws_s3_bucket_public_access_block.dropbox_bucket_pab.block_public_policy
    restrict_public_buckets = aws_s3_bucket_public_access_block.dropbox_bucket_pab.restrict_public_buckets
  }
}

output "iam_role_arn" {
  description = "ARN of the IAM role for application access"
  value       = aws_iam_role.dropbox_app_role.arn
}

output "iam_policy_arn" {
  description = "ARN of the IAM policy for S3 access"
  value       = aws_iam_policy.dropbox_s3_policy.arn
}

output "logs_bucket_name" {
  description = "Name of the access logs bucket"
  value       = aws_s3_bucket.dropbox_logs_bucket.bucket
}

output "security_summary" {
  description = "Summary of security configurations applied"
  value = {
    public_write_blocked    = true
    public_read_blocked     = true
    versioning_enabled      = aws_s3_bucket_versioning.dropbox_bucket_versioning.versioning_configuration[0].status == "Enabled"
    encryption_enabled      = true
    access_logging_enabled  = var.enable_logging
    iam_role_configured     = true
    bucket_policy_applied   = true
  }
}