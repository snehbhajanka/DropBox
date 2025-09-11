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
public class AccountZeroTest {

    @LocalServerPort
    private int port;

    private TestRestTemplate restTemplate = new TestRestTemplate();

    @Test
    void testAccountZeroIsolation() {
        String baseUrl = "http://localhost:" + port;
        
        // Test specifically for Account ID "0" as mentioned in the issue
        String accountZero = "0";
        String accountOne = "1";
        
        // Upload file for Account 0
        String fileIdAccount0 = uploadFile(baseUrl, accountZero, "account0_file.txt", "Account 0 Content");
        assertNotNull(fileIdAccount0);
        
        // Upload file for Account 1
        String fileIdAccount1 = uploadFile(baseUrl, accountOne, "account1_file.txt", "Account 1 Content");
        assertNotNull(fileIdAccount1);
        
        // Test that Account 0 can only access their own files
        ResponseEntity<Map> listResponseAccount0 = restTemplate.getForEntity(
            baseUrl + "/files?account_id=" + accountZero, Map.class);
        assertEquals(HttpStatus.OK, listResponseAccount0.getStatusCode());
        assertTrue(listResponseAccount0.getBody().containsKey("files"));
        
        // Test that Account 0 cannot access Account 1's files
        ResponseEntity<byte[]> crossAccessResponse = restTemplate.getForEntity(
            baseUrl + "/files/" + fileIdAccount1 + "?account_id=" + accountZero, byte[].class);
        assertEquals(HttpStatus.NOT_FOUND, crossAccessResponse.getStatusCode());
        
        // Test that Account 0 can access their own file
        ResponseEntity<byte[]> ownFileResponse = restTemplate.getForEntity(
            baseUrl + "/files/" + fileIdAccount0 + "?account_id=" + accountZero, byte[].class);
        assertEquals(HttpStatus.OK, ownFileResponse.getStatusCode());
        assertEquals("Account 0 Content", new String(ownFileResponse.getBody()));
        
        // Test that Account 0 cannot delete Account 1's file
        restTemplate.delete(baseUrl + "/files/" + fileIdAccount1 + "?account_id=" + accountZero);
        
        // Verify Account 1's file still exists by accessing it with correct account
        ResponseEntity<byte[]> verifyAccount1File = restTemplate.getForEntity(
            baseUrl + "/files/" + fileIdAccount1 + "?account_id=" + accountOne, byte[].class);
        assertEquals(HttpStatus.OK, verifyAccount1File.getStatusCode());
        assertEquals("Account 1 Content", new String(verifyAccount1File.getBody()));
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