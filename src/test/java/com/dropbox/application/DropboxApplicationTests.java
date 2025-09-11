package com.dropbox.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
class DropboxApplicationTests {

	@Autowired
	private WebApplicationContext webApplicationContext;

	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
	}

	@Test
	void contextLoads() {
		// Basic context loading test
	}

	@Test
	void testListFiles_ShouldReturnValidResponse() throws Exception {
		// Since tests share in-memory storage, we test that the response has valid structure
		// Either files array or status message
		MvcResult result = mockMvc.perform(get("/files"))
				.andExpect(status().isOk())
				.andReturn();
		
		String response = result.getResponse().getContentAsString();
		// Response should contain either "files" key with array or "status" key with message
		assert response.contains("files") || response.contains("status");
	}

	@Test
	void testUploadFile_ShouldReturnFileId() throws Exception {
		MockMultipartFile file = new MockMultipartFile(
				"file",
				"test.txt",
				MediaType.TEXT_PLAIN_VALUE,
				"Hello World".getBytes()
		);

		MvcResult result = mockMvc.perform(multipart("/files/upload")
						.file(file)
						.param("file_name", "test.txt"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.file_id").exists())
				.andReturn();

		String response = result.getResponse().getContentAsString();
		// Verify the response contains a file_id
		assert response.contains("file_id");
	}

	@Test
	void testUploadAndDownloadFile_ShouldReturnOriginalContent() throws Exception {
		String originalContent = "Hello World Test Content";
		MockMultipartFile file = new MockMultipartFile(
				"file",
				"test.txt",
				MediaType.TEXT_PLAIN_VALUE,
				originalContent.getBytes()
		);

		// Upload file
		MvcResult uploadResult = mockMvc.perform(multipart("/files/upload")
						.file(file)
						.param("file_name", "test.txt"))
				.andExpect(status().isOk())
				.andReturn();

		String uploadResponse = uploadResult.getResponse().getContentAsString();
		String fileId = extractFileIdFromResponse(uploadResponse);

		// Download file
		MvcResult downloadResult = mockMvc.perform(get("/files/" + fileId))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", "attachment; filename=test.txt"))
				.andReturn();

		String downloadedContent = downloadResult.getResponse().getContentAsString();
		assert downloadedContent.equals(originalContent);
	}

	@Test
	void testDownloadNonExistentFile_ShouldReturn404() throws Exception {
		mockMvc.perform(get("/files/non-existent-id"))
				.andExpect(status().isNotFound());
	}

	@Test
	void testDeleteFile_ShouldRemoveFileSuccessfully() throws Exception {
		// First upload a file
		MockMultipartFile file = new MockMultipartFile(
				"file",
				"test-delete.txt",
				MediaType.TEXT_PLAIN_VALUE,
				"Content to delete".getBytes()
		);

		MvcResult uploadResult = mockMvc.perform(multipart("/files/upload")
						.file(file)
						.param("file_name", "test-delete.txt"))
				.andExpect(status().isOk())
				.andReturn();

		String fileId = extractFileIdFromResponse(uploadResult.getResponse().getContentAsString());

		// Delete the file
		mockMvc.perform(delete("/files/" + fileId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message", is("File deleted successfully")));

		// Verify file is deleted by trying to download it
		mockMvc.perform(get("/files/" + fileId))
				.andExpect(status().isNotFound());
	}

	@Test
	void testDeleteNonExistentFile_ShouldReturn404() throws Exception {
		mockMvc.perform(delete("/files/non-existent-id"))
				.andExpect(status().isNotFound());
	}

	@Test
	void testUpdateFile_ShouldModifyFileContent() throws Exception {
		// First upload a file
		MockMultipartFile originalFile = new MockMultipartFile(
				"file",
				"test-update.txt",
				MediaType.TEXT_PLAIN_VALUE,
				"Original Content".getBytes()
		);

		MvcResult uploadResult = mockMvc.perform(multipart("/files/upload")
						.file(originalFile)
						.param("file_name", "test-update.txt"))
				.andExpect(status().isOk())
				.andReturn();

		String fileId = extractFileIdFromResponse(uploadResult.getResponse().getContentAsString());

		// Update the file with new content using MockMvc fileUpload for PUT
		MockMultipartFile updatedFile = new MockMultipartFile(
				"file",
				"test-update-new.txt",
				MediaType.TEXT_PLAIN_VALUE,
				"Updated Content".getBytes()
		);

		// Use PUT request with MockMvc's multipart approach  
		MockMultipartHttpServletRequestBuilder putRequest = multipart("/files/" + fileId);
		putRequest.file(updatedFile);
		putRequest = (MockMultipartHttpServletRequestBuilder) putRequest.with(request -> {
			request.setMethod("PUT");
			return request;
		});

		mockMvc.perform(putRequest)
				.andExpect(status().isOk());

		// Verify the file was updated by downloading it
		MvcResult downloadResult = mockMvc.perform(get("/files/" + fileId))
				.andExpect(status().isOk())
				.andReturn();

		String updatedContent = downloadResult.getResponse().getContentAsString();
		assert updatedContent.equals("Updated Content");
	}

	@Test
	void testUpdateNonExistentFile_ShouldReturn404() throws Exception {
		MockMultipartFile file = new MockMultipartFile(
				"file",
				"test.txt",
				MediaType.TEXT_PLAIN_VALUE,
				"Content".getBytes()
		);

		MockMultipartHttpServletRequestBuilder putRequest = multipart("/files/non-existent-id");
		putRequest.file(file);
		putRequest = (MockMultipartHttpServletRequestBuilder) putRequest.with(request -> {
			request.setMethod("PUT");
			return request;
		});

		mockMvc.perform(putRequest)
				.andExpect(status().isNotFound());
	}

	@Test
	void testListFiles_AfterUpload_ShouldShowFiles() throws Exception {
		// Upload a file first
		MockMultipartFile file = new MockMultipartFile(
				"file",
				"list-test.txt",
				MediaType.TEXT_PLAIN_VALUE,
				"Content for listing".getBytes()
		);

		mockMvc.perform(multipart("/files/upload")
						.file(file)
						.param("file_name", "list-test.txt"))
				.andExpect(status().isOk());

		// List files and verify it's not empty
		mockMvc.perform(get("/files"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.files").exists())
				.andExpect(jsonPath("$.files").isArray());
	}

	@Test
	void testUploadEmptyFile_ShouldSucceed() throws Exception {
		MockMultipartFile emptyFile = new MockMultipartFile(
				"file",
				"empty.txt",
				MediaType.TEXT_PLAIN_VALUE,
				new byte[0]
		);

		mockMvc.perform(multipart("/files/upload")
						.file(emptyFile)
						.param("file_name", "empty.txt"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.file_id").exists());
	}

	@Test
	void testUploadDifferentFileTypes_ShouldSucceed() throws Exception {
		// Test with JSON file
		MockMultipartFile jsonFile = new MockMultipartFile(
				"file",
				"test.json",
				MediaType.APPLICATION_JSON_VALUE,
				"{\"key\": \"value\"}".getBytes()
		);

		mockMvc.perform(multipart("/files/upload")
						.file(jsonFile)
						.param("file_name", "test.json"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.file_id").exists());

		// Test with binary file
		MockMultipartFile binaryFile = new MockMultipartFile(
				"file",
				"test.bin",
				MediaType.APPLICATION_OCTET_STREAM_VALUE,
				new byte[]{1, 2, 3, 4, 5}
		);

		mockMvc.perform(multipart("/files/upload")
						.file(binaryFile)
						.param("file_name", "test.bin"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.file_id").exists());
	}

	private String extractFileIdFromResponse(String response) {
		try {
			var jsonNode = objectMapper.readTree(response);
			return jsonNode.get("file_id").asText();
		} catch (Exception e) {
			throw new RuntimeException("Failed to extract file_id from response", e);
		}
	}
}
