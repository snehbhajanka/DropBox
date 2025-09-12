package com.dropbox.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@SpringBootApplication
@RestController
public class DropboxApplication {

	private static final Map<String,FileMetaData> fileStorage=new HashMap<>();
	private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB limit

	public static void main(String[] args) {

		SpringApplication.run(DropboxApplication.class, args);
	}

	/**
	 * Handle file upload size exceeded exceptions
	 */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<Map<String, String>> handleMaxSizeException(MaxUploadSizeExceededException exc) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(Map.of("error", "File size exceeds maximum allowed size of " + (MAX_FILE_SIZE / 1024 / 1024) + "MB"));
	}

	/**
	 * Sanitizes filename to prevent header injection and path traversal attacks
	 */
	private String sanitizeFilename(String filename) {
		if (filename == null) {
			return "download";
		}
		// Remove path traversal sequences and control characters
		String sanitized = filename.replaceAll("[\\r\\n\\t\\f\\v]", "")  // Remove all control chars
				.replaceAll("\\\\r", "")  // Remove escaped \r
				.replaceAll("\\\\n", "")  // Remove escaped \n
				.replaceAll("\\.\\./", "")
				.replaceAll("^\\.+", "")
				// Remove common header injection patterns
				.replaceAll("(?i)content-type:", "")
				.replaceAll("(?i)content-disposition:", "")
				.replaceAll("(?i)set-cookie:", "")
				.replaceAll("(?i)location:", "")
				.replaceAll("[;:\"'\\\\]", "_")  // Replace problematic characters including backslash
				.trim();
		
		// If filename becomes empty after sanitization, provide a default
		if (sanitized.isEmpty()) {
			return "download";
		}
		
		// Limit filename length to prevent excessively long filenames
		if (sanitized.length() > 255) {
			sanitized = sanitized.substring(0, 255);
		}
		
		return sanitized;
	}

	/**
	 * Validates file size to prevent resource exhaustion attacks
	 */
	private boolean isValidFileSize(long size) {
		return size > 0 && size <= MAX_FILE_SIZE;
	}

	/**
	 * Validates file ID format to prevent injection attacks
	 */
	private boolean isValidFileID(String fileID) {
		if (fileID == null || fileID.trim().isEmpty()) {
			return false;
		}
		// UUID format validation (basic)
		return fileID.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
	}


@GetMapping("/files")
public ResponseEntity<Map<String, Object>> listFiles(){
	if(fileStorage!=null && fileStorage.values()!=null && !fileStorage.values().isEmpty())
		return ResponseEntity.ok(Map.of("files",fileStorage.values()));
	else return ResponseEntity.ok(Map.of("status","No files to display"));
}

@GetMapping("/files/{fileID}")
public ResponseEntity<byte[]> readFile(@PathVariable String fileID){
	// Validate file ID format
	if (!isValidFileID(fileID)) {
		return ResponseEntity.badRequest().build();
	}
	
	FileMetaData file=fileStorage.get(fileID);
	if(file!=null){
		// Sanitize filename to prevent header injection attacks
		String sanitizedFilename = sanitizeFilename(file.getFileName());
		return ResponseEntity.ok()
				.header("Content-Disposition", "attachment; filename=\"" + sanitizedFilename + "\"")
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
		// Validate file size
		if (!isValidFileSize(file.getSize())) {
			return ResponseEntity.badRequest()
					.body(Map.of("error","File size exceeds maximum allowed size of " + (MAX_FILE_SIZE / 1024 / 1024) + "MB"));
		}
		
		// Sanitize filename
		String sanitizedFileName = sanitizeFilename(fileName);
		if (sanitizedFileName.equals("download")) {
			return ResponseEntity.badRequest()
					.body(Map.of("error","Invalid filename provided"));
		}
		
		String fileID = UUID.randomUUID().toString();
		byte[] fileData = file.getBytes();
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
	// Validate file ID format
	if (!isValidFileID(fileID)) {
		return ResponseEntity.badRequest().build();
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
		// Validate file ID format
		if (!isValidFileID(fileID)) {
			return ResponseEntity.badRequest().build();
		}
		
		FileMetaData fileMetaData = fileStorage.get(fileID);
		if (fileMetaData != null) {
			if (file != null) {
				// Validate file size for updates
				if (!isValidFileSize(file.getSize())) {
					return ResponseEntity.badRequest()
							.body(Map.of("error","File size exceeds maximum allowed size of " + (MAX_FILE_SIZE / 1024 / 1024) + "MB"));
				}
				fileMetaData.setData(file.getBytes());
				fileMetaData.setSize(file.getSize());
				fileMetaData.setContentType(file.getContentType());
			}
			if (metaData != null) {
				fileMetaData.getMetadata().putAll(metaData);
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


}
