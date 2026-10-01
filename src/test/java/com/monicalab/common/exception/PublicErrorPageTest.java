package com.monicalab.common.exception;

import static com.monicalab.common.exception.GlobalExceptionHandlerTest.BROWSER_ACCEPT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.monicalab.board.entity.Board;
import com.monicalab.board.entity.BoardType;
import com.monicalab.board.repository.BoardRepository;
import com.monicalab.program.entity.Program;
import com.monicalab.program.entity.ProgramType;
import com.monicalab.program.entity.RecruitStatus;
import com.monicalab.program.repository.ProgramRepository;
import com.monicalab.support.AbstractIntegrationTest;
import com.monicalab.support.PublicHeadAssertions;
import jakarta.servlet.RequestDispatcher;
import java.nio.charset.StandardCharsets;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// P15-T5: 공개 HTML 오류 페이지 계약. 공개 View Controller 오류와 /api/ 외 미매핑 경로는 실제 HTTP status를
// 유지한 독립 HTML(error/4xx, error/5xx)로, /api/**·관리자 상세 404·Security 401/302는 기존 계약 그대로 응답한다.
// 500(서비스/메뉴 장애)은 MockitoBean이 필요해 PublicErrorPageServerErrorTest로 분리했다.
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class PublicErrorPageTest extends AbstractIntegrationTest {

    private static final long MISSING_ID = 999_999_999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BoardRepository boardRepository;

    @Autowired
    private ProgramRepository programRepository;

    @Test
    void unmappedPublicPathRendersHtmlNotFoundPageRegardlessOfAccept() throws Exception {
        for (String accept : new String[] {BROWSER_ACCEPT, MediaType.APPLICATION_JSON_VALUE}) {
            Document document = assertErrorPage(mockMvc.perform(get("/no-such-path").header(HttpHeaders.ACCEPT, accept)),
                    404, "error/4xx", "/no-such-path");

            assertThat(document.title()).isEqualTo("페이지를 찾을 수 없습니다 - 모니카영어교육연구소");
            assertThat(document.select("h1").text()).isEqualTo("페이지를 찾을 수 없습니다");
        }
    }

    @Test
    void missingOrPrivateBoardAndProgramRenderHtmlNotFoundPage() throws Exception {
        Long privateBoardId = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE).title("비공개 공지").isPublic(false).build()).getId();
        Long privateProgramId = programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.COURSE).title("비공개 프로그램")
                .recruitStatus(RecruitStatus.OPEN).isPublic(false).build()).getId();

        for (String path : new String[] {
                "/boards/" + MISSING_ID, "/boards/" + privateBoardId,
                "/programs/" + MISSING_ID, "/programs/" + privateProgramId}) {
            assertErrorPage(mockMvc.perform(get(path)), 404, "error/4xx", path);
        }
    }

    // 경로 변수 변환 실패는 타입(enum/Long)과 무관하게 존재할 수 없는 주소이므로 404다.
    @Test
    void pathVariableTypeMismatchRendersHtmlNotFoundPage() throws Exception {
        for (String path : new String[] {"/pages/NOPE", "/boards/abc", "/programs/abc"}) {
            assertErrorPage(mockMvc.perform(get(path)), 404, "error/4xx", path);
        }
    }

    // query 값 변환/검증 실패는 잘못된 요청 400이다(4xx 템플릿의 일반 문구).
    @Test
    void invalidQueryParameterRendersHtmlBadRequestPage() throws Exception {
        for (String[] request : new String[][] {
                {"/boards", "boardType", "NOPE"},
                {"/programs", "programType", "NOPE"},
                {"/boards", "sort", "nope,desc"}}) {
            Document document = assertErrorPage(
                    mockMvc.perform(get(request[0]).param(request[1], request[2])), 400, "error/4xx", "NOPE");

            assertThat(document.title()).isEqualTo("잘못된 요청입니다 - 모니카영어교육연구소");
            assertThat(document.select("h1").text()).isEqualTo("잘못된 요청입니다");
        }
    }

    // 필터 단계 오류 등 Boot 기본 /error 경로도 같은 4xx/5xx 템플릿으로 해석된다(Whitelabel 대체).
    @Test
    void bootErrorPathResolvesSameIndependentTemplates() throws Exception {
        assertErrorPage(mockMvc.perform(get("/error").accept(MediaType.TEXT_HTML)
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)), 404, "error/4xx", "/error");

        Document document = assertErrorPage(mockMvc.perform(get("/error").accept(MediaType.TEXT_HTML)
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)), 500, "error/5xx", "/error");
        assertThat(document.select("h1").text()).isEqualTo("일시적인 오류가 발생했습니다");
    }

    // /api/**는 브라우저 Accept(text/html 우선)로 요청해도 기존 JSON 오류 계약을 유지한다.
    @Test
    void apiErrorsKeepJsonContract() throws Exception {
        mockMvc.perform(get("/api/no-such").header(HttpHeaders.ACCEPT, BROWSER_ACCEPT))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/api/boards/{id}", MISSING_ID).header(HttpHeaders.ACCEPT, BROWSER_ACCEPT))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("BOARD_NOT_FOUND"));
    }

    @Test
    void adminAndSecurityContractsAreUnchanged() throws Exception {
        mockMvc.perform(get("/admin/boards"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/admin/login"));

        mockMvc.perform(get("/api/admin/boards"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        // 관리자 View Controller 안에서 발생한 상세 404는 PublicViewExceptionHandler 대상이 아니므로 JSON을 유지한다.
        mockMvc.perform(get("/admin/boards/{id}", MISSING_ID).with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BOARD_NOT_FOUND"));
        mockMvc.perform(get("/admin/programs/{id}", MISSING_ID).with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROGRAM_NOT_FOUND"));

        // 반면 어떤 Controller에도 매핑되지 않은 /admin/ 주소는 /api/ 외 미매핑 경로라 HTML 404다.
        assertErrorPage(mockMvc.perform(get("/admin/no-such").with(admin())), 404, "error/4xx", "/admin/no-such");
    }

    // P15-T1 로그 정책 유지: 4xx CustomException은 code/status만 담은 WARN 한 줄(stacktrace 없음).
    @Test
    void publicClientErrorLogsSingleWarnLineWithoutStacktrace(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/programs/{id}", MISSING_ID)).andExpect(status().isNotFound());

        String logLine = output.getAll().lines()
                .filter(line -> line.contains("CustomException: code=PROGRAM_NOT_FOUND, status=404"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("PROGRAM_NOT_FOUND log line not found"));
        assertThat(logLine).contains("WARN");
        assertThat(output.getAll()).doesNotContain("at com.monicalab.program.service.ProgramService.");
    }

    private static RequestPostProcessor admin() {
        return user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    // 공통 검증: 실제 status 유지, HTML, 독립 오류 템플릿(main/h1 1개/홈 링크), 내부 정보(ErrorCode/예외/경로) 비노출.
    static Document assertErrorPage(ResultActions result, int expectedStatus, String expectedView,
            String requestDetail) throws Exception {
        String body = result
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(view().name(expectedView))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        assertThat(document.select("html").attr("lang")).isEqualTo("ko");
        assertThat(document.select("main#error-main.error-page")).hasSize(1);
        assertThat(document.select("h1")).hasSize(1);
        assertThat(document.select(".error-page__code").text()).isEqualTo(String.valueOf(expectedStatus));
        assertThat(document.select("a.error-page__home").attr("href")).isEqualTo("/");
        assertThat(document.select("script")).isEmpty();
        assertThat(document.select("#site-header, #site-footer")).isEmpty();
        // 템플릿 작성 주석(내부 class/ErrorCode 이름 포함)은 Thymeleaf parser-level 주석이라 응답에 남지 않아야 한다.
        assertThat(body).doesNotContain("<!--", "ErrorCode", "stacktrace", "_NOT_FOUND", "INVALID_INPUT_VALUE",
                "INTERNAL_SERVER_ERROR", "Exception", "at com.", "at org.", requestDetail);
        // P15-T7B: 독립 오류 템플릿에는 공개 layout의 OG/apple-touch-icon 메타가 들어가지 않는다.
        PublicHeadAssertions.assertNoPublicBrandMeta(document);
        return document;
    }
}
