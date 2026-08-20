package com.monicalab.program.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
class AdminProgramControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProgramRepository programRepository;

    @BeforeEach
    void setUp() {
        programRepository.deleteAll();
    }

    @Test
    void unauthenticatedAccessToAdminListReturns401() throws Exception {
        mockMvc.perform(get("/api/admin/programs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createReturns201WithPostDefaults() throws Exception {
        String body = "{\"programType\":\"COURSE\",\"title\":\"신규 강좌\"}";

        mockMvc.perform(admin(post("/api/admin/programs")).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recruitStatus").value("OPEN"))
                .andExpect(jsonPath("$.data.isPublic").value(false));
    }

    @Test
    void createWithInvalidGoogleFormUrlReturns400() throws Exception {
        String body = "{\"programType\":\"COURSE\",\"title\":\"신규 강좌\",\"googleFormUrl\":\"not-a-valid-url\"}";

        mockMvc.perform(admin(post("/api/admin/programs")).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void createSanitizesScriptTagInContent() throws Exception {
        String body = "{\"programType\":\"COURSE\",\"title\":\"신규 강좌\","
                + "\"content\":\"<p>hello</p><script>alert(1)</script>\"}";

        mockMvc.perform(admin(post("/api/admin/programs")).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content", org.hamcrest.Matchers.containsString("hello")))
                .andExpect(jsonPath("$.data.content", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("<script"))));
    }

    @Test
    void adminListAndDetailReturnPrivatePrograms() throws Exception {
        Program privateProgram = programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.SPECIAL)
                .title("비공개 특강")
                .recruitStatus(RecruitStatus.OPEN)
                .isPublic(false)
                .build());

        mockMvc.perform(admin(get("/api/admin/programs")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());

        mockMvc.perform(admin(get("/api/admin/programs/{id}", privateProgram.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPublic").value(false))
                .andExpect(jsonPath("$.data.title").value("비공개 특강"));
    }

    @Test
    void putUpdatesProgramAndRequiresRecruitStatusAndIsPublic() throws Exception {
        Program program = programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.COURSE)
                .title("원래 제목")
                .recruitStatus(RecruitStatus.OPEN)
                .isPublic(false)
                .build());

        String validBody = "{\"programType\":\"COURSE\",\"title\":\"수정된 제목\","
                + "\"recruitStatus\":\"CLOSED\",\"isPublic\":true}";

        mockMvc.perform(admin(put("/api/admin/programs/{id}", program.getId())).content(validBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정된 제목"))
                .andExpect(jsonPath("$.data.recruitStatus").value("CLOSED"))
                .andExpect(jsonPath("$.data.isPublic").value(true));

        String missingStatusBody = "{\"programType\":\"COURSE\",\"title\":\"수정된 제목\"}";

        mockMvc.perform(admin(put("/api/admin/programs/{id}", program.getId())).content(missingStatusBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void patchVisibilityAndStatusUpdateSingleField() throws Exception {
        Program program = programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.COURSE)
                .title("상태 변경 대상")
                .recruitStatus(RecruitStatus.OPEN)
                .isPublic(false)
                .build());

        mockMvc.perform(admin(patch("/api/admin/programs/{id}/visibility", program.getId()))
                        .content("{\"isPublic\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPublic").value(true));

        mockMvc.perform(admin(patch("/api/admin/programs/{id}/status", program.getId()))
                        .content("{\"recruitStatus\":\"CLOSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recruitStatus").value("CLOSED"));
    }

    @Test
    void adminListProgramTypeFilterReturnsOnlyMatchingTypeIncludingPrivate() throws Exception {
        programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.COURSE).title("정규반 공개")
                .recruitStatus(RecruitStatus.OPEN).isPublic(true).build());
        programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.COURSE).title("정규반 비공개")
                .recruitStatus(RecruitStatus.OPEN).isPublic(false).build());
        programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.SPECIAL).title("특강 공개")
                .recruitStatus(RecruitStatus.OPEN).isPublic(true).build());

        mockMvc.perform(admin(get("/api/admin/programs")).param("programType", "COURSE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[*].programType",
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("COURSE"))));
    }

    @Test
    void adminListKeywordFilterMatchesTitleOrContentIncludingPrivate() throws Exception {
        programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.COURSE).title("여름 정규반")
                .recruitStatus(RecruitStatus.OPEN).isPublic(true).build());
        programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.SPECIAL).title("비공개 특강").content("여름 특강 안내")
                .recruitStatus(RecruitStatus.OPEN).isPublic(false).build());
        programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.COURSE).title("겨울 캠프")
                .recruitStatus(RecruitStatus.OPEN).isPublic(true).build());

        mockMvc.perform(admin(get("/api/admin/programs")).param("keyword", "여름"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[*].title", org.hamcrest.Matchers.hasItem("비공개 특강")));
    }

    @Test
    void adminListProgramTypeAndKeywordCombinedNarrowsToMatchingItemsOnlyIncludingPrivate() throws Exception {
        Program matching = programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.COURSE).title("여름 정규반 비공개")
                .recruitStatus(RecruitStatus.OPEN).isPublic(false).build());
        programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.SPECIAL).title("여름 특강 공개")
                .recruitStatus(RecruitStatus.OPEN).isPublic(true).build());
        programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.COURSE).title("겨울 캠프")
                .recruitStatus(RecruitStatus.OPEN).isPublic(true).build());

        mockMvc.perform(admin(get("/api/admin/programs"))
                        .param("programType", "COURSE")
                        .param("keyword", "여름"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(matching.getId()))
                .andExpect(jsonPath("$.data.content[0].isPublic").value(false));
    }

    @Test
    void deleteRemovesProgramAndSubsequentGetReturns404() throws Exception {
        Program program = programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.COURSE)
                .title("삭제 대상")
                .recruitStatus(RecruitStatus.OPEN)
                .isPublic(false)
                .build());

        mockMvc.perform(admin(delete("/api/admin/programs/{id}", program.getId())))
                .andExpect(status().isNoContent());

        mockMvc.perform(admin(get("/api/admin/programs/{id}", program.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROGRAM_NOT_FOUND"));
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder
                .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON);
    }
}
