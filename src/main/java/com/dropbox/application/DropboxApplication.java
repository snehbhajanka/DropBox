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
import java.util.regex.Pattern;

@SpringBootApplication
@RestController
public class DropboxApplication {

	private static final Map<String,FileMetaData> fileStorage=new HashMap<>();
	
	// Security constants
	private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB
	private static final Pattern SAFE_FILENAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");
	
	// Security validation methods
	String sanitizeFileName(String fileName) {
		if (fileName == null || fileName.trim().isEmpty()) {
			return "unnamed_file";
		}
		
		// Remove path traversal attempts
		fileName = fileName.replaceAll("\\.\\./", "").replaceAll("\\.\\./", "");
		
		// Remove dangerous characters and control characters
		fileName = fileName.replaceAll("[\\r\\n\\t\\u0000\\f\\u0008]", "");
		fileName = fileName.replaceAll("[<>:\"/\\\\|?*]", "_");
		
		// Limit length
		if (fileName.length() > 255) {
			fileName = fileName.substring(0, 255);
		}
		
		// Ensure it's not empty after sanitization
		if (fileName.trim().isEmpty()) {
			return "unnamed_file";
		}
		
		return fileName.trim();
	}
	
	String sanitizeHeaderValue(String value) {
		if (value == null) {
			return "";
		}
		// Remove CRLF injection attempts and control characters
		return value.replaceAll("[\\r\\n\\t\\u0000\\f\\u0008]", "");
	}
	
	boolean isValidFileSize(long size) {
		return size > 0 && size <= MAX_FILE_SIZE;
	}

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
	FileMetaData file=fileStorage.get(fileID);
	if(file!=null){
		// Sanitize filename to prevent header injection
		String safeFileName = sanitizeHeaderValue(file.getFileName());
		return ResponseEntity.ok()
				.header("Content-Disposition", "attachment; filename=\"" + safeFileName + "\"")
				.body(file.getData());
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
		// Validate file size
		if (!isValidFileSize(file.getSize())) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(Map.of("error", "File size exceeds maximum limit of " + (MAX_FILE_SIZE / 1024 / 1024) + " MB"));
		}
		
		// Sanitize file name to prevent path traversal and other attacks
		String sanitizedFileName = sanitizeFileName(fileNname);
		
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
