# DropBox

I have added all the five api's required. 

## Storage Implementation
This application now uses **secure AWS S3 storage** instead of in-memory storage for production environments. The S3 integration includes comprehensive security controls to prevent public write access vulnerabilities.

## Security Features
- ✅ **S3 Public Access Block**: Prevents all public access configurations
- ✅ **Bucket Policy**: Explicitly denies public write operations  
- ✅ **Private ACL**: All files are private by default
- ✅ **Security Verification**: Endpoint to verify security configuration

For detailed security information, see [SECURITY.md](SECURITY.md).

## API Endpoints
- `GET /files` - List all files
- `GET /files/{fileID}` - Download a specific file
- `POST /files/upload` - Upload a new file
- `PUT /files/{fileID}` - Update an existing file
- `DELETE /files/{fileID}` - Delete a file
- `GET /security/verify` - Verify S3 security configuration

## Configuration
Set the following environment variables:
```
AWS_S3_BUCKET_NAME=your-secure-bucket-name
AWS_REGION=us-east-2
AWS_ACCESS_KEY_ID=your-access-key
AWS_SECRET_ACCESS_KEY=your-secret-key
```

## Development Notes
In this approach, i have migrated from in-memory storage to secure S3 storage. 
The implementation can be scaled in production environments with proper AWS IAM roles and policies.
For this commit i have implemented comprehensive security controls to prevent public write access vulnerabilities.
As a enhancement to this, we can consider implementing additional features like encryption at rest and in transit. 
