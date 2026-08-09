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
