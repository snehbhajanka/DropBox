# Output values for the Terraform configuration

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

output "bucket_regional_domain_name" {
  description = "Regional domain name of the S3 bucket"
  value       = aws_s3_bucket.dropbox_storage.bucket_regional_domain_name
}

output "public_access_block_id" {
  description = "ID of the public access block configuration"
  value       = aws_s3_bucket_public_access_block.dropbox_storage_pab.id
}

output "public_access_settings" {
  description = "Public access block settings for verification"
  value = {
    block_public_acls       = aws_s3_bucket_public_access_block.dropbox_storage_pab.block_public_acls
    ignore_public_acls      = aws_s3_bucket_public_access_block.dropbox_storage_pab.ignore_public_acls
    block_public_policy     = aws_s3_bucket_public_access_block.dropbox_storage_pab.block_public_policy
    restrict_public_buckets = aws_s3_bucket_public_access_block.dropbox_storage_pab.restrict_public_buckets
  }
}

output "encryption_status" {
  description = "Server-side encryption configuration status"
  value = {
    enabled   = var.enable_encryption
    algorithm = var.enable_encryption ? "AES256" : "none"
  }
}

output "versioning_status" {
  description = "Bucket versioning status"
  value = aws_s3_bucket_versioning.dropbox_storage_versioning.versioning_configuration[0].status
}