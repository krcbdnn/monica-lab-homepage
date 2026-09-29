package com.monicalab.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.monicalab.common.exception.support.ExceptionTestController;
import com.monicalab.menu.service.MenuService;
import com.monicalab.theme.service.SiteThemeSettingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// P13-T30B: @WebMvcTest는 controllers 필터와 무관하게 클래스패스의 모든 @ControllerAdvice 빈을
// 항상 컨텍스트에 포함시킨다(Spring Boot 표준 동작). HeaderMenuControllerAdvice가 MenuService를,
// P14-T8C의 ThemeControllerAdvice가 SiteThemeSettingService를 각각 생성자로 요구하는데 이 슬라이스에는
// @Service 빈이 로드되지 않아, 둘 다 MockitoBean으로 대체하지 않으면 컨텍스트 기동이 실패한다. 두
// Advice의 동작 자체는 이 테스트의 검증 대상이 아니므로(ExceptionTestController는 둘 다의
// assignableTypes에 없어 어차피 호출되지 않음) mock으로 충분하다.
@WebMvcTest(controllers = ExceptionTestController.class)
@AutoConfigureMockMvc(addFilters = false)
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    // P15-T1: CustomException을 던진 테스트 controller 메서드의 stack frame. stacktrace가 로그에 출력됐는지를
    // timestamp/thread/logger 포맷과 무관하게 판별하는 semantic marker로만 사용한다.
    private static final String THROW_SITE_FRAME = "at " + ExceptionTestController.class.getName() + ".";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MenuService menuService;

    @MockitoBean
    private SiteThemeSettingService siteThemeSettingService;

    @Test
    void successResponseFollowsApiResponseFormat() throws Exception {
        mockMvc.perform(get("/test/exceptions/ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("ok"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void customExceptionReturnsMappedErrorCode() throws Exception {
        mockMvc.perform(get("/test/exceptions/custom"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("PROGRAM_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value(ErrorCode.PROGRAM_NOT_FOUND.getMessage()));
    }

    @Test
    void validationFailureReturnsFieldErrors() throws Exception {
        mockMvc.perform(post("/test/exceptions/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.error.fields[0].field").value("name"));
    }

    @Test
    void unhandledExceptionReturnsInternalServerError() throws Exception {
        mockMvc.perform(get("/test/exceptions/unhandled"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"));
    }

    @Test
    void unmappedEndpointReturnsDefinedNotFoundFormat() throws Exception {
        mockMvc.perform(get("/no-such-endpoint"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    // P15-T1: 4xx CustomException은 code/status만 담은 WARN 한 줄로 남기고 stacktrace를 출력하지 않는다.
    @Test
    void clientErrorCustomExceptionLogsSingleWarnLineWithoutStacktrace(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/exceptions/custom"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROGRAM_NOT_FOUND"));

        String logLine = findLogLine(output, "CustomException: code=PROGRAM_NOT_FOUND, status=404");
        assertThat(logLine).contains("WARN");
        assertThat(output.getAll()).doesNotContain(THROW_SITE_FRAME);
    }

    // P15-T1: 5xx CustomException은 ERROR + stacktrace로 남기며 API 응답 계약은 기존과 같다.
    @Test
    void serverErrorCustomExceptionLogsErrorWithStacktrace(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/exceptions/custom-server-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("FILE_UPLOAD_FAILED"))
                .andExpect(jsonPath("$.error.message").value(ErrorCode.FILE_UPLOAD_FAILED.getMessage()));

        String logLine = findLogLine(output, "CustomException: code=FILE_UPLOAD_FAILED, status=500");
        assertThat(logLine).contains("ERROR");
        assertThat(output.getAll()).contains(THROW_SITE_FRAME);
    }

    private static String findLogLine(CapturedOutput output, String marker) {
        return output.getAll().lines()
                .filter(line -> line.contains(marker))
                .findFirst()
                .orElseThrow(() -> new AssertionError("log line not found: " + marker));
    }
}
