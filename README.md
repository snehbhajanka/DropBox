# DropBox

I have added all the five api's required. 
In this approach, i have used in-memory storage. However, 
we can scale this in production environments to use sql or aws storage. 
For this commit i have assumed that the file size is considerably smaller and can be 
handled in one-go. 
As a enhancement to this, we can consider the file data in chunks and handle.

## 🔒 Security Configuration

This repository now includes secure AWS S3 infrastructure configuration that addresses **CRITICAL** security misconfiguration **S3.3 - Block Public Write Access**.

### Security Features

✅ **S3 Public Access Blocked**: All S3 buckets are configured with comprehensive public access blocks  
✅ **Encryption Enabled**: Server-side encryption with AES256  
✅ **Versioning Enabled**: Object versioning for data protection  
✅ **Lifecycle Management**: Automatic cleanup of old versions  

### Infrastructure

The `terraform/` directory contains infrastructure-as-code that creates secure S3 buckets with:

- `block_public_acls = true` - Prevents new public ACLs
- `ignore_public_acls = true` - Ignores existing public ACLs  
- `block_public_policy = true` - Prevents public bucket policies
- `restrict_public_buckets = true` - Restricts access to buckets with public policies

### Quick Start

1. **Deploy secure infrastructure**:
   ```bash
   cd terraform
   cp terraform.tfvars.example terraform.tfvars
   # Edit terraform.tfvars with your configuration
   terraform init
   terraform apply
   ```

2. **Run the application**:
   ```bash
   ./mvnw spring-boot:run
   ```

3. **Validate security**:
   ```bash
   # Check S3 security settings
   aws s3api get-public-access-block --bucket <your-bucket-name>
   ```

### API Endpoints

- `GET /files` - List all files
- `GET /files/{fileID}` - Download a specific file  
- `POST /files/upload` - Upload a new file
- `PUT /files/{fileID}` - Update file or metadata
- `DELETE /files/{fileID}` - Delete a file

See `terraform/README.md` for detailed security configuration documentation. 
