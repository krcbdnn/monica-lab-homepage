package com.monicalab.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monicalab.admin.repository.AdminRepository;
import com.monicalab.admin.service.AdminService;
import com.monicalab.support.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// P15-T6: PUT /api/admin/me/password 계약을 실제 Security filter(인증/CSRF)를 거쳐 검증한다. 테스트마다 별도
// 관리자 계정을 만들어 비밀번호 변경이 다른 테스트/클래스에 새지 않게 한다. 로그인도 실제 /api/admin/login으로 한다.
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AdminPasswordChangeIntegrationTest extends AbstractIntegrationTest {

    private static final String CURRENT_PASSWORD = "Current#Pass1";
    private static final String NEW_PASSWORD = "Changed!Pass2";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminService adminService;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private String loginId;

    @BeforeEach
    void seedAdmin() {
        loginId = "p15t6-" + UUID.randomUUID().toString().substring(0, 8);
        adminService.createInitialAdminIfAbsent(loginId, CURRENT_PASSWORD, "비밀번호변경테스트");
    }

    @Test
    void successChangesPasswordRotatesSessionIdAndKeepsAuthentication(CapturedOutput output) throws Exception {
        MockHttpSession session = login(CURRENT_PASSWORD);
        String sessionIdBefore = session.getId();

        String body = changePassword(session, CURRENT_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // 세션 고정 방어: 세션 ID는 바뀌지만 같은 세션(인증 컨텍스트)은 그대로 이어진다.
        assertThat(session.getId()).isNotEqualTo(sessionIdBefore);
        mockMvc.perform(get("/api/admin/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginId").value(loginId));

        String storedHash = storedHash();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, storedHash)).isTrue();
        assertThat(passwordEncoder.matches(CURRENT_PASSWORD, storedHash)).isFalse();

        assertNoSecretLeak(output, body, storedHash);
    }

    @Test
    void afterChangeNewPasswordLogsInAndOldPasswordIsRejected() throws Exception {
        changePassword(login(CURRENT_PASSWORD), CURRENT_PASSWORD, NEW_PASSWORD).andExpect(status().isOk());

        loginRequest(NEW_PASSWORD).andExpect(status().isOk());
        loginRequest(CURRENT_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void wrongCurrentPasswordReturnsInvalidCurrentPasswordAndKeepsPassword(CapturedOutput output) throws Exception {
        String hashBefore = storedHash();
        MockHttpSession session = login(CURRENT_PASSWORD);

        String body = changePassword(session, "Wrong#Pass9", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_CURRENT_PASSWORD"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(storedHash()).isEqualTo(hashBefore);
        // 현재 비밀번호 오류(400)는 세션을 끊지 않는다 - 401처럼 로그인 화면으로 보내지 않는 계약.
        mockMvc.perform(get("/api/admin/me").session(session)).andExpect(status().isOk());
        assertThat(body).doesNotContain("Wrong#Pass9", NEW_PASSWORD);
        assertThat(output.getAll()).doesNotContain("Wrong#Pass9", NEW_PASSWORD, hashBefore);
    }

    @Test
    void newPasswordSameAsCurrentReturnsInvalidInputValue() throws Exception {
        String hashBefore = storedHash();

        changePassword(login(CURRENT_PASSWORD), CURRENT_PASSWORD, CURRENT_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));

        assertThat(storedHash()).isEqualTo(hashBefore);
    }

    // 정책 위반은 모두 Validation 400이며, 비ASCII(한글 32자 = UTF-8 96byte 등)도 BCrypt encode(72byte 초과 예외)까지
    // 도달하지 않아 500이 되지 않는다.
    @ParameterizedTest
    @ValueSource(strings = {
            "abc1234",                                            // 7자
            "a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1b", // 65자
            "abcdefghij",                                         // 영문만
            "1234567890",                                         // 숫자만
            "!@#$%^&*()",                                         // 특수문자만
            "abcd 1234",                                          // 공백
            "가나다라마바사아자차카타파하1!가나다라마바사아자차카타파하1!",    // 한글(64자 이하, 72byte 초과)
            "abcd1234é",                                          // 기타 비ASCII
            "abcd1234😀"                                          // emoji
    })
    void policyViolationReturnsValidationErrorWithoutReachingEncoder(String newPassword, CapturedOutput output)
            throws Exception {
        String hashBefore = storedHash();

        String body = changePassword(login(CURRENT_PASSWORD), CURRENT_PASSWORD, newPassword)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.error.fields[0].field").value("newPassword"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(storedHash()).isEqualTo(hashBefore);
        assertThat(body).doesNotContain(newPassword, CURRENT_PASSWORD);
        assertThat(output.getAll()).doesNotContain(newPassword, CURRENT_PASSWORD, "IllegalArgumentException");
    }

    @Test
    void blankCurrentPasswordReturnsValidationError() throws Exception {
        changePassword(login(CURRENT_PASSWORD), " ", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.error.fields[0].field").value("currentPassword"));
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(put("/api/admin/me/password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(CURRENT_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void missingCsrfTokenReturns403AndKeepsPassword() throws Exception {
        String hashBefore = storedHash();

        mockMvc.perform(put("/api/admin/me/password")
                        .session(login(CURRENT_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(CURRENT_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));

        assertThat(storedHash()).isEqualTo(hashBefore);
    }

    private MockHttpSession login(String password) throws Exception {
        return (MockHttpSession) loginRequest(password)
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    private ResultActions loginRequest(String password) throws Exception {
        return mockMvc.perform(post("/api/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("loginId", loginId, "password", password))));
    }

    private ResultActions changePassword(MockHttpSession session, String currentPassword, String newPassword)
            throws Exception {
        return mockMvc.perform(put("/api/admin/me/password")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(currentPassword, newPassword)));
    }

    private String json(String currentPassword, String newPassword) throws Exception {
        return objectMapper.writeValueAsString(Map.of("currentPassword", currentPassword, "newPassword", newPassword));
    }

    private String storedHash() {
        return adminRepository.findByLoginId(loginId).orElseThrow().getPassword();
    }

    private static void assertNoSecretLeak(CapturedOutput output, String responseBody, String storedHash) {
        assertThat(responseBody).doesNotContain(CURRENT_PASSWORD, NEW_PASSWORD, storedHash);
        assertThat(output.getAll()).doesNotContain(CURRENT_PASSWORD, NEW_PASSWORD, storedHash);
    }
}
