package com.monicalab.file.dto;

import com.monicalab.file.entity.FileType;
import com.monicalab.file.entity.UploadFile;
import java.time.LocalDateTime;

public record FileResponse(
        Long id,
        String originalName,
        String url,
        String contentType,
        long size,
        FileType fileType,
        LocalDateTime createdAt) {

    public static FileResponse from(UploadFile uploadFile) {
        return new FileResponse(
                uploadFile.getId(),
                uploadFile.getOriginalName(),
                "/api/files/" + uploadFile.getId(),
                uploadFile.getContentType(),
                uploadFile.getSize(),
                uploadFile.getFileType(),
                uploadFile.getCreatedAt());
    }
}
