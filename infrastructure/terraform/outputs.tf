# Outputs for DropBox S3 Infrastructure
output "s3_bucket_name" {
  description = "Name of the S3 bucket"
  value       = aws_s3_bucket.dropbox_files.bucket
}

output "s3_bucket_arn" {
  description = "ARN of the S3 bucket"
  value       = aws_s3_bucket.dropbox_files.arn
}

output "s3_bucket_domain_name" {
  description = "Domain name of the S3 bucket"
  value       = aws_s3_bucket.dropbox_files.bucket_domain_name
}

output "iam_role_arn" {
  description = "ARN of the IAM role for DropBox application"
  value       = aws_iam_role.dropbox_app_role.arn
}

output "instance_profile_name" {
  description = "Name of the instance profile for EC2 instances"
  value       = aws_iam_instance_profile.dropbox_profile.name
}

output "public_access_block_settings" {
  description = "Public access block settings for security verification"
  value = {
    block_public_acls       = aws_s3_bucket_public_access_block.dropbox_files_pab.block_public_acls
    ignore_public_acls      = aws_s3_bucket_public_access_block.dropbox_files_pab.ignore_public_acls
    block_public_policy     = aws_s3_bucket_public_access_block.dropbox_files_pab.block_public_policy
    restrict_public_buckets = aws_s3_bucket_public_access_block.dropbox_files_pab.restrict_public_buckets
  }
}