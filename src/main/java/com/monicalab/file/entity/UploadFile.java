package com.monicalab.file.entity;

import com.monicalab.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "file")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UploadFile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "stored_name", nullable = false)
    private String storedName;

    @Column(name = "path", nullable = false, length = 500)
    private String path;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size", nullable = false)
    private long size;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 20)
    private FileType fileType;

    @Builder
    private UploadFile(String originalName, String storedName, String path, String contentType, long size,
            FileType fileType) {
        this.originalName = originalName;
        this.storedName = storedName;
        this.path = path;
        this.contentType = contentType;
        this.size = size;
        this.fileType = fileType;
    }
}
