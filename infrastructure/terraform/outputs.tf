# Output values for the S3 bucket configuration

output "bucket_name" {
  description = "Name of the created S3 bucket"
  value       = aws_s3_bucket.dropbox_storage.id
}

output "bucket_arn" {
  description = "ARN of the created S3 bucket"
  value       = aws_s3_bucket.dropbox_storage.arn
}

output "bucket_domain_name" {
  description = "Domain name of the S3 bucket"
  value       = aws_s3_bucket.dropbox_storage.bucket_domain_name
}

output "public_access_block_configuration" {
  description = "Public access block configuration for security validation"
  value = {
    block_public_acls       = aws_s3_bucket_public_access_block.dropbox_storage_pab.block_public_acls
    ignore_public_acls      = aws_s3_bucket_public_access_block.dropbox_storage_pab.ignore_public_acls
    block_public_policy     = aws_s3_bucket_public_access_block.dropbox_storage_pab.block_public_policy
    restrict_public_buckets = aws_s3_bucket_public_access_block.dropbox_storage_pab.restrict_public_buckets
  }
}