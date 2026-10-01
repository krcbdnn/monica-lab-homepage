package com.monicalab.file.service;

import com.monicalab.file.entity.FileType;
import org.springframework.core.io.Resource;

public record FileDownload(Resource resource, String originalName, String contentType, FileType fileType) {
}
