# Outputs for the DropBox S3 infrastructure

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

output "public_access_block_configuration" {
  description = "Public access block configuration for the S3 bucket"
  value = {
    block_public_acls       = aws_s3_bucket_public_access_block.dropbox_bucket_pab.block_public_acls
    ignore_public_acls      = aws_s3_bucket_public_access_block.dropbox_bucket_pab.ignore_public_acls
    block_public_policy     = aws_s3_bucket_public_access_block.dropbox_bucket_pab.block_public_policy
    restrict_public_buckets = aws_s3_bucket_public_access_block.dropbox_bucket_pab.restrict_public_buckets
  }
}