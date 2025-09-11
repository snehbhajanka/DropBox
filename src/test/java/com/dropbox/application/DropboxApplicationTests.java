package com.dropbox.application;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.core.io.ByteArrayResource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DropboxApplicationTests {

	@LocalServerPort
	private int port;

	private TestRestTemplate restTemplate = new TestRestTemplate();

	@Test
	void contextLoads() {
	}

	@Test
	void testAccountBasedFileStorage() {
		String baseUrl = "http://localhost:" + port;
		
		// Test Account ID 0 (default account)
		ResponseEntity<Map> responseAccount0 = restTemplate.getForEntity(baseUrl + "/files?account_id=0", Map.class);
		assertThat(responseAccount0.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(responseAccount0.getBody().get("status")).isEqualTo("No files to display");
		
		// Test another account
		ResponseEntity<Map> responseAccount1 = restTemplate.getForEntity(baseUrl + "/files?account_id=1", Map.class);
		assertThat(responseAccount1.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(responseAccount1.getBody().get("status")).isEqualTo("No files to display");
	}

	@Test
	void testFileUploadWithAccountId() {
		String baseUrl = "http://localhost:" + port;
		
		// Create test file
		ByteArrayResource fileContent = new ByteArrayResource("Test file content".getBytes()) {
			@Override
			public String getFilename() {
				return "test.txt";
			}
		};
		
		// Upload file to Account ID 0
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", fileContent);
		body.add("file_name", "test.txt");
		body.add("account_id", "0");
		
		ResponseEntity<Map> uploadResponse = restTemplate.postForEntity(baseUrl + "/files/upload", body, Map.class);
		assertThat(uploadResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(uploadResponse.getBody().get("file_id")).isNotNull();
		
		String fileId = (String) uploadResponse.getBody().get("file_id");
		
		// Verify file is accessible from Account ID 0
		ResponseEntity<byte[]> fileResponse = restTemplate.getForEntity(
			baseUrl + "/files/" + fileId + "?account_id=0", byte[].class);
		assertThat(fileResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(new String(fileResponse.getBody())).isEqualTo("Test file content");
		
		// Verify file is NOT accessible from Account ID 1
		ResponseEntity<byte[]> fileResponseAccount1 = restTemplate.getForEntity(
			baseUrl + "/files/" + fileId + "?account_id=1", byte[].class);
		assertThat(fileResponseAccount1.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

}
