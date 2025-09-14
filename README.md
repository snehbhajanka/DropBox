# DropBox

A Spring Boot application that provides file storage and management APIs. The application supports file upload, download, listing, update, and deletion operations.

## 📋 Features

I have added all the five APIs required:
- **Upload** files with metadata
- **Download** files by ID  
- **List** all stored files
- **Update** file content and metadata
- **Delete** files by ID

## 🏗️ Architecture

In this approach, I have used in-memory storage for development and testing. However, we can scale this in production environments to use SQL databases or AWS S3 storage.

For this implementation, I have assumed that the file size is considerably smaller and can be handled in one-go. As an enhancement, we can consider processing file data in chunks for larger files.

## 🔒 Security

The repository includes secure AWS S3 infrastructure configuration that addresses AWS Security Hub Control S3.3 (Block Public Write Access). See [SECURITY.md](SECURITY.md) for complete security documentation.

### Production S3 Configuration
- Secure S3 bucket with public access blocked
- IAM roles with least privilege access
- Server-side encryption enabled
- HTTPS-only access enforced

## 🚀 Getting Started

### Prerequisites
- Java 17 or later
- Maven 3.6 or later

### Running the Application

1. **Clone the repository**:
   ```bash
   git clone <repository-url>
   cd DropBox
   ```

2. **Build the application**:
   ```bash
   ./mvnw clean compile
   ```

3. **Run tests**:
   ```bash
   ./mvnw test
   ```

4. **Start the application**:
   ```bash
   ./mvnw spring-boot:run
   ```

The application will start on port 8080.

## 📚 API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/files/upload` | Upload a new file |
| GET | `/files` | List all files |
| GET | `/files/{fileID}` | Download a file |
| PUT | `/files/{fileID}` | Update a file |
| DELETE | `/files/{fileID}` | Delete a file |

## 🏭 Production Deployment

For production environments using AWS S3:

1. Deploy the secure infrastructure using Terraform (see `/infrastructure/terraform/`)
2. Configure the application with AWS credentials
3. Update the application to use S3 instead of in-memory storage

See [SECURITY.md](SECURITY.md) for detailed security configuration and compliance information. 
