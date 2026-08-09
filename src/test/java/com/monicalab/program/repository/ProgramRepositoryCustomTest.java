package com.monicalab.program.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.monicalab.program.dto.ProgramSearchCondition;
import com.monicalab.program.entity.Program;
import com.monicalab.program.entity.ProgramType;
import com.monicalab.program.entity.RecruitStatus;
import com.monicalab.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

class ProgramRepositoryCustomTest extends AbstractIntegrationTest {

    @Autowired
    private ProgramRepository programRepository;

    @BeforeEach
    void setUp() {
        programRepository.deleteAll();

        programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.COURSE)
                .title("여름 정규반 모집")
                .content("정규 강좌 안내")
                .recruitStatus(RecruitStatus.OPEN)
                .isPublic(true)
                .build());

        programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.SPECIAL)
                .title("특별 워크숍")
                .content("여름 특강 콘텐츠")
                .recruitStatus(RecruitStatus.OPEN)
                .isPublic(true)
                .build());

        programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.COURSE)
                .title("겨울 캠프")
                .content("캠프 소개")
                .recruitStatus(RecruitStatus.OPEN)
                .isPublic(true)
                .build());
    }

    @Test
    void keywordOnlyMatchesTitleOrContentAcrossProgramTypes() {
        Page<Program> result = programRepository.search(
                new ProgramSearchCondition(null, "여름"), PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(Program::getTitle)
                .containsExactlyInAnyOrder("여름 정규반 모집", "특별 워크숍");
    }

    @Test
    void programTypeOnlyMatchesRegardlessOfKeyword() {
        Page<Program> result = programRepository.search(
                new ProgramSearchCondition(ProgramType.COURSE, null), PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(Program::getTitle)
                .containsExactlyInAnyOrder("여름 정규반 모집", "겨울 캠프");
    }

    @Test
    void keywordAndProgramTypeCombinedNarrowsToMatchingItemsOnly() {
        Page<Program> result = programRepository.search(
                new ProgramSearchCondition(ProgramType.COURSE, "여름"), PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(Program::getTitle)
                .containsExactly("여름 정규반 모집");
    }
}
