package com.monicalab.program.repository;

import com.monicalab.program.dto.ProgramSearchCondition;
import com.monicalab.program.entity.Program;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProgramRepositoryCustom {

    Page<Program> search(ProgramSearchCondition condition, Pageable pageable);
}
