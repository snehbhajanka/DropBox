package com.dropbox.application;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class SecurityTest {

    @LocalServerPort
    private int port;

    private TestRestTemplate restTemplate = new TestRestTemplate();

    @Test
    public void testValidFileUpload() {
        // Test normal file upload works
        byte[] fileContent = "test content".getBytes();
        
        String url = "http://localhost:" + port + "/files/upload";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(fileContent) {
            @Override
            public String getFilename() {
                return "test.txt";
            }
        });
        body.add("file_name", "test.txt");
        
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        
        ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("file_id"));
    }

    @Test
    public void testFileUploadWithMaliciousFilename() {
        // Test path traversal protection - should succeed but filename should be sanitized
        byte[] fileContent = "test content".getBytes();
        
        String url = "http://localhost:" + port + "/files/upload";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(fileContent) {
            @Override
            public String getFilename() {
                return "test.txt";
            }
        });
        body.add("file_name", "../../../etc/passwd");
        
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        
        ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("file_id"));
    }

    @Test
    public void testInvalidFileIdAccess() {
        // Test invalid file ID rejection
        String url = "http://localhost:" + port + "/files/invalid-file-id";
        
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        // Test delete with invalid ID
        ResponseEntity<String> deleteResponse = restTemplate.exchange(
            url, HttpMethod.DELETE, null, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, deleteResponse.getStatusCode());
    }

    @Test
    public void testHeaderInjectionPrevention() {
        // Upload a file with a filename containing newline characters
        byte[] fileContent = "test content".getBytes();
        
        String url = "http://localhost:" + port + "/files/upload";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(fileContent) {
            @Override
            public String getFilename() {
                return "test.txt";
            }
        });
        // Attempt header injection
        body.add("file_name", "test\r\nContent-Type: application/malicious\r\n");
        
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        
        ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
        
        // Should succeed but filename should be sanitized
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("file_id"));
        
        // Now try to download the file and check that headers are properly sanitized
        String fileId = response.getBody().replaceAll(".*\"file_id\":\"([^\"]+)\".*", "$1");
        
        ResponseEntity<byte[]> downloadResponse = restTemplate.getForEntity(
            "http://localhost:" + port + "/files/" + fileId, byte[].class);
        
        assertEquals(HttpStatus.OK, downloadResponse.getStatusCode());
        
        // Check that Content-Disposition header doesn't contain injected content
        String contentDisposition = downloadResponse.getHeaders().getFirst("Content-Disposition");
        assertNotNull(contentDisposition);
        
        // Debug: print the actual header value to understand what's happening
        System.out.println("Content-Disposition header: " + contentDisposition);
        
        // The header should not contain the original malicious content
        assertFalse(contentDisposition.contains("Content-Type: application/malicious"), 
                "Header injection was not prevented. Found: " + contentDisposition);
    }
}