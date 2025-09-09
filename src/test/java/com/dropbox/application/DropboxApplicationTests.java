package com.dropbox.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DropboxApplicationTests {

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void contextLoads() {
	}

	@Test
	void testListFiles() {
		ResponseEntity<Map> response = restTemplate.getForEntity(
				"http://localhost:" + port + "/files", Map.class);
		
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		
		// The response should have either a "status" key (when empty) or "files" key (when not empty)
		assertTrue(response.getBody().containsKey("status") || response.getBody().containsKey("files"),
				"Response should contain either 'status' or 'files' key");
		
		if (response.getBody().containsKey("status")) {
			assertEquals("No files to display", response.getBody().get("status"));
		} else {
			assertNotNull(response.getBody().get("files"));
		}
	}

	@Test
	void testUploadAndRetrieveFile() {
		// Test file upload
		String testContent = "Hello, World!";
		String fileName = "test.txt";
		
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", new ByteArrayResource(testContent.getBytes()) {
			@Override
			public String getFilename() {
				return fileName;
			}
		});
		body.add("file_name", fileName);
		
		HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
		
		ResponseEntity<Map> uploadResponse = restTemplate.postForEntity(
				"http://localhost:" + port + "/files/upload", requestEntity, Map.class);
		
		assertEquals(HttpStatus.OK, uploadResponse.getStatusCode());
		assertNotNull(uploadResponse.getBody());
		String fileId = (String) uploadResponse.getBody().get("file_id");
		assertNotNull(fileId);
		
		// Test file retrieval
		ResponseEntity<byte[]> downloadResponse = restTemplate.getForEntity(
				"http://localhost:" + port + "/files/" + fileId, byte[].class);
		
		assertEquals(HttpStatus.OK, downloadResponse.getStatusCode());
		assertNotNull(downloadResponse.getBody());
		assertEquals(testContent, new String(downloadResponse.getBody()));
		
		// Test list files shows the uploaded file
		ResponseEntity<Map> listResponse = restTemplate.getForEntity(
				"http://localhost:" + port + "/files", Map.class);
		
		assertEquals(HttpStatus.OK, listResponse.getStatusCode());
		assertNotNull(listResponse.getBody());
		assertTrue(listResponse.getBody().containsKey("files"));
		assertNotNull(listResponse.getBody().get("files"));
	}

	@Test
	void testDeleteFile() {
		// First upload a file
		String testContent = "Delete me!";
		String fileName = "delete-test.txt";
		
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", new ByteArrayResource(testContent.getBytes()) {
			@Override
			public String getFilename() {
				return fileName;
			}
		});
		body.add("file_name", fileName);
		
		HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
		
		ResponseEntity<Map> uploadResponse = restTemplate.postForEntity(
				"http://localhost:" + port + "/files/upload", requestEntity, Map.class);
		
		String fileId = (String) uploadResponse.getBody().get("file_id");
		
		// Now delete the file
		restTemplate.delete("http://localhost:" + port + "/files/" + fileId);
		
		// Verify file is deleted
		ResponseEntity<byte[]> downloadResponse = restTemplate.getForEntity(
				"http://localhost:" + port + "/files/" + fileId, byte[].class);
		
		assertEquals(HttpStatus.NOT_FOUND, downloadResponse.getStatusCode());
	}

	@Test
	void testGetNonExistentFile() {
		ResponseEntity<byte[]> response = restTemplate.getForEntity(
				"http://localhost:" + port + "/files/non-existent-id", byte[].class);
		
		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
	}

	@Test
	void testDeleteNonExistentFile() {
		ResponseEntity<Map> response = restTemplate.exchange(
				"http://localhost:" + port + "/files/non-existent-id",
				HttpMethod.DELETE,
				null,
				Map.class);
		
		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
	}
}
