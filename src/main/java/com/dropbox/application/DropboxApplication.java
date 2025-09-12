package com.dropbox.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileDescriptor;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@SpringBootApplication
@RestController
public class DropboxApplication {

	private static final Map<String,FileMetaData> fileStorage=new HashMap<>();
	
	// Security constants
	private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
	private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
		"text/plain", "text/csv", "application/pdf", "image/jpeg", 
		"image/png", "image/gif", "application/json", "application/xml"
	);
	private static final Pattern SAFE_FILENAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");
	private static final int MAX_FILENAME_LENGTH = 255;

	public static void main(String[] args) {

		SpringApplication.run(DropboxApplication.class, args);
	}

	/**
	 * Validates file upload parameters for security
	 */
	private ResponseEntity<Map<String,String>> validateFileUpload(MultipartFile file, String fileName) {
		// Check file size
		if (file.getSize() > MAX_FILE_SIZE) {
			return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
				.body(Map.of("error", "File size exceeds maximum allowed size of " + (MAX_FILE_SIZE / 1024 / 1024) + "MB"));
		}
		
		// Check if file is empty
		if (file.isEmpty()) {
			return ResponseEntity.badRequest()
				.body(Map.of("error", "File cannot be empty"));
		}
		
		// Validate content type
		String contentType = file.getContentType();
		if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
			return ResponseEntity.badRequest()
				.body(Map.of("error", "File type not allowed. Allowed types: " + ALLOWED_CONTENT_TYPES));
		}
		
		// Validate filename
		if (fileName == null || fileName.trim().isEmpty()) {
			return ResponseEntity.badRequest()
				.body(Map.of("error", "Filename cannot be empty"));
		}
		
		if (fileName.length() > MAX_FILENAME_LENGTH) {
			return ResponseEntity.badRequest()
				.body(Map.of("error", "Filename too long. Maximum length is " + MAX_FILENAME_LENGTH + " characters"));
		}
		
		if (!SAFE_FILENAME_PATTERN.matcher(fileName).matches()) {
			return ResponseEntity.badRequest()
				.body(Map.of("error", "Filename contains invalid characters. Only alphanumeric, dots, underscores and hyphens are allowed"));
		}
		
		return null; // No validation errors
	}

	/**
	 * Sanitizes metadata to prevent injection attacks
	 */
	private Map<String,String> sanitizeMetadata(Map<String,String> metadata) {
		if (metadata == null) {
			return new HashMap<>();
		}
		
		Map<String,String> sanitized = new HashMap<>();
		for (Map.Entry<String,String> entry : metadata.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			
			// Limit key and value lengths and remove dangerous characters
			if (key != null && value != null && key.length() <= 100 && value.length() <= 500) {
				key = key.replaceAll("[<>\"'&]", "");
				value = value.replaceAll("[<>\"'&]", "");
				if (!key.isEmpty() && !value.isEmpty()) {
					sanitized.put(key, value);
				}
			}
		}
		
		return sanitized;
	}


@GetMapping("/files")
public ResponseEntity<Map<String, Object>> listFiles(){
	try {
		if(fileStorage!=null && fileStorage.values()!=null && !fileStorage.values().isEmpty())
			return ResponseEntity.ok()
				.header("X-Content-Type-Options", "nosniff")
				.header("X-Frame-Options", "DENY")
				.body(Map.of("files",fileStorage.values()));
		else return ResponseEntity.ok()
			.header("X-Content-Type-Options", "nosniff")
			.header("X-Frame-Options", "DENY")
			.body(Map.of("status","No files to display"));
	} catch (Exception e) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(Map.of("error", "Unable to list files"));
	}
}

@GetMapping("/files/{fileID}")
public ResponseEntity<byte[]> readFile(@PathVariable String fileID){
	try {
		// Validate fileID format (UUID)
		if (fileID == null || fileID.trim().isEmpty()) {
			return ResponseEntity.badRequest().build();
		}
		
		// Basic UUID format validation
		if (!fileID.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")) {
			return ResponseEntity.badRequest().build();
		}
		
		FileMetaData file=fileStorage.get(fileID);
		if(file!=null){
			// Sanitize filename for Content-Disposition header
			String sanitizedFilename = file.getFileName().replaceAll("[^a-zA-Z0-9._-]", "");
			
			return ResponseEntity.ok()
				.header("Content-Disposition", "attachment; filename=\"" + sanitizedFilename + "\"")
				.header("X-Content-Type-Options", "nosniff")
				.header("X-Frame-Options", "DENY")
				.header("Content-Type", file.getContentType())
				.body(file.getData());
		} else{
			return ResponseEntity.notFound().build();
		}
	} catch (Exception e) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
	}
}

