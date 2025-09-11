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
import java.util.*;
import java.util.regex.Pattern;

@SpringBootApplication
@RestController
public class DropboxApplication {

	private static final Map<String,FileMetaData> fileStorage=new HashMap<>();
	
	// Security configuration
	private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB limit
	private static final Set<String> ALLOWED_FILE_TYPES = Set.of(
		"text/plain", "text/csv", "application/pdf", "image/jpeg", 
		"image/png", "image/gif", "application/json", "text/xml"
	);
	private static final Set<String> ALLOWED_FILE_EXTENSIONS = Set.of(
		".txt", ".csv", ".pdf", ".jpg", ".jpeg", ".png", ".gif", ".json", ".xml"
	);
	private static final Pattern SAFE_FILENAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");

	public static void main(String[] args) {

		SpringApplication.run(DropboxApplication.class, args);
	}


@GetMapping("/files")
public ResponseEntity<Map<String, Object>> listFiles(){
	if(fileStorage!=null && fileStorage.values()!=null && !fileStorage.values().isEmpty()) {
		ResponseEntity<Map<String, Object>> response = ResponseEntity.ok(Map.of("files",fileStorage.values()));
		return addSecurityHeaders(response);
	} else {
		ResponseEntity<Map<String, Object>> response = ResponseEntity.ok(Map.of("status","No files to display"));
		return addSecurityHeaders(response);
	}
}

@GetMapping("/files/{fileID}")
public ResponseEntity<byte[]> readFile(@PathVariable String fileID){
	FileMetaData file=fileStorage.get(fileID);
	if(file!=null){
		ResponseEntity<byte[]> response = ResponseEntity.ok()
				.header("Content-Disposition", "attachment; filename=\"" + file.getFileName() + "\"")
				.header("Content-Type", file.getContentType())
				.body(file.getData());
		return addSecurityHeaders(response);
	} else{
		return ResponseEntity.notFound().build();
	}
}

@PostMapping("/files/upload")
public ResponseEntity<Map<String,String>> uploadFile(
		@RequestParam("file") MultipartFile file,
		@RequestParam("file_name") String fileNname,
		@RequestParam(value = "metadata",required = false) Map<String,String> metaData){
	try {
		// Security validations
		ResponseEntity<Map<String,String>> validationError = validateFileUpload(file, fileNname);
		if (validationError != null) {
			return validationError;
		}
		
		String fileID = UUID.randomUUID().toString();
		byte[] fileData = file.getBytes();
		
		// Sanitize filename
		String sanitizedFileName = sanitizeFileName(fileNname);
		
		FileMetaData fileMetaData = new FileMetaData(fileID,
				sanitizedFileName, LocalDateTime.now(), file.getSize(), file.getContentType(), metaData, fileData);
		fileStorage.put(fileID, fileMetaData);
		return ResponseEntity.ok(Map.of("file_id",fileID));
	} catch(Exception e){
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error","Failed to upload the file"));
	}
}

@DeleteMapping("/files/{fileID}")
public ResponseEntity<?> deleteFile(@PathVariable String fileID){
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
			FileMetaData fileMetaData = fileStorage.get(fileID);
			if (fileMetaData != null) {
				if (file != null) {
					// Apply same security validations for updates
					ResponseEntity<Map<String,String>> validationError = validateFileUpload(file, fileMetaData.getFileName());
					if (validationError != null) {
						return validationError;
					}
					
					fileMetaData.setData(file.getBytes());
					fileMetaData.setSize(file.getSize());
					fileMetaData.setContentType(file.getContentType());
				}
				if (metaData != null) {
					// Validate metadata
					if (validateMetadata(metaData)) {
						fileMetaData.getMetadata().putAll(metaData);
					} else {
						return ResponseEntity.badRequest()
								.body(Map.of("error", "Invalid metadata"));
					}
				}
				return ResponseEntity.ok(fileMetaData.getMetadata());
			} else {
				return ResponseEntity.notFound().build();
			}
		} catch (Exception e){
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error","Failed to update the file"));

		}
}

	/**
	 * Validates file upload for security concerns
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
					.body(Map.of("error", "File is empty"));
		}
		
		// Check file type by content type
		String contentType = file.getContentType();
		if (contentType == null || !ALLOWED_FILE_TYPES.contains(contentType.toLowerCase())) {
			return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
					.body(Map.of("error", "File type not allowed. Allowed types: " + ALLOWED_FILE_TYPES));
		}
		
		// Check file extension
		String fileExtension = getFileExtension(fileName);
		if (!ALLOWED_FILE_EXTENSIONS.contains(fileExtension.toLowerCase())) {
			return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
					.body(Map.of("error", "File extension not allowed. Allowed extensions: " + ALLOWED_FILE_EXTENSIONS));
		}
		
		return null; // No validation errors
	}
	
	/**
	 * Sanitizes filename to prevent path traversal and other attacks
	 */
	private String sanitizeFileName(String fileName) {
		if (fileName == null || fileName.trim().isEmpty()) {
			return "unnamed_file.txt";
		}
		
		// Remove path traversal characters and keep only the filename
		String sanitized = new File(fileName).getName();
		
		// Remove any characters that aren't alphanumeric, dots, underscores, or hyphens
		sanitized = sanitized.replaceAll("[^a-zA-Z0-9._-]", "_");
		
		// Ensure filename isn't too long
		if (sanitized.length() > 255) {
			String extension = getFileExtension(sanitized);
			String nameWithoutExt = sanitized.substring(0, sanitized.lastIndexOf('.'));
			sanitized = nameWithoutExt.substring(0, 255 - extension.length()) + extension;
		}
		
		// Ensure it's not empty after sanitization
		if (sanitized.trim().isEmpty()) {
			sanitized = "sanitized_file.txt";
		}
		
		return sanitized;
	}
	
	/**
	 * Gets file extension from filename
	 */
	private String getFileExtension(String fileName) {
		if (fileName == null || !fileName.contains(".")) {
			return "";
		}
		return fileName.substring(fileName.lastIndexOf("."));
	}
	
	/**
	 * Validates metadata to prevent injection attacks
	 */
	private boolean validateMetadata(Map<String, String> metadata) {
		if (metadata == null) {
			return true;
		}
		
		// Check for reasonable limits and safe content
		for (Map.Entry<String, String> entry : metadata.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			
			// Check key and value lengths
			if (key == null || key.length() > 100 || value == null || value.length() > 500) {
				return false;
			}
			
			// Check for potentially dangerous content (basic check)
			if (containsSuspiciousContent(key) || containsSuspiciousContent(value)) {
				return false;
			}
		}
		
		return true;
	}
	
	/**
	 * Checks for potentially dangerous content in strings
	 */
	private boolean containsSuspiciousContent(String content) {
		if (content == null) return false;
		
		String lower = content.toLowerCase();
		// Basic check for script injection patterns
		return lower.contains("<script") || lower.contains("javascript:") || 
			   lower.contains("on") || lower.contains("eval(") ||
			   lower.contains("../") || lower.contains("..\\");
	}
	
	/**
	 * Adds basic security headers to responses
	 */
	private <T> ResponseEntity<T> addSecurityHeaders(ResponseEntity<T> response) {
		return ResponseEntity.status(response.getStatusCode())
				.headers(response.getHeaders())
				.header("X-Content-Type-Options", "nosniff")
				.header("X-Frame-Options", "DENY")
				.header("X-XSS-Protection", "1; mode=block")
				.header("Cache-Control", "no-cache, no-store, must-revalidate")
				.header("Pragma", "no-cache")
				.body(response.getBody());
	}


}
