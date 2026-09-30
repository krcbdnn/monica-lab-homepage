package com.monicalab.common.exception;

import static com.monicalab.common.exception.PublicErrorPageTest.assertErrorPage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.monicalab.menu.service.MenuService;
import com.monicalab.page.entity.PageType;
import com.monicalab.page.service.PageService;
import com.monicalab.support.AbstractIntegrationTest;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// P15-T5: 공개 화면 500은 production debug endpoint 없이 MockitoBean으로 서비스/메뉴 장애를 흉내 내 검증한다.
// MenuService는 공개 Controller 전에 실행되는 HeaderMenuControllerAdvice의 @ModelAttribute가 호출하므로,
// 메뉴 장애에서도 5xx 페이지가 렌더링된다는 것은 오류 템플릿이 header/menu/theme model에 의존하지 않는다는 뜻이다.
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class PublicErrorPageServerErrorTest extends AbstractIntegrationTest {

    private static final String SECRET = "SECRET-DETAIL";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PageService pageService;

    @MockitoBean
    private MenuService menuService;

    @Test
    void unexpectedServiceExceptionRendersHtmlServerErrorPageWithoutDetails(CapturedOutput output) throws Exception {
        given(pageService.getByType(any(PageType.class))).willThrow(new IllegalStateException(SECRET));

        Document document = assertErrorPage(mockMvc.perform(get("/pages/GREETING")), 500, "error/5xx", SECRET);

        assertThat(document.title()).isEqualTo("일시적인 오류가 발생했습니다 - 모니카영어교육연구소");
        assertThat(document.select("h1").text()).isEqualTo("일시적인 오류가 발생했습니다");
        assertThat(document.outerHtml()).doesNotContain("IllegalStateException", "/pages/GREETING");

        // P15-T1 로그 정책 유지: 미처리 예외는 ERROR + stacktrace(내부 메시지는 로그에만 남는다).
        String logLine = output.getAll().lines()
                .filter(line -> line.contains("Unhandled exception"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Unhandled exception log line not found"));
        assertThat(logLine).contains("ERROR");
        assertThat(output.getAll()).contains("java.lang.IllegalStateException: " + SECRET);
    }

    @Test
    void headerMenuFailureStillRendersIndependentServerErrorPage() throws Exception {
        given(menuService.getPublicMenuTree()).willThrow(new IllegalStateException(SECRET));

        assertErrorPage(mockMvc.perform(get("/")), 500, "error/5xx", SECRET);
        assertErrorPage(mockMvc.perform(get("/boards")), 500, "error/5xx", SECRET);
    }

    // 공개 Controller에서 난 5xx CustomException도 5xx 페이지로 응답하고 ERROR + stacktrace로 남긴다.
    @Test
    void serverErrorCustomExceptionRendersServerErrorPageAndLogsError(CapturedOutput output) throws Exception {
        given(pageService.getByType(any(PageType.class)))
                .willThrow(new CustomException(ErrorCode.FILE_UPLOAD_FAILED));

        assertErrorPage(mockMvc.perform(get("/pages/GREETING")), 500, "error/5xx", "FILE_UPLOAD_FAILED");

        String logLine = output.getAll().lines()
                .filter(line -> line.contains("CustomException: code=FILE_UPLOAD_FAILED, status=500"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("FILE_UPLOAD_FAILED log line not found"));
        assertThat(logLine).contains("ERROR");
    }

    // 도메인 PAGE_NOT_FOUND도 공개 화면에서는 HTML 404다(고정 페이지를 삭제하지 않고 서비스 응답으로 재현).
    @Test
    void pageNotFoundRendersHtmlNotFoundPage() throws Exception {
        given(pageService.getByType(any(PageType.class)))
                .willThrow(new CustomException(ErrorCode.PAGE_NOT_FOUND));

        assertErrorPage(mockMvc.perform(get("/pages/HISTORY")), 404, "error/4xx", "/pages/HISTORY");
    }
}
