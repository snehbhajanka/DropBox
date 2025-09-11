package com.dropbox.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.core.io.ByteArrayResource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DropboxApplicationTests {

	@LocalServerPort
	private int port;

	private TestRestTemplate restTemplate = new TestRestTemplate();

	private String createURLWithPort(String uri) {
		return "http://localhost:" + port + uri;
	}

	@BeforeEach
	void setUp() {
		// Clear any existing files by restarting the application context if needed
		// Since we're using in-memory storage, each test class run starts fresh
	}

	@Test
	void contextLoads() {
	}

	@Test
	void testListFiles_EmptyStorage() {
		ResponseEntity<Map> response = restTemplate.getForEntity(
				createURLWithPort("/files"), Map.class);
		
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		
		// The response could either be empty (status message) or contain files from other tests
		// Let's check for both possibilities to make test order-independent
		if (response.getBody().containsKey("status")) {
			assertEquals("No files to display", response.getBody().get("status"));
		} else if (response.getBody().containsKey("files")) {
			// Files are present, which is also valid
			assertNotNull(response.getBody().get("files"));
		} else {
			fail("Response should contain either 'status' or 'files' key");
		}
	}

	@Test
	void testUploadFile_Success() {
		// Prepare file upload request
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);

		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", new ByteArrayResource("test file content".getBytes()) {
			@Override
			public String getFilename() {
				return "test.txt";
			}
		});
		body.add("file_name", "test.txt");

		HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

		ResponseEntity<Map> response = restTemplate.postForEntity(
				createURLWithPort("/files/upload"), requestEntity, Map.class);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertNotNull(response.getBody());
		assertTrue(response.getBody().containsKey("file_id"));
		assertNotNull(response.getBody().get("file_id"));
	}

	@Test
	void testUploadAndListFiles() {
		// First upload a file
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);

		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", new ByteArrayResource("test file content".getBytes()) {
			@Override
			public String getFilename() {
				return "test.txt";
			}
		});
		body.add("file_name", "test.txt");

		HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
		ResponseEntity<Map> uploadResponse = restTemplate.postForEntity(
				createURLWithPort("/files/upload"), requestEntity, Map.class);

		assertEquals(HttpStatus.OK, uploadResponse.getStatusCode());
		String fileId = (String) uploadResponse.getBody().get("file_id");

		// Then list files
		ResponseEntity<Map> listResponse = restTemplate.getForEntity(
				createURLWithPort("/files"), Map.class);

		assertEquals(HttpStatus.OK, listResponse.getStatusCode());
		assertNotNull(listResponse.getBody());
		assertTrue(listResponse.getBody().containsKey("files"));
	}

	@Test
	void testDownloadFile_Success() {
		// First upload a file
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);

		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		String testContent = "test file content";
		body.add("file", new ByteArrayResource(testContent.getBytes()) {
			@Override
			public String getFilename() {
				return "test.txt";
			}
		});
		body.add("file_name", "test.txt");

		HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
		ResponseEntity<Map> uploadResponse = restTemplate.postForEntity(
				createURLWithPort("/files/upload"), requestEntity, Map.class);

		String fileId = (String) uploadResponse.getBody().get("file_id");

		// Then download the file
		ResponseEntity<byte[]> downloadResponse = restTemplate.getForEntity(
				createURLWithPort("/files/" + fileId), byte[].class);

		assertEquals(HttpStatus.OK, downloadResponse.getStatusCode());
		assertNotNull(downloadResponse.getBody());
		assertEquals(testContent, new String(downloadResponse.getBody()));
		assertTrue(downloadResponse.getHeaders().getFirst("Content-Disposition").contains("test.txt"));
	}

	@Test
	void testDownloadFile_NotFound() {
		ResponseEntity<byte[]> response = restTemplate.getForEntity(
				createURLWithPort("/files/nonexistent-id"), byte[].class);

		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
	}

	@Test
	void testDeleteFile_Success() {
		// First upload a file
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);

		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", new ByteArrayResource("test file content".getBytes()) {
			@Override
			public String getFilename() {
				return "test.txt";
			}
		});
		body.add("file_name", "test.txt");

		HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
		ResponseEntity<Map> uploadResponse = restTemplate.postForEntity(
				createURLWithPort("/files/upload"), requestEntity, Map.class);

		String fileId = (String) uploadResponse.getBody().get("file_id");

		// Then delete the file
		ResponseEntity<Map> deleteResponse = restTemplate.exchange(
				createURLWithPort("/files/" + fileId), HttpMethod.DELETE, null, Map.class);

		assertEquals(HttpStatus.OK, deleteResponse.getStatusCode());
		assertNotNull(deleteResponse.getBody());
		assertEquals("File deleted successfully", deleteResponse.getBody().get("message"));

		// Verify file is actually deleted by trying to download it
		ResponseEntity<byte[]> downloadResponse = restTemplate.getForEntity(
				createURLWithPort("/files/" + fileId), byte[].class);
		assertEquals(HttpStatus.NOT_FOUND, downloadResponse.getStatusCode());
	}

	@Test
	void testDeleteFile_NotFound() {
		ResponseEntity<Map> response = restTemplate.exchange(
				createURLWithPort("/files/nonexistent-id"), HttpMethod.DELETE, null, Map.class);

		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
	}

	@Test
	void testUpdateFile_Success() {
		// First upload a file
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);

		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", new ByteArrayResource("original content".getBytes()) {
			@Override
			public String getFilename() {
				return "test.txt";
			}
		});
		body.add("file_name", "test.txt");

		HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
		ResponseEntity<Map> uploadResponse = restTemplate.postForEntity(
				createURLWithPort("/files/upload"), requestEntity, Map.class);

		String fileId = (String) uploadResponse.getBody().get("file_id");

		// Then update the file
		MultiValueMap<String, Object> updateBody = new LinkedMultiValueMap<>();
		updateBody.add("file", new ByteArrayResource("updated content".getBytes()) {
			@Override
			public String getFilename() {
				return "updated.txt";
			}
		});

		HttpEntity<MultiValueMap<String, Object>> updateRequest = new HttpEntity<>(updateBody, headers);
		ResponseEntity<Map> updateResponse = restTemplate.exchange(
				createURLWithPort("/files/" + fileId), HttpMethod.PUT, updateRequest, Map.class);

		assertEquals(HttpStatus.OK, updateResponse.getStatusCode());

		// Verify the file was updated by downloading it
		ResponseEntity<byte[]> downloadResponse = restTemplate.getForEntity(
				createURLWithPort("/files/" + fileId), byte[].class);

		assertEquals(HttpStatus.OK, downloadResponse.getStatusCode());
		assertEquals("updated content", new String(downloadResponse.getBody()));
	}

	@Test
	void testUpdateFile_NotFound() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);

		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", new ByteArrayResource("content".getBytes()) {
			@Override
			public String getFilename() {
				return "test.txt";
			}
		});

		HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
		ResponseEntity<Map> response = restTemplate.exchange(
				createURLWithPort("/files/nonexistent-id"), HttpMethod.PUT, requestEntity, Map.class);

		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
	}

}
