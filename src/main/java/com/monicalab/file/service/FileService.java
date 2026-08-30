package com.monicalab.file.service;

import com.monicalab.common.dto.PageResponse;
import com.monicalab.common.exception.CustomException;
import com.monicalab.common.exception.ErrorCode;
import com.monicalab.file.dto.FileResponse;
import com.monicalab.file.entity.FileType;
import com.monicalab.file.entity.UploadFile;
import com.monicalab.file.repository.FileRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileService {

    private static final DateTimeFormatter DIRECTORY_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif");
    private static final Set<String> ALLOWED_ATTACHMENT_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "pdf", "hwp", "hwpx", "docx", "xlsx", "pptx", "zip");

    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final long MAX_UPLOAD_SIZE = 10L * 1024 * 1024;

    private static final Set<String> ALLOWED_SORT_PROPERTIES =
            Set.of("createdAt", "originalName", "size", "fileType");

    private final FileRepository fileRepository;
    private final String uploadRoot;

    public FileService(FileRepository fileRepository, @Value("${app.upload.root}") String uploadRoot) {
        this.fileRepository = fileRepository;
        this.uploadRoot = uploadRoot;
    }

    @Transactional(readOnly = true)
    public PageResponse<FileResponse> list(Pageable pageable) {
        validateSort(pageable.getSort());
        Page<UploadFile> page = fileRepository.findAll(pageable);
        return PageResponse.of(page, FileResponse::from);
    }

    @Transactional
    public FileResponse upload(MultipartFile file, FileType fileType) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        String extension = extractExtension(file.getOriginalFilename());
        validateExtension(extension, fileType);

        byte[] content = readBytes(file);
        if (fileType == FileType.IMAGE) {
            validateImageSignature(content, extension);
        }
        validateSize(content.length, fileType);

        String storedName = UUID.randomUUID() + "." + extension;
        String relativePath = LocalDate.now().format(DIRECTORY_FORMATTER) + "/" + storedName;
        Path targetPath = Path.of(uploadRoot, relativePath);
        writeFile(targetPath, content);

        UploadFile uploadFile = UploadFile.builder()
                .originalName(file.getOriginalFilename())
                .storedName(storedName)
                .path(relativePath)
                .contentType(file.getContentType())
                .size(content.length)
                .fileType(fileType)
                .build();

        return FileResponse.from(fileRepository.save(uploadFile));
    }

    @Transactional(readOnly = true)
    public FileResponse get(Long id) {
        return FileResponse.from(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public FileDownload download(Long id) {
        UploadFile uploadFile = getOrThrow(id);
        Path path = Path.of(uploadRoot, uploadFile.getPath());
        if (!Files.exists(path)) {
            throw new CustomException(ErrorCode.FILE_NOT_FOUND);
        }
        Resource resource = new FileSystemResource(path);
        return new FileDownload(resource, uploadFile.getOriginalName(), uploadFile.getContentType(),
                uploadFile.getFileType());
    }

    @Transactional
    public void delete(Long id) {
        UploadFile uploadFile = getOrThrow(id);
        Path path = Path.of(uploadRoot, uploadFile.getPath());
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED);
        }
        fileRepository.delete(uploadFile);
    }

    private UploadFile getOrThrow(Long id) {
        return fileRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.FILE_NOT_FOUND));
    }

    private void validateSort(Sort sort) {
        boolean invalid = sort.stream().anyMatch(order -> !ALLOWED_SORT_PROPERTIES.contains(order.getProperty()));
        if (invalid) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new CustomException(ErrorCode.INVALID_FILE_TYPE);
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf('.') + 1);
        return extension.toLowerCase(Locale.ROOT);
    }

    private void validateExtension(String extension, FileType fileType) {
        Set<String> allowed = fileType == FileType.IMAGE ? ALLOWED_IMAGE_EXTENSIONS : ALLOWED_ATTACHMENT_EXTENSIONS;
        if (!allowed.contains(extension)) {
            throw new CustomException(ErrorCode.INVALID_FILE_TYPE);
        }
    }

    private void validateSize(long size, FileType fileType) {
        long max = fileType == FileType.IMAGE ? MAX_IMAGE_SIZE : MAX_UPLOAD_SIZE;
        if (size > max) {
            throw new CustomException(ErrorCode.FILE_SIZE_EXCEEDED);
        }
    }

    private void validateImageSignature(byte[] content, String extension) {
        boolean valid = switch (extension) {
            case "png" -> matchesSignature(content, new int[] {0x89, 0x50, 0x4E, 0x47});
            case "jpg", "jpeg" -> matchesSignature(content, new int[] {0xFF, 0xD8, 0xFF});
            case "gif" -> matchesSignature(content, new int[] {0x47, 0x49, 0x46, 0x38});
            default -> false;
        };
        if (!valid) {
            throw new CustomException(ErrorCode.INVALID_FILE_TYPE);
        }
    }

    private boolean matchesSignature(byte[] content, int[] signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((content[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    private void writeFile(Path targetPath, byte[] content) {
        try {
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, content);
        } catch (IOException e) {
            throw new CustomException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }
}
