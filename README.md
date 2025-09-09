# DropBox
I have added all the five api's required. 
In this approach, i have used in-memory storage. However, 
we can scale this in production environments to use sql or aws storage. 
For this commit i have assumed that the file size is considerably smaller and can be 
handled in one-go. 
As a enhancement to this, we can consider the file data in chunks and handle. 

## API Endpoints

This DropBox application provides the following REST API endpoints for file management:

### 1. List Files
**GET** `/files`
- Returns a list of all uploaded files with their metadata
- Response when files exist: `{"files": [...]}`
- Response when no files: `{"status": "No files to display"}`

### 2. Upload File
**POST** `/files/upload`
- Upload a new file to the storage
- Parameters:
  - `file`: The file to upload (multipart/form-data)
  - `file_name`: Name for the uploaded file
  - `metadata` (optional): Additional metadata as key-value pairs
- Response: `{"file_id": "uuid-string"}`

### 3. Download File
**GET** `/files/{fileID}`
- Download a specific file by its ID
- Returns the file content with appropriate headers
- Returns 404 if file not found

### 4. Update File
**PUT** `/files/{fileID}`
- Update an existing file or its metadata
- Parameters:
  - `file` (optional): New file content
  - `metadata` (optional): Updated metadata
- Returns updated metadata or 404 if file not found

### 5. Delete File
**DELETE** `/files/{fileID}`
- Delete a specific file by its ID  
- Response: `{"message": "File deleted successfully"}`
- Returns 404 if file not found

## Usage Examples

### Upload a file
```bash
curl -X POST -F "file=@example.txt" -F "file_name=example.txt" http://localhost:8080/files/upload
```

### List all files
```bash
curl http://localhost:8080/files
```

### Download a file
```bash
curl -O http://localhost:8080/files/{file_id}
```

### Delete a file
```bash
curl -X DELETE http://localhost:8080/files/{file_id}
```

## Running the Application

1. Build and run with Maven:
```bash
./mvnw spring-boot:run
```

2. The application will start on port 8080 by default

## Testing

Run the test suite:
```bash
./mvnw test
```

The tests include comprehensive integration tests for all API endpoints.
