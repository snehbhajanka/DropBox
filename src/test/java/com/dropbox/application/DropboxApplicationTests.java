package com.dropbox.application;

import org.junit.jupiter.api.Test;
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

	private TestRestTemplate restTemplate = new TestRestTemplate();

	@Test
	void contextLoads() {
	}

	@Test
	void testHttpHeaderInjectionVulnerability() {
		// Test for HTTP Header Injection vulnerability in filename
		String maliciousFilename = "test.txt\r\nX-Injected-Header: malicious-value";
		
		// Upload a file with malicious filename
		MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
		parts.add("file", new ByteArrayResource("test content".getBytes()) {
			@Override
			public String getFilename() {
				return "test.txt";
			}
		});
		parts.add("file_name", maliciousFilename);
		
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		
		HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(parts, headers);
		
		// Upload the file
		ResponseEntity<Map> uploadResponse = restTemplate.postForEntity(
			"http://localhost:" + port + "/files/upload", 
			requestEntity, 
			Map.class
		);
		
		assertEquals(HttpStatus.OK, uploadResponse.getStatusCode());
		String fileId = (String) uploadResponse.getBody().get("file_id");
		assertNotNull(fileId);
		
		// Download the file and check for header injection
		ResponseEntity<byte[]> downloadResponse = restTemplate.getForEntity(
			"http://localhost:" + port + "/files/" + fileId, 
			byte[].class
		);
		
		assertEquals(HttpStatus.OK, downloadResponse.getStatusCode());
		
		// Check if the malicious header was injected
		String contentDisposition = downloadResponse.getHeaders().getFirst("Content-Disposition");
		assertNotNull(contentDisposition);
		
		// After the fix: the malicious content should be sanitized
		// Newline characters should be replaced with underscores
		assertFalse(contentDisposition.contains("\r"), 
			"Content-Disposition header should not contain carriage return characters");
		assertFalse(contentDisposition.contains("\n"), 
			"Content-Disposition header should not contain line feed characters");
		
		// Should contain sanitized filename - check for the underscore replacement
		assertTrue(contentDisposition.contains("test.txt_"), 
			"Content-Disposition should contain sanitized filename with underscore replacing newline characters");
		
		// Ensure no separate injected header exists
		assertFalse(downloadResponse.getHeaders().containsKey("X-Injected-Header"), 
			"Response should not contain injected headers");
	}

}
