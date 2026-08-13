package com.monicalab.program.repository;

import com.monicalab.program.entity.Program;
import com.monicalab.program.entity.RecruitStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgramRepository extends JpaRepository<Program, Long>, ProgramRepositoryCustom {

    Optional<Program> findByIdAndIsPublicTrue(Long id);

    long countByRecruitStatus(RecruitStatus recruitStatus);
}
