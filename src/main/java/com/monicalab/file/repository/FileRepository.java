package com.monicalab.file.repository;

import com.monicalab.file.entity.UploadFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileRepository extends JpaRepository<UploadFile, Long> {
}
