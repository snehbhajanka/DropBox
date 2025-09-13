package com.dropbox.application;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@SpringBootApplication
@RestController
public class DropboxApplication {

	@Autowired
	private FileStorageService fileStorageService;

	public static void main(String[] args) {
		SpringApplication.run(DropboxApplication.class, args);
	}

@GetMapping("/files")
public ResponseEntity<Map<String, Object>> listFiles(){
	Map<String, FileMetaData> files = fileStorageService.listFiles();
	if(files != null && !files.isEmpty()) {
		return ResponseEntity.ok(Map.of("files", files.values()));
	} else {
		return ResponseEntity.ok(Map.of("status", "No files to display"));
	}
}

@GetMapping("/files/{fileID}")
public ResponseEntity<byte[]> readFile(@PathVariable String fileID){
	FileMetaData file = fileStorageService.getFile(fileID);
	if(file != null && file.getData() != null){
		return ResponseEntity.ok()
				.header("Content-Disposition", "attachment; filename=" + file.getFileName())
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
		byte[] fileData = file.getBytes();
		String fileID = fileStorageService.storeFile(
			fileName, 
			fileData, 
			file.getContentType(), 
			metaData
		);
		return ResponseEntity.ok(Map.of("file_id", fileID));
	} catch(Exception e){
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error","Failed to upload the file: " + e.getMessage()));
	}
}

@DeleteMapping("/files/{fileID}")
public ResponseEntity<?> deleteFile(@PathVariable String fileID){
	if(fileStorageService.deleteFile(fileID)){
		return ResponseEntity.ok(Map.of("message","File deleted successfully"));
	} else {
		return ResponseEntity.notFound().build();
	}
}

@PutMapping("/files/{fileID}")
public ResponseEntity<?> updateFile(@PathVariable String fileID,
									@RequestParam(value="file",required = false) MultipartFile file,
									@RequestParam(value ="metadata",required = false) Map<String,String> metaData){
	try {
		byte[] fileData = null;
		if (file != null) {
			fileData = file.getBytes();
		}
		
		if (fileStorageService.updateFile(fileID, fileData, metaData)) {
			FileMetaData updatedFile = fileStorageService.getFile(fileID);
			return ResponseEntity.ok(updatedFile != null ? updatedFile.getMetadata() : Map.of("status", "updated"));
		} else {
			return ResponseEntity.notFound().build();
		}
	} catch (Exception e){
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error","Failed to update the file: " + e.getMessage()));
	}
}

@GetMapping("/storage/stats")
public ResponseEntity<Map<String, Object>> getStorageStats() {
	try {
		Map<String, Object> stats = fileStorageService.getStorageStats();
		return ResponseEntity.ok(stats);
	} catch (Exception e) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error", "Failed to get storage stats: " + e.getMessage()));
	}
}

@GetMapping("/security/status")
public ResponseEntity<Map<String, Object>> getSecurityStatus() {
	try {
		Map<String, Object> stats = fileStorageService.getStorageStats();
		Map<String, Object> securityStatus = new HashMap<>();
		securityStatus.put("storage_security", stats.get("s3_security_status"));
		securityStatus.put("storage_type", stats.get("storage_type"));
		securityStatus.put("s3_enabled", stats.get("s3_enabled"));
		securityStatus.put("timestamp", LocalDateTime.now());
		return ResponseEntity.ok(securityStatus);
	} catch (Exception e) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error", "Failed to get security status: " + e.getMessage()));
	}
}

}
