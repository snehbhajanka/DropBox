package com.dropbox.application;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "logging.level.org.springframework.web=DEBUG")
class DropboxApplicationTests {

	@LocalServerPort
	private int port;

	private final RestTemplate restTemplate = new RestTemplate();

	private String getBaseUrl() {
		return "http://localhost:" + port;
	}

	@Test
	void contextLoads() {
	}

	@Test
	void testListFilesEmpty() {
		// Clear any existing files first by getting the current list and deleting them
		String listUrl = getBaseUrl() + "/files";
		ResponseEntity<Map> currentResponse = restTemplate.getForEntity(listUrl, Map.class);
		
		if (currentResponse.getBody().containsKey("files")) {
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> files = (List<Map<String, Object>>) currentResponse.getBody().get("files");
			for (Map<String, Object> file : files) {
				String fileId = (String) file.get("fileID");
				String deleteUrl = getBaseUrl() + "/files/" + fileId;
				restTemplate.exchange(deleteUrl, HttpMethod.DELETE, null, Map.class);
			}
		}
		
		// Now test empty list
		ResponseEntity<Map> response = restTemplate.getForEntity(listUrl, Map.class);
		
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertEquals("No files to display", response.getBody().get("status"));
	}

	@Test
	void testUploadAndListFiles() {
		// First verify empty list
		String listUrl = getBaseUrl() + "/files";
		ResponseEntity<Map> emptyResponse = restTemplate.getForEntity(listUrl, Map.class);
		assertEquals(HttpStatus.OK, emptyResponse.getStatusCode());
		assertEquals("No files to display", emptyResponse.getBody().get("status"));
		
		// Upload a file
		String uploadUrl = getBaseUrl() + "/files/upload";
		
		String content = "Hello, World!";
		ByteArrayResource fileResource = new ByteArrayResource(content.getBytes()) {
			@Override
			public String getFilename() {
				return "test.txt";
			}
		};
		
		HttpHeaders headers = new HttpHeaders();
		headers.set("Content-Type", "multipart/form-data");
		
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", fileResource);
		body.add("file_name", "test.txt");
		
		HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
		
		ResponseEntity<Map> uploadResponse = restTemplate.postForEntity(uploadUrl, requestEntity, Map.class);
		
		assertEquals(HttpStatus.OK, uploadResponse.getStatusCode());
		assertNotNull(uploadResponse.getBody());
		assertTrue(uploadResponse.getBody().containsKey("file_id"));
		
		// Now verify list contains files
		ResponseEntity<Map> listResponse = restTemplate.getForEntity(listUrl, Map.class);
		assertEquals(HttpStatus.OK, listResponse.getStatusCode());
		assertTrue(listResponse.getBody().containsKey("files"));
		
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> files = (List<Map<String, Object>>) listResponse.getBody().get("files");
		assertFalse(files.isEmpty());
	}

	@Test
	void testDownloadNonExistentFile() {
		String url = getBaseUrl() + "/files/non-existent-id";
		
		try {
			ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
			fail("Expected NotFound exception");
		} catch (Exception e) {
			assertTrue(e.getMessage().contains("404"));
		}
	}

	@Test
	void testDeleteNonExistentFile() {
		String url = getBaseUrl() + "/files/non-existent-id";
		
		try {
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.DELETE, null, String.class);
			fail("Expected NotFound exception");
		} catch (Exception e) {
			assertTrue(e.getMessage().contains("404"));
		}
	}

	@Test
	void testUpdateNonExistentFile() {
		String url = getBaseUrl() + "/files/non-existent-id";
		
		HttpHeaders headers = new HttpHeaders();
		headers.set("Content-Type", "multipart/form-data");
		
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		
		HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
		
		try {
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, requestEntity, String.class);
			fail("Expected NotFound exception");
		} catch (Exception e) {
			assertTrue(e.getMessage().contains("404"));
		}
	}

	@Test
	void testCompleteFileLifecycle() {
		// 1. Upload a file
		String uploadUrl = getBaseUrl() + "/files/upload";
		
		String content = "Test file content";
		ByteArrayResource fileResource = new ByteArrayResource(content.getBytes()) {
			@Override
			public String getFilename() {
				return "lifecycle-test.txt";
			}
		};
		
		HttpHeaders headers = new HttpHeaders();
		headers.set("Content-Type", "multipart/form-data");
		
		MultiValueMap<String, Object> uploadBody = new LinkedMultiValueMap<>();
		uploadBody.add("file", fileResource);
		uploadBody.add("file_name", "lifecycle-test.txt");
		
		HttpEntity<MultiValueMap<String, Object>> uploadRequest = new HttpEntity<>(uploadBody, headers);
		
		ResponseEntity<Map> uploadResponse = restTemplate.postForEntity(uploadUrl, uploadRequest, Map.class);
		
		assertEquals(HttpStatus.OK, uploadResponse.getStatusCode());
		String fileId = (String) uploadResponse.getBody().get("file_id");
		assertNotNull(fileId);
		
		// 2. Download the file
		String downloadUrl = getBaseUrl() + "/files/" + fileId;
		
		ResponseEntity<byte[]> downloadResponse = restTemplate.getForEntity(downloadUrl, byte[].class);
		
		assertEquals(HttpStatus.OK, downloadResponse.getStatusCode());
		assertArrayEquals(content.getBytes(), downloadResponse.getBody());
		
		// 3. Update the file (just sending empty body to test the endpoint)
		String updateUrl = getBaseUrl() + "/files/" + fileId;
		
		MultiValueMap<String, Object> updateBody = new LinkedMultiValueMap<>();
		
		HttpEntity<MultiValueMap<String, Object>> updateRequest = new HttpEntity<>(updateBody, headers);
		
		ResponseEntity<Map> updateResponse = restTemplate.exchange(updateUrl, HttpMethod.PUT, updateRequest, Map.class);
		
		assertEquals(HttpStatus.OK, updateResponse.getStatusCode());
		
		// 4. Delete the file
		String deleteUrl = getBaseUrl() + "/files/" + fileId;
		
		ResponseEntity<Map> deleteResponse = restTemplate.exchange(deleteUrl, HttpMethod.DELETE, null, Map.class);
		
		assertEquals(HttpStatus.OK, deleteResponse.getStatusCode());
		assertEquals("File deleted successfully", deleteResponse.getBody().get("message"));
		
		// 5. Verify file is deleted
		try {
			ResponseEntity<String> verifyResponse = restTemplate.getForEntity(downloadUrl, String.class);
			fail("Expected NotFound exception");
		} catch (Exception e) {
			assertTrue(e.getMessage().contains("404"));
		}
	}
}
