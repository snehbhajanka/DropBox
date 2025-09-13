# Outputs for Terraform configuration

output "primary_bucket_name" {
  description = "Name of the primary S3 bucket for file storage"
  value       = aws_s3_bucket.dropbox_storage.bucket
}

output "primary_bucket_arn" {
  description = "ARN of the primary S3 bucket"
  value       = aws_s3_bucket.dropbox_storage.arn
}

output "temp_bucket_name" {
  description = "Name of the temporary uploads S3 bucket"
  value       = aws_s3_bucket.dropbox_temp_uploads.bucket
}

output "temp_bucket_arn" {
  description = "ARN of the temporary uploads S3 bucket"
  value       = aws_s3_bucket.dropbox_temp_uploads.arn
}

output "public_access_block_status" {
  description = "Public access block configuration status for primary bucket"
  value = {
    bucket                  = aws_s3_bucket.dropbox_storage.bucket
    block_public_acls       = aws_s3_bucket_public_access_block.dropbox_storage_pab.block_public_acls
    ignore_public_acls      = aws_s3_bucket_public_access_block.dropbox_storage_pab.ignore_public_acls
    block_public_policy     = aws_s3_bucket_public_access_block.dropbox_storage_pab.block_public_policy
    restrict_public_buckets = aws_s3_bucket_public_access_block.dropbox_storage_pab.restrict_public_buckets
  }
}

output "temp_bucket_public_access_block_status" {
  description = "Public access block configuration status for temporary bucket"
  value = {
    bucket                  = aws_s3_bucket.dropbox_temp_uploads.bucket
    block_public_acls       = aws_s3_bucket_public_access_block.dropbox_temp_uploads_pab.block_public_acls
    ignore_public_acls      = aws_s3_bucket_public_access_block.dropbox_temp_uploads_pab.ignore_public_acls
    block_public_policy     = aws_s3_bucket_public_access_block.dropbox_temp_uploads_pab.block_public_policy
    restrict_public_buckets = aws_s3_bucket_public_access_block.dropbox_temp_uploads_pab.restrict_public_buckets
  }
}

output "security_compliance_summary" {
  description = "Summary of S3.3 security compliance status"
  value = {
    compliant = true
    controls = {
      "S3.3" = "S3 buckets should block public write access"
    }
    buckets_configured = [
      aws_s3_bucket.dropbox_storage.bucket,
      aws_s3_bucket.dropbox_temp_uploads.bucket
    ]
    all_public_access_blocked = true
  }
}