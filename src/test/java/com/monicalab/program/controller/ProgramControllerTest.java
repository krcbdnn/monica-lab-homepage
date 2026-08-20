package com.monicalab.program.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.monicalab.program.entity.Program;
import com.monicalab.program.entity.ProgramType;
import com.monicalab.program.entity.RecruitStatus;
import com.monicalab.program.repository.ProgramRepository;
import com.monicalab.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class ProgramControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProgramRepository programRepository;

    private Long publicProgramId;
    private Long privateProgramId;

    @BeforeEach
    void setUp() {
        programRepository.deleteAll();

        publicProgramId = programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.COURSE)
                .title("여름 정규반 모집")
                .content("정규 강좌 안내")
                .recruitStatus(RecruitStatus.OPEN)
                .isPublic(true)
                .build()).getId();

        programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.SPECIAL)
                .title("특별 워크숍")
                .content("여름 특강 콘텐츠")
                .recruitStatus(RecruitStatus.OPEN)
                .isPublic(true)
                .build());

        privateProgramId = programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.COURSE)
                .title("비공개 강좌")
                .content("비공개 안내")
                .recruitStatus(RecruitStatus.OPEN)
                .isPublic(false)
                .build()).getId();
    }

    @Test
    void publicListReturnsOnlyPublicProgramsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/programs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[*].isPublic").isNotEmpty());
    }

    @Test
    void publicDetailReturnsOkForPublicProgram() throws Exception {
        mockMvc.perform(get("/api/programs/{id}", publicProgramId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("여름 정규반 모집"));
    }

    @Test
    void publicDetailReturnsNotFoundForPrivateProgram() throws Exception {
        mockMvc.perform(get("/api/programs/{id}", privateProgramId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PROGRAM_NOT_FOUND"));
    }

    @Test
    void publicDetailReturnsNotFoundForNonExistentProgram() throws Exception {
        mockMvc.perform(get("/api/programs/{id}", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROGRAM_NOT_FOUND"));
    }

    @Test
    void programTypeFilterReturnsOnlyMatchingType() throws Exception {
        mockMvc.perform(get("/api/programs").param("programType", "SPECIAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("특별 워크숍"));
    }

    @Test
    void keywordFilterReturnsOnlyTitleOrContentMatches() throws Exception {
        mockMvc.perform(get("/api/programs").param("keyword", "여름"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void keywordFilterDoesNotLeakPrivateProgramsEvenWhenMatching() throws Exception {
        mockMvc.perform(get("/api/programs").param("keyword", "비공개"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }
}
