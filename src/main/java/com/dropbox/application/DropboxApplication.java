package com.dropbox.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileDescriptor;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@SpringBootApplication
@RestController
public class DropboxApplication {

	private static final Map<String,FileMetaData> fileStorage=new HashMap<>();
	
	// Security configuration
	private static final List<String> ALLOWED_FILE_TYPES = Arrays.asList(
		"image/jpeg", "image/png", "image/gif", "image/bmp", "image/webp",
		"text/plain", "text/csv", "application/pdf", 
		"application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
		"application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
	);
	
	private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
	private static final Pattern SAFE_FILENAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");
	private static final int MAX_FILENAME_LENGTH = 100;

	public static void main(String[] args) {

		SpringApplication.run(DropboxApplication.class, args);
	}

	// Security validation methods
	private boolean isValidFileType(String contentType) {
		return contentType != null && ALLOWED_FILE_TYPES.contains(contentType.toLowerCase());
	}
	
	private boolean isValidFileSize(long size) {
		return size > 0 && size <= MAX_FILE_SIZE;
	}
	
	private String sanitizeFileName(String fileName) {
		if (fileName == null || fileName.trim().isEmpty()) {
			return null;
		}
		
		// Check for path traversal attempts first - reject if found
		if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
			return null;
		}
		
		String sanitized = fileName.trim();
		
		// Check length
		if (sanitized.length() > MAX_FILENAME_LENGTH) {
			return null; // Don't truncate, reject instead
		}
		
		// Validate against safe pattern
		if (!SAFE_FILENAME_PATTERN.matcher(sanitized).matches()) {
			return null;
		}
		
		return sanitized;
	}
	
	private boolean isValidMetadata(Map<String, String> metadata) {
		if (metadata == null) {
			return true;
		}
		
		// Validate metadata size and content
		if (metadata.size() > 10) {
			return false;
		}
		
		for (Map.Entry<String, String> entry : metadata.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			
			if (key == null || key.length() > 50 || value == null || value.length() > 200) {
				return false;
			}
			
			// Basic XSS prevention
			if (key.contains("<") || key.contains(">") || value.contains("<") || value.contains(">")) {
				return false;
			}
		}
		
		return true;
	}


@GetMapping("/files")
public ResponseEntity<Map<String, Object>> listFiles(){
	if(fileStorage!=null && fileStorage.values()!=null && !fileStorage.values().isEmpty())
		return ResponseEntity.ok(Map.of("files",fileStorage.values()));
	else return ResponseEntity.ok(Map.of("status","No files to display"));
}

@GetMapping("/files/{fileID}")
public ResponseEntity<byte[]> readFile(@PathVariable String fileID){
	// Validate fileID format
	if (fileID == null || fileID.trim().isEmpty() || !fileID.matches("^[a-fA-F0-9-]+$")) {
		return ResponseEntity.badRequest().build();
	}
	
	FileMetaData file=fileStorage.get(fileID);
	if(file!=null){
		// Sanitize filename for Content-Disposition header
		String safeFileName = file.getFileName().replaceAll("[^a-zA-Z0-9._-]", "_");
		return ResponseEntity.ok()
				.header("Content-Disposition", "attachment; filename=\"" + safeFileName + "\"")
				.header("Content-Type", file.getContentType())
				.body(file.getData());
	} else{
		return ResponseEntity.notFound().build();
	}
}

@PostMapping("/files/upload")
public ResponseEntity<Map<String,String>> uploadFile(
		@RequestParam("file") MultipartFile file,
		@RequestParam("file_name") String fileName,
		@RequestParam(value = "metadata",required = false) Map<String,String> metaData){
	try {
		// Validate file is not empty
		if (file.isEmpty()) {
			return ResponseEntity.badRequest()
				.body(Map.of("error", "File cannot be empty"));
		}
		
		// Validate file size
		if (!isValidFileSize(file.getSize())) {
			return ResponseEntity.badRequest()
				.body(Map.of("error", "File size must be between 1 byte and 10MB"));
		}
		
		// Validate file type
		if (!isValidFileType(file.getContentType())) {
			return ResponseEntity.badRequest()
				.body(Map.of("error", "File type not allowed. Allowed types: images, text, PDF, Office documents"));
		}
		
		// Sanitize and validate filename
		String sanitizedFileName = sanitizeFileName(fileName);
		if (sanitizedFileName == null) {
			return ResponseEntity.badRequest()
				.body(Map.of("error", "Invalid filename. Use only alphanumeric characters, dots, hyphens, and underscores"));
		}
		
		// Validate metadata
		if (!isValidMetadata(metaData)) {
			return ResponseEntity.badRequest()
				.body(Map.of("error", "Invalid metadata. Limit 10 entries, 50 chars per key, 200 chars per value"));
		}
		
		String fileID = UUID.randomUUID().toString();
		byte[] fileData = file.getBytes();
		FileMetaData fileMetaData = new FileMetaData(fileID,
				sanitizedFileName, LocalDateTime.now(), file.getSize(), file.getContentType(), metaData, fileData);
		fileStorage.put(fileID, fileMetaData);
		return ResponseEntity.ok(Map.of("file_id",fileID));
	} catch(Exception e){
		// Log error for debugging but don't expose details
		System.err.println("File upload error: " + e.getMessage());
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error","Failed to upload file"));
	}
}

@DeleteMapping("/files/{fileID}")
public ResponseEntity<?> deleteFile(@PathVariable String fileID){
		// Validate fileID format
		if (fileID == null || fileID.trim().isEmpty() || !fileID.matches("^[a-fA-F0-9-]+$")) {
			return ResponseEntity.badRequest().body(Map.of("error", "Invalid file ID format"));
		}
		
		if(fileStorage.containsKey(fileID)){
			fileStorage.remove(fileID);
			return ResponseEntity.ok(Map.of("message","File deleted successfully"));
		} else {
			return  ResponseEntity.notFound().build();
		}
}

@PutMapping("/files/{fileID}")
public ResponseEntity<?> updateFile(@PathVariable String fileID,
									@RequestParam(value="file",required = false) MultipartFile file,
									@RequestParam(value ="metadata",required = false) Map<String,String> metaData){
		try {
			// Validate fileID format
			if (fileID == null || fileID.trim().isEmpty() || !fileID.matches("^[a-fA-F0-9-]+$")) {
				return ResponseEntity.badRequest().body(Map.of("error", "Invalid file ID format"));
			}
			
			FileMetaData fileMetaData = fileStorage.get(fileID);
			if (fileMetaData != null) {
				if (file != null) {
					// Validate file size
					if (!isValidFileSize(file.getSize())) {
						return ResponseEntity.badRequest()
							.body(Map.of("error", "File size must be between 1 byte and 10MB"));
					}
					
					// Validate file type
					if (!isValidFileType(file.getContentType())) {
						return ResponseEntity.badRequest()
							.body(Map.of("error", "File type not allowed"));
					}
					
					fileMetaData.setData(file.getBytes());
					fileMetaData.setSize(file.getSize());
					fileMetaData.setContentType(file.getContentType());
				}
				if (metaData != null) {
					// Validate metadata
					if (!isValidMetadata(metaData)) {
						return ResponseEntity.badRequest()
							.body(Map.of("error", "Invalid metadata"));
					}
					fileMetaData.getMetadata().putAll(metaData);
				}
				return ResponseEntity.ok(fileMetaData.getMetadata());
			} else {
				return ResponseEntity.notFound().build();
			}
		} catch (Exception e){
			// Log error for debugging but don't expose details
			System.err.println("File update error: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error","Failed to update file"));

		}
}


}
