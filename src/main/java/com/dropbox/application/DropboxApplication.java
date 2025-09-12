package com.dropbox.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@SpringBootApplication
@RestController
public class DropboxApplication {

	private static final Map<String,FileMetaData> fileStorage=new HashMap<>();
	
	// Pattern to validate file IDs (UUID format)
	private static final Pattern UUID_PATTERN = Pattern.compile(
		"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
	);

	public static void main(String[] args) {
		SpringApplication.run(DropboxApplication.class, args);
	}


@GetMapping("/files")
public ResponseEntity<Map<String, Object>> listFiles(){
	if(fileStorage!=null && fileStorage.values()!=null && !fileStorage.values().isEmpty())
		return ResponseEntity.ok(Map.of("files",fileStorage.values()));
	else return ResponseEntity.ok(Map.of("status","No files to display"));
}

@GetMapping("/files/{fileID}")
public ResponseEntity<byte[]> readFile(@PathVariable String fileID){
	try {
		// Validate file ID format
		if (!isValidFileId(fileID)) {
			return ResponseEntity.badRequest().build();
		}
		
		FileMetaData file = fileStorage.get(fileID);
		if (file != null) {
			return ResponseEntity.ok()
					.header("Content-Disposition", "attachment; filename=\"" + file.getFileName() + "\"")
					.header("Content-Type", file.getContentType())
					.body(file.getData());
		} else {
			return ResponseEntity.notFound().build();
		}
	} catch (Exception e) {
		// Log error but don't expose details to prevent information disclosure
		System.err.println("Error reading file: " + e.getMessage());
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
	}
}

@PostMapping("/files/upload")
public ResponseEntity<Map<String,String>> uploadFile(
		@RequestParam("file") MultipartFile file,
		@RequestParam("file_name") String fileName,
		@RequestParam(value = "metadata", required = false) Map<String,String> metaData){
	try {
		// Security validations
		if (file.isEmpty()) {
			return ResponseEntity.badRequest()
					.body(Map.of("error", "File cannot be empty"));
		}
		
		// Check file size
		if (file.getSize() > SecurityConfig.MAX_FILE_SIZE) {
			return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
					.body(Map.of("error", "File size exceeds maximum allowed size of 10MB"));
		}
		
		// Sanitize and validate filename
		String sanitizedFileName = SecurityConfig.sanitizeFileName(fileName);
		if (!SecurityConfig.isValidFileName(sanitizedFileName)) {
			return ResponseEntity.badRequest()
					.body(Map.of("error", "Invalid file name or file type not allowed"));
		}
		
		// Validate metadata if provided
		if (metaData != null && !isValidMetadata(metaData)) {
			return ResponseEntity.badRequest()
					.body(Map.of("error", "Invalid metadata provided"));
		}
		
		String fileID = UUID.randomUUID().toString();
		byte[] fileData = file.getBytes();
		
		FileMetaData fileMetaData = new FileMetaData(fileID,
				sanitizedFileName, LocalDateTime.now(), file.getSize(), 
				file.getContentType(), metaData, fileData);
		
		fileStorage.put(fileID, fileMetaData);
		return ResponseEntity.ok(Map.of("file_id", fileID, "file_name", sanitizedFileName));
		
	} catch (SecurityException e) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(Map.of("error", "Security validation failed"));
	} catch (Exception e) {
		// Log error but don't expose details
		System.err.println("Error uploading file: " + e.getMessage());
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error", "Failed to upload the file"));
	}
}

@DeleteMapping("/files/{fileID}")
public ResponseEntity<?> deleteFile(@PathVariable String fileID){
	try {
		// Validate file ID format
		if (!isValidFileId(fileID)) {
			return ResponseEntity.badRequest()
					.body(Map.of("error", "Invalid file ID format"));
		}
		
		if (fileStorage.containsKey(fileID)) {
			fileStorage.remove(fileID);
			return ResponseEntity.ok(Map.of("message", "File deleted successfully"));
		} else {
			return ResponseEntity.notFound().build();
		}
	} catch (Exception e) {
		// Log error but don't expose details
		System.err.println("Error deleting file: " + e.getMessage());
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error", "Failed to delete the file"));
	}
}

@PutMapping("/files/{fileID}")
public ResponseEntity<?> updateFile(@PathVariable String fileID,
									@RequestParam(value="file", required = false) MultipartFile file,
									@RequestParam(value ="metadata", required = false) Map<String,String> metaData){
	try {
		// Validate file ID format
		if (!isValidFileId(fileID)) {
			return ResponseEntity.badRequest()
					.body(Map.of("error", "Invalid file ID format"));
		}
		
		FileMetaData fileMetaData = fileStorage.get(fileID);
		if (fileMetaData != null) {
			if (file != null) {
				// Check file size
				if (file.getSize() > SecurityConfig.MAX_FILE_SIZE) {
					return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
							.body(Map.of("error", "File size exceeds maximum allowed size of 10MB"));
				}
				
				fileMetaData.setData(file.getBytes());
				fileMetaData.setSize(file.getSize());
				fileMetaData.setContentType(file.getContentType());
			}
			
			if (metaData != null) {
				// Validate metadata
				if (!isValidMetadata(metaData)) {
					return ResponseEntity.badRequest()
							.body(Map.of("error", "Invalid metadata provided"));
				}
				fileMetaData.getMetadata().putAll(metaData);
			}
			
			return ResponseEntity.ok(Map.of("message", "File updated successfully"));
		} else {
			return ResponseEntity.notFound().build();
		}
	} catch (SecurityException e) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(Map.of("error", "Security validation failed"));
	} catch (Exception e) {
		// Log error but don't expose details
		System.err.println("Error updating file: " + e.getMessage());
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error", "Failed to update the file"));
	}
}

/**
 * Validates if a file ID is in the correct UUID format
 */
private boolean isValidFileId(String fileId) {
	if (fileId == null || fileId.trim().isEmpty()) {
		return false;
	}
	return UUID_PATTERN.matcher(fileId).matches();
}

/**
 * Validates metadata to prevent injection attacks
 */
private boolean isValidMetadata(Map<String, String> metadata) {
	if (metadata == null) {
		return true;
	}
	
	// Check metadata size limits
	if (metadata.size() > 10) {
		return false;
	}
	
	for (Map.Entry<String, String> entry : metadata.entrySet()) {
		String key = entry.getKey();
		String value = entry.getValue();
		
		// Validate key and value lengths and characters
		if (key == null || key.length() > 100 || 
			value == null || value.length() > 500) {
			return false;
		}
		
		// Check for potentially dangerous characters
		if (containsDangerousCharacters(key) || containsDangerousCharacters(value)) {
			return false;
		}
	}
	
	return true;
}

/**
 * Checks for potentially dangerous characters that could be used in injection attacks
 */
private boolean containsDangerousCharacters(String input) {
	if (input == null) {
		return false;
	}
	
	// Check for script tags, SQL injection patterns, etc.
	String lower = input.toLowerCase();
	return lower.contains("<script") || 
		   lower.contains("javascript:") || 
		   lower.contains("onload=") ||
		   lower.contains("onerror=") ||
		   input.contains("'") ||
		   input.contains("\"") ||
		   input.contains(";") ||
		   input.contains("--") ||
		   input.contains("/*") ||
		   input.contains("*/");
}


}
