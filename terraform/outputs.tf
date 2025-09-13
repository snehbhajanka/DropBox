# Outputs for the DropBox S3 infrastructure

output "bucket_names" {
  description = "Names of the created S3 buckets"
  value       = aws_s3_bucket.dropbox_buckets[*].bucket
}

output "bucket_arns" {
  description = "ARNs of the created S3 buckets"
  value       = aws_s3_bucket.dropbox_buckets[*].arn
}

output "bucket_domains" {
  description = "Domain names of the created S3 buckets"
  value       = aws_s3_bucket.dropbox_buckets[*].bucket_domain_name
}

output "public_access_block_status" {
  description = "Public access block configuration for all buckets"
  value = {
    for i, bucket in aws_s3_bucket.dropbox_buckets : bucket.bucket => {
      block_public_acls       = aws_s3_bucket_public_access_block.dropbox_bucket_pab[i].block_public_acls
      ignore_public_acls      = aws_s3_bucket_public_access_block.dropbox_bucket_pab[i].ignore_public_acls
      block_public_policy     = aws_s3_bucket_public_access_block.dropbox_bucket_pab[i].block_public_policy
      restrict_public_buckets = aws_s3_bucket_public_access_block.dropbox_bucket_pab[i].restrict_public_buckets
    }
  }
}

output "aws_account_id" {
  description = "AWS Account ID"
  value       = data.aws_caller_identity.current.account_id
}