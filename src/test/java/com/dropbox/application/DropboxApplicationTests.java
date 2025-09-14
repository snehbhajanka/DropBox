package com.dropbox.application;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DropboxApplicationTests {

	@LocalServerPort
	private int port;

	private TestRestTemplate restTemplate = new TestRestTemplate();

	@Test
	void contextLoads() {
	}

	@Test
	void testSecurityValidations() {
		String baseUrl = "http://localhost:" + port;
		
		// Test 1: Valid file upload should work
		MultiValueMap<String, Object> validRequest = new LinkedMultiValueMap<>();
		validRequest.add("file", new ByteArrayResource("test content".getBytes()) {
			@Override
			public String getFilename() {
				return "test.txt";
			}
		});
		validRequest.add("file_name", "valid_file.txt");
		
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		HttpEntity<MultiValueMap<String, Object>> validEntity = new HttpEntity<>(validRequest, headers);
		
		ResponseEntity<String> validResponse = restTemplate.postForEntity(
			baseUrl + "/files/upload", validEntity, String.class);
		assertEquals(HttpStatus.OK, validResponse.getStatusCode());
		
		// Test 2: Invalid file name with directory traversal should be rejected
		MultiValueMap<String, Object> invalidRequest = new LinkedMultiValueMap<>();
		invalidRequest.add("file", new ByteArrayResource("test content".getBytes()) {
			@Override
			public String getFilename() {
				return "../malicious.txt";
			}
		});
		invalidRequest.add("file_name", "../malicious.txt");
		
		HttpEntity<MultiValueMap<String, Object>> invalidEntity = new HttpEntity<>(invalidRequest, headers);
		
		ResponseEntity<String> invalidResponse = restTemplate.postForEntity(
			baseUrl + "/files/upload", invalidEntity, String.class);
		assertEquals(HttpStatus.BAD_REQUEST, invalidResponse.getStatusCode());
		
		// Test 3: Invalid file ID format should be rejected
		ResponseEntity<String> invalidFileIdResponse = restTemplate.getForEntity(
			baseUrl + "/files/invalid-id", String.class);
		assertEquals(HttpStatus.BAD_REQUEST, invalidFileIdResponse.getStatusCode());
		
		// Test 4: Empty file should be rejected
		MultiValueMap<String, Object> emptyFileRequest = new LinkedMultiValueMap<>();
		emptyFileRequest.add("file", new ByteArrayResource(new byte[0]) {
			@Override
			public String getFilename() {
				return "empty.txt";
			}
		});
		emptyFileRequest.add("file_name", "empty.txt");
		
		HttpEntity<MultiValueMap<String, Object>> emptyFileEntity = new HttpEntity<>(emptyFileRequest, headers);
		
		ResponseEntity<String> emptyFileResponse = restTemplate.postForEntity(
			baseUrl + "/files/upload", emptyFileEntity, String.class);
		assertEquals(HttpStatus.BAD_REQUEST, emptyFileResponse.getStatusCode());
	}
}
