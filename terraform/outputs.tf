output "s3_bucket_id" {
  description = "ID of the DropBox S3 bucket"
  value       = aws_s3_bucket.dropbox_storage.id
}

output "s3_bucket_arn" {
  description = "ARN of the DropBox S3 bucket"
  value       = aws_s3_bucket.dropbox_storage.arn
}

output "s3_bucket_domain_name" {
  description = "Domain name of the DropBox S3 bucket"
  value       = aws_s3_bucket.dropbox_storage.bucket_domain_name
}

output "s3_bucket_regional_domain_name" {
  description = "Regional domain name of the DropBox S3 bucket"
  value       = aws_s3_bucket.dropbox_storage.bucket_regional_domain_name
}

output "public_access_block_enabled" {
  description = "Status of S3 bucket public access block (security validation)"
  value = {
    block_public_acls       = aws_s3_bucket_public_access_block.dropbox_storage_pab.block_public_acls
    block_public_policy     = aws_s3_bucket_public_access_block.dropbox_storage_pab.block_public_policy
    ignore_public_acls      = aws_s3_bucket_public_access_block.dropbox_storage_pab.ignore_public_acls
    restrict_public_buckets = aws_s3_bucket_public_access_block.dropbox_storage_pab.restrict_public_buckets
  }
}