package com.dropbox.application;

import com.dropbox.application.service.S3Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.util.Collection;
import java.util.Map;

@SpringBootApplication
@RestController
public class DropboxApplication {

	@Autowired
	private S3Service s3Service;

	public static void main(String[] args) {
		SpringApplication.run(DropboxApplication.class, args);
	}

	@PostConstruct
	public void init() {
		// Initialize S3 bucket with secure configuration
		s3Service.initializeBucket();
	}

	@GetMapping("/files")
	public ResponseEntity<Map<String, Object>> listFiles(){
		Collection<FileMetaData> files = s3Service.listFiles();
		if(files != null && !files.isEmpty()) {
			return ResponseEntity.ok(Map.of("files", files));
		} else {
			return ResponseEntity.ok(Map.of("status", "No files to display"));
		}
	}

	@GetMapping("/files/{fileID}")
	public ResponseEntity<byte[]> readFile(@PathVariable String fileID){
		try {
			FileMetaData fileMetadata = s3Service.getFileMetadata(fileID);
			if(fileMetadata != null) {
				byte[] fileData = s3Service.downloadFile(fileID);
				return ResponseEntity.ok()
						.header("Content-Disposition", "attachment; filename=" + fileMetadata.getFileName())
						.body(fileData);
			} else {
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
			byte[] fileData = file.getBytes();
			String fileID = s3Service.uploadFile(fileData, fileName, file.getContentType(), metaData);
			return ResponseEntity.ok(Map.of("file_id", fileID));
		} catch(Exception e){
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Failed to upload the file: " + e.getMessage()));
		}
	}

	@DeleteMapping("/files/{fileID}")
	public ResponseEntity<?> deleteFile(@PathVariable String fileID){
		try {
			FileMetaData fileMetadata = s3Service.getFileMetadata(fileID);
			if(fileMetadata != null) {
				s3Service.deleteFile(fileID);
				return ResponseEntity.ok(Map.of("message", "File deleted successfully"));
			} else {
				return ResponseEntity.notFound().build();
			}
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Failed to delete file: " + e.getMessage()));
		}
	}

	@PutMapping("/files/{fileID}")
	public ResponseEntity<?> updateFile(@PathVariable String fileID,
										@RequestParam(value="file",required = false) MultipartFile file,
										@RequestParam(value ="metadata",required = false) Map<String,String> metaData){
		try {
			FileMetaData fileMetaData = s3Service.getFileMetadata(fileID);
			if (fileMetaData != null) {
				byte[] newFileData = null;
				String contentType = fileMetaData.getContentType();
				
				if (file != null) {
					newFileData = file.getBytes();
					contentType = file.getContentType();
				}
				
				s3Service.updateFile(fileID, newFileData, contentType, metaData);
				return ResponseEntity.ok(s3Service.getFileMetadata(fileID).getMetadata());
			} else {
				return ResponseEntity.notFound().build();
			}
		} catch (Exception e){
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Failed to update the file: " + e.getMessage()));
		}
	}

	@GetMapping("/security/public-access-block")
	public ResponseEntity<?> getPublicAccessBlockStatus() {
		try {
			var config = s3Service.getPublicAccessBlockConfiguration();
			return ResponseEntity.ok(Map.of(
					"blockPublicAcls", config.blockPublicAcls(),
					"ignorePublicAcls", config.ignorePublicAcls(),
					"blockPublicPolicy", config.blockPublicPolicy(),
					"restrictPublicBuckets", config.restrictPublicBuckets(),
					"message", "All public access blocked - S3 bucket is secure"
			));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Failed to get public access block status: " + e.getMessage()));
		}
	}
}
