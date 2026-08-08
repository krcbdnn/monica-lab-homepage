package com.monicalab.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.monicalab.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class SecurityConfigTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedGetToAdminApiReturns401() throws Exception {
        mockMvc.perform(get("/api/admin/files"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void postWithoutCsrfTokenToAdminApiReturns403() throws Exception {
        mockMvc.perform(post("/api/admin/files"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminLoginScreenPathIsPermitAll() throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/login")).andReturn();

        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    @Test
    void unauthenticatedAccessToOtherAdminPathsReturns401() throws Exception {
        mockMvc.perform(get("/admin/not-yet-built"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/not-yet-built"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void authenticatedAdminCanAccessAdminApi() throws Exception {
        mockMvc.perform(get("/api/admin/files"))
                .andExpect(status().isOk());
    }
}
