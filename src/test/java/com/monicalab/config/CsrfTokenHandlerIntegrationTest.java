package com.monicalab.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monicalab.admin.service.AdminService;
import com.monicalab.board.entity.Board;
import com.monicalab.board.repository.BoardRepository;
import com.monicalab.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// SpaCsrfTokenRequestHandler 회귀 검증. .with(csrf())(테스트 전용 우회)를 쓰지 않고
// 로그인 -> 관리자 GET(쿠키 발급) -> 그 raw 쿠키 값을 그대로 헤더로 보낸 POST 라는
// 실제 브라우저와 동일한 쿠키-헤더 왕복 흐름을 검증한다.
//
// @DirtiesContext(BEFORE_CLASS): 다른 기존 테스트들이 쓰는 SecurityMockMvcRequestPostProcessors.csrf()는
// 내부적으로 WebTestUtils.setCsrfTokenRepository(...)를 통해 캐시/공유되는 실제 CsrfFilter 빈의
// tokenRepository 필드를 리플렉션으로 세션 기반 저장소(TestCsrfTokenRepository)로 영구 교체한다.
// 이 클래스는 실제 CookieCsrfTokenRepository(운영과 동일한 쿠키 기반 저장소)가 살아있는 컨텍스트가
// 반드시 필요하므로, 실행 순서상 앞서 .with(csrf())를 사용한 다른 테스트 클래스로 오염된 캐시 컨텍스트를
// 물려받지 않도록 클래스 실행 전 컨텍스트를 새로 만들게 한다.
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@AutoConfigureMockMvc
class CsrfTokenHandlerIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_LOGIN_ID = "csrf-handler-test-admin";
    private static final String TEST_PASSWORD = "Passw0rd1";
    private static final String XSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final String XSRF_HEADER_NAME = "X-XSRF-TOKEN";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminService adminService;

    @Autowired
    private BoardRepository boardRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void seedTestAdmin() {
        adminService.createInitialAdminIfAbsent(TEST_LOGIN_ID, TEST_PASSWORD, "CSRF테스트관리자");
    }

    @Test
    void authenticatedAdminGetIssuesXsrfTokenCookie() throws Exception {
        MockHttpSession session = login();

        MvcResult result = mockMvc.perform(get("/admin/boards/new").session(session))
                .andExpect(status().isOk())
                .andReturn();

        Cookie xsrfCookie = result.getResponse().getCookie(XSRF_COOKIE_NAME);
        assertThat(xsrfCookie).isNotNull();
        assertThat(xsrfCookie.getValue()).isNotBlank();
    }

    @Test
    void firstPostWithCookieValueAsHeaderSucceedsAndPersists() throws Exception {
        MockHttpSession session = login();
        Cookie xsrfCookie = fetchXsrfCookie(session);

        String requestBody = """
                {"boardType":"NOTICE","title":"CSRF 회귀 테스트 공지"}""";

        MvcResult result = mockMvc.perform(post("/api/admin/boards")
                        .session(session)
                        .cookie(xsrfCookie)
                        .header(XSRF_HEADER_NAME, xsrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

        long boardId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        Board saved = boardRepository.findById(boardId).orElseThrow();
        assertThat(saved.getTitle()).isEqualTo("CSRF 회귀 테스트 공지");
    }

    @Test
    void authenticatedPostWithoutCsrfTokenReturns403() throws Exception {
        MockHttpSession session = login();

        String requestBody = """
                {"boardType":"NOTICE","title":"CSRF 누락 테스트"}""";

        mockMvc.perform(post("/api/admin/boards")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedPostWithMismatchedCsrfTokenReturns403() throws Exception {
        MockHttpSession session = login();
        Cookie xsrfCookie = fetchXsrfCookie(session);

        String requestBody = """
                {"boardType":"NOTICE","title":"CSRF 위조 테스트"}""";

        mockMvc.perform(post("/api/admin/boards")
                        .session(session)
                        .cookie(xsrfCookie)
                        .header(XSRF_HEADER_NAME, xsrfCookie.getValue() + "-tampered")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden());
    }

    private MockHttpSession login() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + TEST_LOGIN_ID + "\",\"password\":\"" + TEST_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) loginResult.getRequest().getSession(false);
    }

    private Cookie fetchXsrfCookie(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/boards/new").session(session))
                .andExpect(status().isOk())
                .andReturn();
        Cookie xsrfCookie = result.getResponse().getCookie(XSRF_COOKIE_NAME);
        assertThat(xsrfCookie).isNotNull();
        return xsrfCookie;
    }
}
