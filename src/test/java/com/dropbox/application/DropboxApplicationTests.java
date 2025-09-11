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
	void testAccountIsolation() {
		String baseUrl = "http://localhost:" + port;
		
		// Test data for two different accounts
		String account1 = "account1";
		String account2 = "account2";
		
		// Upload file for account1
		String fileId1 = uploadFile(baseUrl, account1, "test1.txt", "Hello Account 1");
		assertNotNull(fileId1);
		
		// Upload file for account2
		String fileId2 = uploadFile(baseUrl, account2, "test2.txt", "Hello Account 2");
		assertNotNull(fileId2);
		
		// Test that account1 can only see their own files
		ResponseEntity<Map> listResponse1 = restTemplate.getForEntity(
			baseUrl + "/files?account_id=" + account1, Map.class);
		assertEquals(HttpStatus.OK, listResponse1.getStatusCode());
		assertTrue(listResponse1.getBody().containsKey("files"));
		
		// Test that account2 can only see their own files
		ResponseEntity<Map> listResponse2 = restTemplate.getForEntity(
			baseUrl + "/files?account_id=" + account2, Map.class);
		assertEquals(HttpStatus.OK, listResponse2.getStatusCode());
		assertTrue(listResponse2.getBody().containsKey("files"));
		
		// Test that account1 cannot access account2's file
		ResponseEntity<byte[]> readResponse = restTemplate.getForEntity(
			baseUrl + "/files/" + fileId2 + "?account_id=" + account1, byte[].class);
		assertEquals(HttpStatus.NOT_FOUND, readResponse.getStatusCode());
		
		// Test that account1 can access their own file
		ResponseEntity<byte[]> readOwnFile = restTemplate.getForEntity(
			baseUrl + "/files/" + fileId1 + "?account_id=" + account1, byte[].class);
		assertEquals(HttpStatus.OK, readOwnFile.getStatusCode());
		assertEquals("Hello Account 1", new String(readOwnFile.getBody()));
		
		// Test that account1 cannot delete account2's file
		restTemplate.delete(baseUrl + "/files/" + fileId2 + "?account_id=" + account1);
		
		// Verify account2's file still exists
		ResponseEntity<byte[]> verifyFile = restTemplate.getForEntity(
			baseUrl + "/files/" + fileId2 + "?account_id=" + account2, byte[].class);
		assertEquals(HttpStatus.OK, verifyFile.getStatusCode());
	}

	private String uploadFile(String baseUrl, String accountId, String fileName, String content) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);

		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", new ByteArrayResource(content.getBytes()) {
			@Override
			public String getFilename() {
				return fileName;
			}
		});
		body.add("file_name", fileName);
		body.add("account_id", accountId);

		HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
		
		ResponseEntity<Map> response = restTemplate.postForEntity(
			baseUrl + "/files/upload", requestEntity, Map.class);
		
		if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
			return (String) response.getBody().get("file_id");
		}
		return null;
	}
}
