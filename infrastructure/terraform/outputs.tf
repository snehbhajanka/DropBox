# Outputs for S3 Bucket Security Configuration

output "dropbox_storage_bucket_name" {
  description = "Name of the primary DropBox storage bucket"
  value       = aws_s3_bucket.dropbox_storage.bucket
}

output "dropbox_storage_bucket_arn" {
  description = "ARN of the primary DropBox storage bucket"
  value       = aws_s3_bucket.dropbox_storage.arn
}

output "dropbox_backups_bucket_name" {
  description = "Name of the DropBox backups bucket"
  value       = aws_s3_bucket.dropbox_backups.bucket
}

output "dropbox_logs_bucket_name" {
  description = "Name of the DropBox logs bucket"
  value       = aws_s3_bucket.dropbox_logs.bucket
}

output "public_access_block_status" {
  description = "Status of public access block settings for all buckets"
  value = {
    storage = {
      block_public_acls       = aws_s3_bucket_public_access_block.dropbox_storage_pab.block_public_acls
      ignore_public_acls      = aws_s3_bucket_public_access_block.dropbox_storage_pab.ignore_public_acls
      block_public_policy     = aws_s3_bucket_public_access_block.dropbox_storage_pab.block_public_policy
      restrict_public_buckets = aws_s3_bucket_public_access_block.dropbox_storage_pab.restrict_public_buckets
    }
    backups = {
      block_public_acls       = aws_s3_bucket_public_access_block.dropbox_backups_pab.block_public_acls
      ignore_public_acls      = aws_s3_bucket_public_access_block.dropbox_backups_pab.ignore_public_acls
      block_public_policy     = aws_s3_bucket_public_access_block.dropbox_backups_pab.block_public_policy
      restrict_public_buckets = aws_s3_bucket_public_access_block.dropbox_backups_pab.restrict_public_buckets
    }
    logs = {
      block_public_acls       = aws_s3_bucket_public_access_block.dropbox_logs_pab.block_public_acls
      ignore_public_acls      = aws_s3_bucket_public_access_block.dropbox_logs_pab.ignore_public_acls
      block_public_policy     = aws_s3_bucket_public_access_block.dropbox_logs_pab.block_public_policy
      restrict_public_buckets = aws_s3_bucket_public_access_block.dropbox_logs_pab.restrict_public_buckets
    }
  }
}