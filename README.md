# DropBox Application

A Spring Boot-based file storage application that provides RESTful APIs for file management operations.

## Features

I have implemented all five required APIs:
- **Upload files** - Store files with metadata
- **Download files** - Retrieve files by ID
- **List files** - Get all stored files
- **Update files** - Modify file content and metadata  
- **Delete files** - Remove files from storage

## Current Implementation

This implementation uses **in-memory storage** for simplicity and demonstration purposes. File size is assumed to be manageable in memory for this version.

## Production Scaling

For production environments, this application can be scaled to use:
- **SQL databases** for metadata storage
- **AWS S3** for secure file storage (see [AWS Infrastructure](#aws-infrastructure))
- **Chunked file processing** for large files

## AWS Infrastructure

🔒 **Security-First S3 Implementation**

This repository includes production-ready AWS infrastructure configurations that comply with **AWS Security Hub control S3.3** for blocking public write access to S3 buckets.

### Quick Start - Deploy Secure S3 Buckets

```bash
# Option 1: Using AWS CLI scripts (Recommended for quick deployment)
./scripts/deploy-s3-security.sh --bucket-prefix your-org-dropbox

# Option 2: Using Terraform
cd infrastructure/terraform
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars with your settings
terraform init && terraform apply

# Option 3: Using CloudFormation
aws cloudformation deploy \
  --template-file infrastructure/cloudformation/s3-secure-buckets.yaml \
  --stack-name dropbox-s3-security \
  --parameter-overrides BucketPrefix=your-org-dropbox
```

### Security Validation

```bash
# Validate S3 security compliance
./scripts/validate-s3-security.sh --bucket-prefix your-org-dropbox
```

📚 **[Complete AWS Infrastructure Documentation](infrastructure/README.md)**

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/files` | List all files |
| GET | `/files/{fileID}` | Download file by ID |
| POST | `/files/upload` | Upload a new file |
| PUT | `/files/{fileID}` | Update existing file |
| DELETE | `/files/{fileID}` | Delete file |

## Running the Application

```bash
# Build and run
./mvnw clean spring-boot:run

# Or build and run JAR
./mvnw clean package
java -jar target/dropbox-application-0.0.1-SNAPSHOT.jar
```

## Future Enhancements

- [ ] Chunked file processing for large files
- [ ] Database integration for persistent storage  
- [ ] AWS S3 integration for cloud storage
- [ ] Authentication and authorization
- [ ] File sharing capabilities
- [ ] Audit logging 