@PostMapping("/files/upload")
public ResponseEntity<Map<String,String>> uploadFile(
		@RequestParam("file") MultipartFile file,
		@RequestParam("file_name") String fileName,
		@RequestParam(value = "metadata",required = false) Map<String,String> metaData){
	try {
		// Validate file upload
		ResponseEntity<Map<String,String>> validationError = validateFileUpload(file, fileName);
		if (validationError != null) {
			return validationError;
		}
		
		// Sanitize inputs
		String sanitizedFileName = fileName.trim();
		Map<String,String> sanitizedMetadata = sanitizeMetadata(metaData);
		
		String fileID = UUID.randomUUID().toString();
		byte[] fileData = file.getBytes();
		FileMetaData fileMetaData = new FileMetaData(fileID,
				sanitizedFileName, LocalDateTime.now(), file.getSize(), file.getContentType(), sanitizedMetadata, fileData);
		fileStorage.put(fileID, fileMetaData);
		
		return ResponseEntity.ok()
			.header("X-Content-Type-Options", "nosniff")
			.header("X-Frame-Options", "DENY")
			.body(Map.of("file_id",fileID));
	} catch(Exception e){
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error","Failed to upload the file"));
	}
}

@DeleteMapping("/files/{fileID}")
public ResponseEntity<?> deleteFile(@PathVariable String fileID){
	try {
		// Validate fileID format (UUID)
		if (fileID == null || fileID.trim().isEmpty()) {
			return ResponseEntity.badRequest().build();
		}
		
		// Basic UUID format validation
		if (!fileID.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")) {
			return ResponseEntity.badRequest().build();
		}
		
		if(fileStorage.containsKey(fileID)){
			fileStorage.remove(fileID);
			return ResponseEntity.ok()
				.header("X-Content-Type-Options", "nosniff")
				.header("X-Frame-Options", "DENY")
				.body(Map.of("message","File deleted successfully"));
		} else {
			return  ResponseEntity.notFound().build();
		}
	} catch (Exception e) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(Map.of("error", "Failed to delete file"));
	}
}

@PutMapping("/files/{fileID}")
public ResponseEntity<?> updateFile(@PathVariable String fileID,
									@RequestParam(value="file",required = false) MultipartFile file,
									@RequestParam(value ="metadata",required = false) Map<String,String> metaData){
	try {
		// Validate fileID format (UUID)
		if (fileID == null || fileID.trim().isEmpty()) {
			return ResponseEntity.badRequest().build();
		}
		
		// Basic UUID format validation
		if (!fileID.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")) {
			return ResponseEntity.badRequest().build();
		}
		
		FileMetaData fileMetaData = fileStorage.get(fileID);
		if (fileMetaData != null) {
			if (file != null) {
				// Validate new file if provided
				ResponseEntity<Map<String,String>> validationError = validateFileUpload(file, fileMetaData.getFileName());
				if (validationError != null) {
					return validationError;
				}
				
				fileMetaData.setData(file.getBytes());
				fileMetaData.setSize(file.getSize());
				fileMetaData.setContentType(file.getContentType());
			}
			if (metaData != null) {
				Map<String,String> sanitizedMetadata = sanitizeMetadata(metaData);
				fileMetaData.getMetadata().putAll(sanitizedMetadata);
			}
			return ResponseEntity.ok()
				.header("X-Content-Type-Options", "nosniff")
				.header("X-Frame-Options", "DENY")
				.body(fileMetaData.getMetadata());
		} else {
			return ResponseEntity.notFound().build();
		}
	} catch (Exception e){
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error","Failed to update the file"));
	}
}

/**
 * Global exception handler for file upload size limit
 */
@ExceptionHandler(MaxUploadSizeExceededException.class)
public ResponseEntity<Map<String,String>> handleMaxSizeException(MaxUploadSizeExceededException exc) {
	return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
		.body(Map.of("error", "File size exceeds maximum allowed size of " + (MAX_FILE_SIZE / 1024 / 1024) + "MB"));
}


}
