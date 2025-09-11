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

@SpringBootApplication
@RestController
public class DropboxApplication {

	private static final Map<String, Map<String, FileMetaData>> accountFileStorage = new HashMap<>();

	private Map<String, FileMetaData> getAccountStorage(String accountId) {
		return accountFileStorage.computeIfAbsent(accountId, k -> new HashMap<>());
	}

	public static void main(String[] args) {

		SpringApplication.run(DropboxApplication.class, args);
	}


@GetMapping("/files")
public ResponseEntity<Map<String, Object>> listFiles(@RequestParam(value = "account_id", defaultValue = "0") String accountId){
	Map<String, FileMetaData> accountFiles = getAccountStorage(accountId);
	if(accountFiles != null && !accountFiles.values().isEmpty())
		return ResponseEntity.ok(Map.of("files", accountFiles.values()));
	else return ResponseEntity.ok(Map.of("status","No files to display"));
}

@GetMapping("/files/{fileID}")
public ResponseEntity<byte[]> readFile(@PathVariable String fileID, @RequestParam(value = "account_id", defaultValue = "0") String accountId){
	Map<String, FileMetaData> accountFiles = getAccountStorage(accountId);
	FileMetaData file = accountFiles.get(fileID);
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
		@RequestParam("file_name") String fileNname,
		@RequestParam(value = "account_id", defaultValue = "0") String accountId,
		@RequestParam(value = "metadata",required = false) Map<String,String> metaData){
	try {
		String fileID = UUID.randomUUID().toString();
		byte[] fileData = file.getBytes();
		FileMetaData fileMetaData = new FileMetaData(fileID, accountId,
				fileNname, LocalDateTime.now(), file.getSize(), file.getContentType(), metaData, fileData);
		Map<String, FileMetaData> accountFiles = getAccountStorage(accountId);
		accountFiles.put(fileID, fileMetaData);
		return ResponseEntity.ok(Map.of("file_id",fileID));
	} catch(Exception e){
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error","Failed to upload the file"));
	}
}

@DeleteMapping("/files/{fileID}")
public ResponseEntity<?> deleteFile(@PathVariable String fileID, @RequestParam(value = "account_id", defaultValue = "0") String accountId){
	Map<String, FileMetaData> accountFiles = getAccountStorage(accountId);
	if(accountFiles.containsKey(fileID)){
		accountFiles.remove(fileID);
		return ResponseEntity.ok(Map.of("message","File deleted successfully"));
	} else {
		return  ResponseEntity.notFound().build();
	}
}

@PutMapping("/files/{fileID}")
public ResponseEntity<?> updateFile(@PathVariable String fileID,
									@RequestParam(value = "account_id", defaultValue = "0") String accountId,
									@RequestParam(value="file",required = false) MultipartFile file,
									@RequestParam(value ="metadata",required = false) Map<String,String> metaData){
		try {
			Map<String, FileMetaData> accountFiles = getAccountStorage(accountId);
			FileMetaData fileMetaData = accountFiles.get(fileID);
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
