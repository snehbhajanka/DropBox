package com.dropbox.application;

import com.dropbox.application.service.S3StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SpringBootApplication
@RestController
public class DropboxApplication {

	// Keep metadata in memory for now, but store actual files in S3
	private static final Map<String,FileMetaData> fileMetadata = new HashMap<>();
	
	@Autowired
	private S3StorageService s3StorageService;

	public static void main(String[] args) {
		SpringApplication.run(DropboxApplication.class, args);
	}


@GetMapping("/files")
public ResponseEntity<Map<String, Object>> listFiles(){
	try {
		if(fileMetadata != null && !fileMetadata.values().isEmpty()) {
			return ResponseEntity.ok(Map.of("files", fileMetadata.values()));
		} else {
			return ResponseEntity.ok(Map.of("status", "No files to display"));
		}
	} catch (Exception e) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error", "Failed to list files"));
	}
}

@GetMapping("/files/{fileID}")
public ResponseEntity<byte[]> readFile(@PathVariable String fileID){
	try {
		FileMetaData file = fileMetadata.get(fileID);
		if(file != null && s3StorageService.fileExists(fileID)){
			byte[] data = s3StorageService.downloadFile(fileID);
			return ResponseEntity.ok()
					.header("Content-Disposition", "attachment; filename=" + file.getFileName())
					.header("Content-Type", file.getContentType())
					.body(data);
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
		String fileID = UUID.randomUUID().toString();
		byte[] fileData = file.getBytes();
		
		// Upload to S3 with security controls
		s3StorageService.uploadFile(fileID, fileData, file.getContentType(), metaData);
		
		// Store metadata in memory (without file data for memory efficiency)
		FileMetaData fileMetaData = new FileMetaData(fileID,
				fileName, LocalDateTime.now(), file.getSize(), file.getContentType(), metaData, null);
		fileMetadata.put(fileID, fileMetaData);
		
		return ResponseEntity.ok(Map.of("file_id", fileID));
	} catch(Exception e){
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error","Failed to upload the file: " + e.getMessage()));
	}
}

@DeleteMapping("/files/{fileID}")
public ResponseEntity<?> deleteFile(@PathVariable String fileID){
	try {
		if(fileMetadata.containsKey(fileID) && s3StorageService.fileExists(fileID)){
			s3StorageService.deleteFile(fileID);
			fileMetadata.remove(fileID);
			return ResponseEntity.ok(Map.of("message","File deleted successfully"));
		} else {
			return ResponseEntity.notFound().build();
		}
	} catch (Exception e) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error","Failed to delete the file"));
	}
}

@PutMapping("/files/{fileID}")
public ResponseEntity<?> updateFile(@PathVariable String fileID,
									@RequestParam(value="file",required = false) MultipartFile file,
									@RequestParam(value ="metadata",required = false) Map<String,String> metaData){
	try {
		FileMetaData fileMetaData = fileMetadata.get(fileID);
		if (fileMetaData != null && s3StorageService.fileExists(fileID)) {
			if (file != null) {
				// Upload new file data to S3
				s3StorageService.uploadFile(fileID, file.getBytes(), file.getContentType(), metaData);
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

/**
 * New endpoint to verify S3 security configuration
 * This helps ensure compliance and security
 */
@GetMapping("/security/verify")
public ResponseEntity<Map<String, Object>> verifySecurityConfiguration(){
	try {
		boolean isSecure = s3StorageService.verifySecurityConfiguration();
		return ResponseEntity.ok(Map.of(
			"secure", isSecure,
			"message", isSecure ? "S3 bucket is properly secured against public write access" : "Security configuration verification failed"
		));
	} catch (Exception e) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error", "Failed to verify security configuration"));
	}
}


}
