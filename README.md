# DropBox

I have added all the five api's required. 
In this approach, i have used in-memory storage. However, 
we can scale this in production environments to use sql or aws storage. 
For this commit i have assumed that the file size is considerably smaller and can be 
handled in one-go. 
As a enhancement to this, we can consider the file data in chunks and handle. 

## Security Configuration

This repository now includes secure AWS S3 configurations to address security misconfiguration S3.3 (public write access). All S3 bucket configurations include:

- **Block Public ACLs**: Enabled
- **Ignore Public ACLs**: Enabled  
- **Block Public Policy**: Enabled
- **Restrict Public Buckets**: Enabled

### Infrastructure as Code
- **Terraform**: `infrastructure/terraform/s3-secure-bucket.tf`
- **CloudFormation**: `infrastructure/cloudformation/s3-secure-bucket.yaml`

### Security Validation
Run the security validation script:
```bash
./scripts/validate-s3-security.sh your-bucket-name validate
```

For detailed security documentation, see [docs/S3_SECURITY.md](docs/S3_SECURITY.md). 
