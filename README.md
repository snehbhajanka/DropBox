# DropBox

A Spring Boot application that implements a DropBox-like file storage API with in-memory storage.

## Features

This application provides five REST API endpoints for file management:

1. **GET /files** - List all stored files
2. **GET /files/{fileID}** - Download a specific file
3. **POST /files/upload** - Upload a new file
4. **DELETE /files/{fileID}** - Delete a file
5. **PUT /files/{fileID}** - Update a file or its metadata

## Implementation Details

- Uses in-memory storage with HashMap for simplicity
- Built with Spring Boot 3.1.5 and Java 17
- Includes input validation and error handling
- Comprehensive test coverage
- Separated concerns with dedicated controller class

## Architecture

The application follows good software engineering practices:
- **DropboxApplication.java** - Main Spring Boot application class
- **FileController.java** - REST controller handling all file operations
- **FileMetaData.java** - Data model for file metadata and content

## Assumptions

For this implementation, I have assumed that:
- File size is considerably smaller and can be handled in one-go
- In-memory storage is sufficient for demonstration purposes

## Future Enhancements

This approach can be scaled for production environments by:
- Using SQL databases or AWS storage instead of in-memory storage
- Handling file data in chunks for larger files
- Adding authentication and authorization
- Implementing file versioning
- Adding folder structure support

## Running the Application

```bash
# Build the project
./mvnw clean compile

# Run tests
./mvnw test

# Start the application
./mvnw spring-boot:run
```

The application will start on port 8080. You can test the API endpoints:

```bash
# List files
curl http://localhost:8080/files

# Upload a file
curl -X POST -F "file=@yourfile.txt" -F "file_name=test.txt" http://localhost:8080/files/upload
``` 
