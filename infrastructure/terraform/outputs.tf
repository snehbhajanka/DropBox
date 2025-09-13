output "bucket_name" {
  description = "Name of the created S3 bucket"
  value       = aws_s3_bucket.dropbox_storage.bucket
}

output "bucket_arn" {
  description = "ARN of the created S3 bucket"
  value       = aws_s3_bucket.dropbox_storage.arn
}

output "bucket_region" {
  description = "Region of the S3 bucket"
  value       = aws_s3_bucket.dropbox_storage.region
}

output "bucket_domain_name" {
  description = "Domain name of the S3 bucket"
  value       = aws_s3_bucket.dropbox_storage.bucket_domain_name
}

output "logs_bucket_name" {
  description = "Name of the access logs S3 bucket"
  value       = aws_s3_bucket.dropbox_access_logs.bucket
}

output "public_access_block_settings" {
  description = "Public access block settings for security verification"
  value = {
    block_public_acls       = aws_s3_bucket_public_access_block.dropbox_storage_pab.block_public_acls
    ignore_public_acls      = aws_s3_bucket_public_access_block.dropbox_storage_pab.ignore_public_acls
    block_public_policy     = aws_s3_bucket_public_access_block.dropbox_storage_pab.block_public_policy
    restrict_public_buckets = aws_s3_bucket_public_access_block.dropbox_storage_pab.restrict_public_buckets
  }
}

output "security_validation_commands" {
  description = "Commands to validate S3 bucket security settings"
  value = {
    check_public_access_block = "aws s3api get-public-access-block --bucket ${aws_s3_bucket.dropbox_storage.bucket}"
    check_bucket_policy       = "aws s3api get-bucket-policy --bucket ${aws_s3_bucket.dropbox_storage.bucket}"
    test_public_write_denied  = "aws s3 cp test-file.txt s3://${aws_s3_bucket.dropbox_storage.bucket}/test-file.txt --no-sign-request"
  }
}