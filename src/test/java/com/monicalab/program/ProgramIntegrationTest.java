package com.monicalab.program;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.monicalab.common.dto.PageResponse;
import com.monicalab.common.exception.CustomException;
import com.monicalab.common.exception.ErrorCode;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

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

    @Test
    void createSanitizesScriptTagsInContent() {
        ProgramRequest request = new ProgramRequest(ProgramType.COURSE, "정규 강좌",
                "<p>hello</p><script>alert(1)</script>", null, null, null, null, null);

        ProgramResponse response = programService.create(request);

        assertThat(response.content()).contains("hello");
        assertThat(response.content()).doesNotContain("<script");
        assertThat(response.content()).doesNotContain("alert(1)");
    }

    @Test
    void publicListOnlyReturnsPublicPrograms() {
        programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.COURSE).title("공개A")
                .recruitStatus(RecruitStatus.OPEN).isPublic(true).build());
        programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.COURSE).title("비공개A")
                .recruitStatus(RecruitStatus.OPEN).isPublic(false).build());

        PageResponse<ProgramResponse> page = programService.getPublicList(
                null, null, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertThat(page.getContent()).allMatch(ProgramResponse::isPublic);
        assertThat(page.getContent()).extracting(ProgramResponse::title).contains("공개A");
        assertThat(page.getContent()).extracting(ProgramResponse::title).doesNotContain("비공개A");
    }

    @Test
    void publicByIdReturnsOnlyPublicProgram() {
        Program publicProgram = programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.COURSE).title("공개B")
                .recruitStatus(RecruitStatus.OPEN).isPublic(true).build());

        ProgramResponse response = programService.getPublicById(publicProgram.getId());

        assertThat(response.title()).isEqualTo("공개B");
    }

    @Test
    void publicByIdThrowsNotFoundForPrivateProgram() {
        Program privateProgram = programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.COURSE).title("비공개B")
                .recruitStatus(RecruitStatus.OPEN).isPublic(false).build());

        assertThatThrownBy(() -> programService.getPublicById(privateProgram.getId()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROGRAM_NOT_FOUND);
    }
}
