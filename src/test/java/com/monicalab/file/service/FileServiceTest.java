package com.monicalab.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.monicalab.common.exception.CustomException;
import com.monicalab.common.exception.ErrorCode;
import com.monicalab.file.entity.FileType;
import com.monicalab.file.repository.FileRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * uploadRoot를 이미 존재하는 일반 파일 경로로 지정해, FileService.writeFile()의
 * Files.createDirectories(...) 호출이 실제로 IOException(FileAlreadyExistsException)을 던지도록
 * 유도한다. OS 권한 조작 없이 순수 java.nio.file 의미론만으로 결정적으로 재현되므로
 * Windows/Linux 어디서든 동일하게 동작한다.
 */
class FileServiceTest {

    private static final byte[] PNG_SIGNATURE = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private Path collidingUploadRoot;

    @BeforeEach
    void createCollidingUploadRootFile() throws IOException {
        collidingUploadRoot = Files.createTempFile("p10-t2-upload-root-", ".tmp");
    }

    @AfterEach
    void cleanUp() throws IOException {
        Files.deleteIfExists(collidingUploadRoot);
    }

    @Test
    void uploadWithUploadRootCollidingWithExistingRegularFileThrowsFileUploadFailed() {
        FileService fileService = new FileService(mock(FileRepository.class), collidingUploadRoot.toString());
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", PNG_SIGNATURE);

        assertThatThrownBy(() -> fileService.upload(file, FileType.IMAGE))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.FILE_UPLOAD_FAILED));
    }
}
