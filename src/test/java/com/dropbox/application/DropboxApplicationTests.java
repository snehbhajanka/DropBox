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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class DropboxApplicationTests {

	private MockMvc mockMvc;

	@Autowired
	private WebApplicationContext webApplicationContext;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
		// Clear the static storage before each test
		DropboxApplication.clearStorage();
	}

	@Test
	void contextLoads() {
		// Basic context load test
	}

	@Test
	void testListFiles_EmptyStorage() throws Exception {
		mockMvc.perform(get("/files"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("No files to display"));
	}

	@Test
	void testUploadFile_Success() throws Exception {
		MockMultipartFile file = new MockMultipartFile(
				"file",
				"test.txt",
				"text/plain",
				"Hello World".getBytes()
		);

		mockMvc.perform(multipart("/files/upload")
						.file(file)
						.param("file_name", "test.txt"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.file_id").exists());
	}

	@Test
	void testListFiles_WithFiles() throws Exception {
		// First upload a file
		MockMultipartFile file = new MockMultipartFile(
				"file",
				"test.txt", 
				"text/plain",
				"Hello World".getBytes()
		);

		mockMvc.perform(multipart("/files/upload")
						.file(file)
						.param("file_name", "test.txt"))
				.andExpect(status().isOk());

		// Then list files
		mockMvc.perform(get("/files"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.files").exists())
				.andExpect(jsonPath("$.files").isArray());
	}

	@Test
	void testDownloadFile_Success() throws Exception {
		// First upload a file
		MockMultipartFile file = new MockMultipartFile(
				"file",
				"test.txt",
				"text/plain", 
				"Hello World".getBytes()
		);

		String response = mockMvc.perform(multipart("/files/upload")
						.file(file)
						.param("file_name", "test.txt"))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();

		String fileId = objectMapper.readTree(response).get("file_id").asText();

		// Then download the file
		mockMvc.perform(get("/files/" + fileId))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", "attachment; filename=test.txt"))
				.andExpect(content().bytes("Hello World".getBytes()));
	}

	@Test
	void testDownloadFile_NotFound() throws Exception {
		mockMvc.perform(get("/files/nonexistent-id"))
				.andExpect(status().isNotFound());
	}

	@Test
	void testDeleteFile_Success() throws Exception {
		// First upload a file
		MockMultipartFile file = new MockMultipartFile(
				"file",
				"test.txt",
				"text/plain",
				"Hello World".getBytes()
		);

		String response = mockMvc.perform(multipart("/files/upload")
						.file(file)
						.param("file_name", "test.txt"))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();

		String fileId = objectMapper.readTree(response).get("file_id").asText();

		// Then delete the file
		mockMvc.perform(delete("/files/" + fileId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("File deleted successfully"));

		// Verify file is deleted by trying to download
		mockMvc.perform(get("/files/" + fileId))
				.andExpect(status().isNotFound());
	}

	@Test
	void testDeleteFile_NotFound() throws Exception {
		mockMvc.perform(delete("/files/nonexistent-id"))
				.andExpect(status().isNotFound());
	}

	@Test
	void testUpdateFile_Success() throws Exception {
		// First upload a file
		MockMultipartFile file = new MockMultipartFile(
				"file",
				"test.txt",
				"text/plain",
				"Hello World".getBytes()
		);

		String response = mockMvc.perform(multipart("/files/upload")
						.file(file)
						.param("file_name", "test.txt"))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();

		String fileId = objectMapper.readTree(response).get("file_id").asText();

		// Then update the file with new content
		MockMultipartFile updatedFile = new MockMultipartFile(
				"file",
				"updated.txt",
				"text/plain",
				"Updated content".getBytes()
		);

		mockMvc.perform(multipart("/files/" + fileId)
						.file(updatedFile)
						.with(request -> {
							request.setMethod("PUT");
							return request;
						}))
				.andExpect(status().isOk());

		// Verify the file was updated by downloading
		mockMvc.perform(get("/files/" + fileId))
				.andExpect(status().isOk())
				.andExpect(content().bytes("Updated content".getBytes()));
	}

	@Test
	void testUpdateFile_NotFound() throws Exception {
		MockMultipartFile file = new MockMultipartFile(
				"file",
				"test.txt",
				"text/plain",
				"Hello World".getBytes()
		);

		mockMvc.perform(multipart("/files/nonexistent-id")
						.file(file)
						.with(request -> {
							request.setMethod("PUT");
							return request;
						}))
				.andExpect(status().isNotFound());
	}

	@Test
	void testUploadFile_EmptyFile() throws Exception {
		MockMultipartFile emptyFile = new MockMultipartFile(
				"file",
				"empty.txt",
				"text/plain",
				new byte[0]
		);

		mockMvc.perform(multipart("/files/upload")
						.file(emptyFile)
						.param("file_name", "empty.txt"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.file_id").exists());
	}

	@Test
	void testUploadFile_LargeFile() throws Exception {
		// Create a larger file (1KB)
		byte[] largeContent = new byte[1024];
		for (int i = 0; i < largeContent.length; i++) {
			largeContent[i] = (byte) (i % 256);
		}

		MockMultipartFile largeFile = new MockMultipartFile(
				"file",
				"large.bin",
				"application/octet-stream",
				largeContent
		);

		mockMvc.perform(multipart("/files/upload")
						.file(largeFile)
						.param("file_name", "large.bin"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.file_id").exists());
	}

}
