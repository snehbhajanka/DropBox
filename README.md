# DropBox

I have added all the five api's required. 
In this approach, i have used in-memory storage. However, 
we can scale this in production environments to use sql or aws storage. 
For this commit i have assumed that the file size is considerably smaller and can be 
handled in one-go. 
As a enhancement to this, we can consider the file data in chunks and handle. 

## Security Enhancements

### S3 Security Configuration

The `terraform/` directory contains infrastructure code to deploy secure S3 buckets that address critical security vulnerabilities:

- **Blocks all public write access** to prevent unauthorized data manipulation
- **Implements comprehensive public access blocking** (S3.3 security control)
- **Enables server-side encryption** for data protection
- **Configures object versioning** for data recovery
- **Sets up lifecycle management** for cost optimization

#### Deployment

```bash
cd terraform/
terraform init
terraform plan
terraform apply
```

#### Security Validation

```bash
# Validate S3 bucket security configuration
./terraform/validate-s3-security.sh

# Manual verification using AWS CLI
aws s3api get-public-access-block --bucket <bucket-name>
```

For detailed deployment and security information, see [terraform/README.md](terraform/README.md).

## API Endpoints

The application provides the following REST API endpoints for file management:

- `GET /files` - List all files
- `GET /files/{fileID}` - Download a specific file
- `POST /files/upload` - Upload a new file
- `PUT /files/{fileID}` - Update an existing file
- `DELETE /files/{fileID}` - Delete a file 
