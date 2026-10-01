package com.monicalab.file.controller;

import com.monicalab.file.entity.FileType;
import com.monicalab.file.service.FileDownload;
import com.monicalab.file.service.FileService;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        FileDownload download = fileService.download(id);
        String encodedName = URLEncoder.encode(download.originalName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        // IMAGE는 <img src>로 즉시 렌더링돼야 하므로 inline, ATTACHMENT는 명시적 "다운로드" 링크이므로
        // attachment로 Save-As를 유도한다. 두 용도가 이 엔드포인트 하나를 공유하므로 fileType으로만 분기한다.
        String dispositionType = download.fileType() == FileType.IMAGE ? "inline" : "attachment";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, dispositionType + "; filename*=UTF-8''" + encodedName)
                .body(download.resource());
    }
}
