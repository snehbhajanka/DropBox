package com.dropbox.application;

import com.dropbox.application.service.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@SpringBootApplication
@RestController
@EnableConfigurationProperties
public class DropboxApplication {

	@Autowired
	private StorageService storageService;

	public static void main(String[] args) {
		SpringApplication.run(DropboxApplication.class, args);
	}

	@GetMapping("/files")
	public ResponseEntity<Map<String, Object>> listFiles(){
		var files = storageService.listFiles();
		if(files != null && !files.isEmpty()) {
			return ResponseEntity.ok(Map.of("files", files));
		} else {
			return ResponseEntity.ok(Map.of("status", "No files to display"));
		}
	}

	@GetMapping("/files/{fileID}")
	public ResponseEntity<byte[]> readFile(@PathVariable String fileID){
		FileMetaData file = storageService.getFile(fileID);
		if(file != null){
			return ResponseEntity.ok()
					.header("Content-Disposition", "attachment; filename=" + file.getFileName())
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
			String fileID = storageService.uploadFile(fileData, fileName, file.getContentType(), metaData);
			return ResponseEntity.ok(Map.of("file_id", fileID));
		} catch(Exception e){
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Failed to upload the file: " + e.getMessage()));
		}
	}

	@DeleteMapping("/files/{fileID}")
	public ResponseEntity<?> deleteFile(@PathVariable String fileID){
		if(storageService.deleteFile(fileID)){
			return ResponseEntity.ok(Map.of("message", "File deleted successfully"));
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
			String contentType = null;
			
			if (file != null) {
				fileData = file.getBytes();
				contentType = file.getContentType();
			}
			
			if (storageService.updateFile(fileID, fileData, contentType, metaData)) {
				FileMetaData updatedFile = storageService.getFile(fileID);
				return ResponseEntity.ok(updatedFile != null ? updatedFile.getMetadata() : Map.of("status", "updated"));
			} else {
				return ResponseEntity.notFound().build();
			}
		} catch (Exception e){
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Failed to update the file: " + e.getMessage()));
		}
	}
}
