package com.monicalab.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monicalab.board.entity.Board;
import com.monicalab.board.entity.BoardType;
import com.monicalab.board.repository.BoardRepository;
import com.monicalab.support.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
class BoardFileUploadIntegrationTest extends AbstractIntegrationTest {

    private static final byte[] PNG_SIGNATURE = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BoardRepository boardRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void uploadingNonImageAsThumbnailReturnsInvalidFileType() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "fake.png", "image/png",
                "not-a-real-image".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(adminMultipart(multipart("/api/admin/files")
                        .file(file)
                        .param("fileType", "IMAGE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_FILE_TYPE"));
    }

    @Test
    void uploadedImageUrlIsSavedAsBoardThumbnailOnCreate() throws Exception {
        String thumbnailUrl = uploadAndGetUrl("thumb.png", "image/png", PNG_SIGNATURE, "IMAGE");

        String body = "{\"boardType\":\"GALLERY\",\"title\":\"신규 갤러리\",\"thumbnail\":\"" + thumbnailUrl + "\"}";

        String responseBody = mockMvc.perform(adminJson(post("/api/admin/boards")).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.thumbnail").value(thumbnailUrl))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Long id = objectMapper.readTree(responseBody).path("data").path("id").asLong();
        Board persisted = boardRepository.findById(id).orElseThrow();
        assertThat(persisted.getThumbnail()).isEqualTo(thumbnailUrl);
    }

    @Test
    void uploadedAttachmentUrlIsSavedAsBoardAttachmentOnUpdate() throws Exception {
        Board board = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.ARCHIVE)
                .title("자료 포함 게시글")
                .viewCount(0)
                .isPublic(false)
                .build());

        String attachmentUrl = uploadAndGetUrl("guide.zip", "application/zip",
                "zip-file-content".getBytes(StandardCharsets.UTF_8), "ATTACHMENT");

        String body = "{\"boardType\":\"ARCHIVE\",\"title\":\"자료 포함 게시글\",\"attachment\":\"" + attachmentUrl + "\","
                + "\"isPublic\":false}";

        mockMvc.perform(adminJson(put("/api/admin/boards/{id}", board.getId())).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attachment").value(attachmentUrl));

        Board persisted = boardRepository.findById(board.getId()).orElseThrow();
        assertThat(persisted.getAttachment()).isEqualTo(attachmentUrl);
    }

    private String uploadAndGetUrl(String filename, String contentType, byte[] content, String fileType)
            throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, contentType, content);
        String responseBody = mockMvc.perform(adminMultipart(multipart("/api/admin/files")
                        .file(file)
                        .param("fileType", fileType)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(responseBody).path("data").path("url").asText();
    }

    private MockHttpServletRequestBuilder adminMultipart(MockHttpServletRequestBuilder builder) {
        return builder
                .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .with(csrf());
    }

    private MockHttpServletRequestBuilder adminJson(MockHttpServletRequestBuilder builder) {
        return builder
                .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON);
    }
}
