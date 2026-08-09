package com.monicalab.program.repository;

import com.monicalab.program.entity.Program;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgramRepository extends JpaRepository<Program, Long> {

    Page<Program> findByIsPublicTrue(Pageable pageable);

    Optional<Program> findByIdAndIsPublicTrue(Long id);
}
