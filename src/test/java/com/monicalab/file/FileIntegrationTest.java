package com.monicalab.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monicalab.file.repository.FileRepository;
import com.monicalab.support.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(addFilters = false)
class FileIntegrationTest extends AbstractIntegrationTest {

    private static final byte[] PNG_SIGNATURE = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.upload.root}")
    private String uploadRoot;

    @BeforeEach
    void cleanUp() {
        fileRepository.deleteAll();
    }

    @Test
    void uploadCreatesRecordAndPhysicalFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", validPngBytes(1024));

        String responseBody = mockMvc.perform(multipart("/api/admin/files")
                        .file(file)
                        .param("fileType", "IMAGE"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fileType").value("IMAGE"))
                .andExpect(jsonPath("$.data.url").value(org.hamcrest.Matchers.startsWith("/api/files/")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Long id = objectMapper.readTree(responseBody).path("data").path("id").asLong();

        var uploadFile = fileRepository.findById(id).orElseThrow();
        assertThat(uploadFile.getOriginalName()).isEqualTo("photo.png");
        Path savedPath = Path.of(uploadRoot, uploadFile.getPath());
        assertThat(Files.exists(savedPath)).isTrue();
    }

    @Test
    void uploadWithForgedImageExtensionReturnsInvalidFileType() throws Exception {
        byte[] fakeContent = "not-a-real-image".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "fake.png", "image/png", fakeContent);

        mockMvc.perform(multipart("/api/admin/files")
                        .file(file)
                        .param("fileType", "IMAGE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_FILE_TYPE"));
    }

    @Test
    void uploadExceedingImageSizeLimitReturnsFileSizeExceeded() throws Exception {
        byte[] oversized = validPngBytes(6 * 1024 * 1024);
        MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", oversized);

        mockMvc.perform(multipart("/api/admin/files")
                        .file(file)
                        .param("fileType", "IMAGE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FILE_SIZE_EXCEEDED"));
    }

    @Test
    void listReturnsLatestFirstAndMatchesRepositoryCount() throws Exception {
        Long firstId = uploadPng("first.png");
        Thread.sleep(1100);
        Long secondId = uploadPng("second.png");

        long repositoryCount = fileRepository.count();

        mockMvc.perform(get("/api/admin/files")
                        .param("page", "0")
                        .param("size", "20")
                        .param("sort", "createdAt,DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(repositoryCount))
                .andExpect(jsonPath("$.data.content[0].id").value(secondId))
                .andExpect(jsonPath("$.data.content[1].id").value(firstId));
    }

    @Test
    void publicDownloadReturnsFileContent() throws Exception {
        Long id = uploadPng("download.png");

        mockMvc.perform(get("/api/files/{id}", id))
                .andExpect(status().isOk());
    }

    @Test
    void deleteRemovesRecordAndPhysicalFileThenPublicDownloadReturns404() throws Exception {
        Long id = uploadPng("delete-me.png");
        var uploadFile = fileRepository.findById(id).orElseThrow();
        Path savedPath = Path.of(uploadRoot, uploadFile.getPath());
        assertThat(Files.exists(savedPath)).isTrue();

        mockMvc.perform(delete("/api/admin/files/{id}", id))
                .andExpect(status().isNoContent());

        assertThat(fileRepository.findById(id)).isEmpty();
        assertThat(Files.exists(savedPath)).isFalse();

        mockMvc.perform(get("/api/files/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FILE_NOT_FOUND"));
    }

    private Long uploadPng(String originalFilename) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", originalFilename, "image/png", validPngBytes(1024));
        String responseBody = mockMvc.perform(multipart("/api/admin/files")
                        .file(file)
                        .param("fileType", "IMAGE"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(responseBody).path("data").path("id").asLong();
    }

    private byte[] validPngBytes(int totalSize) {
        byte[] content = new byte[Math.max(totalSize, PNG_SIGNATURE.length)];
        Arrays.fill(content, (byte) 0x00);
        System.arraycopy(PNG_SIGNATURE, 0, content, 0, PNG_SIGNATURE.length);
        return content;
    }
}
