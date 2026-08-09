package com.monicalab.program;

import static org.assertj.core.api.Assertions.assertThat;

import com.monicalab.program.dto.ProgramRequest;
import com.monicalab.program.dto.ProgramResponse;
import com.monicalab.program.entity.Program;
import com.monicalab.program.entity.ProgramType;
import com.monicalab.program.entity.RecruitStatus;
import com.monicalab.program.repository.ProgramRepository;
import com.monicalab.program.service.ProgramService;
import com.monicalab.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ProgramIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private ProgramService programService;

    @Test
    void enumFieldsRoundTripThroughRepository() {
        Program saved = programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.SPECIAL)
                .title("겨울 특강")
                .recruitStatus(RecruitStatus.CLOSED)
                .isPublic(true)
                .build());

        Program found = programRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getProgramType()).isEqualTo(ProgramType.SPECIAL);
        assertThat(found.getRecruitStatus()).isEqualTo(RecruitStatus.CLOSED);
        assertThat(found.isPublic()).isTrue();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void createAppliesPostDefaultsWhenRecruitStatusAndIsPublicAreOmitted() {
        ProgramRequest request = new ProgramRequest(
                ProgramType.COURSE, "정규 강좌", null, null, null, null, null, null);

        ProgramResponse response = programService.create(request);

        assertThat(response.recruitStatus()).isEqualTo(RecruitStatus.OPEN);
        assertThat(response.isPublic()).isFalse();

        Program persisted = programRepository.findById(response.id()).orElseThrow();
        assertThat(persisted.getRecruitStatus()).isEqualTo(RecruitStatus.OPEN);
        assertThat(persisted.isPublic()).isFalse();
    }

    @Test
    void createHonorsExplicitRecruitStatusAndIsPublicWhenProvided() {
        ProgramRequest request = new ProgramRequest(
                ProgramType.SPECIAL, "비공개 마감 특강", null, null, null, null, RecruitStatus.CLOSED, true);

        ProgramResponse response = programService.create(request);

        assertThat(response.recruitStatus()).isEqualTo(RecruitStatus.CLOSED);
        assertThat(response.isPublic()).isTrue();
    }
}
