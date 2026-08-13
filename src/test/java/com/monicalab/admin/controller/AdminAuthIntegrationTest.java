package com.monicalab.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.monicalab.admin.service.AdminService;
import com.monicalab.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class AdminAuthIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_LOGIN_ID = "p3t3-login-admin";
    private static final String TEST_PASSWORD = "Passw0rd1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminService adminService;

    @BeforeEach
    void seedTestAdmin() {
        adminService.createInitialAdminIfAbsent(TEST_LOGIN_ID, TEST_PASSWORD, "테스트관리자");
    }

    @Test
    void loginSuccessReturnsSessionAndAdminInfo() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(TEST_LOGIN_ID, TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.loginId").value(TEST_LOGIN_ID))
                .andExpect(jsonPath("$.data.role").value("ROLE_ADMIN"))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNotNull();
    }

    @Test
    void loginWithWrongPasswordReturnsAuthenticationFailed() throws Exception {
        mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(TEST_LOGIN_ID, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void loginWithNonExistentLoginIdReturnsAuthenticationFailed() throws Exception {
        mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("no-such-login-id", TEST_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void meWithAuthenticatedSessionReturnsAdminInfo() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(TEST_LOGIN_ID, TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(get("/api/admin/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginId").value(TEST_LOGIN_ID))
                .andExpect(jsonPath("$.data.name").value("테스트관리자"))
                .andExpect(jsonPath("$.data.role").value("ROLE_ADMIN"));
    }

    @Test
    void meWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(get("/api/admin/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutInvalidatesSessionAndSubsequentAdminApiReturns401() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(TEST_LOGIN_ID, TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(post("/api/admin/logout")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/admin/files").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminLoginPageIsAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/admin/login"))
                .andExpect(status().isOk());
    }

    @Test
    void sessionIdChangesOnLoginWhenSessionAlreadyExists() throws Exception {
        MockHttpSession existingSession = new MockHttpSession();
        String originalSessionId = existingSession.getId();

        mockMvc.perform(post("/api/admin/login")
                        .session(existingSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(TEST_LOGIN_ID, TEST_PASSWORD)))
                .andExpect(status().isOk());

        assertThat(existingSession.getId()).isNotEqualTo(originalSessionId);
    }

    private String loginJson(String loginId, String password) {
        return "{\"loginId\":\"" + loginId + "\",\"password\":\"" + password + "\"}";
    }
}
