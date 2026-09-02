package com.monicalab.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.monicalab.common.exception.support.ExceptionTestController;
import com.monicalab.menu.service.MenuService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// P13-T30B: @WebMvcTest는 controllers 필터와 무관하게 클래스패스의 모든 @ControllerAdvice 빈을
// 항상 컨텍스트에 포함시킨다(Spring Boot 표준 동작). 신규 HeaderMenuControllerAdvice가
// MenuService를 생성자로 요구하는데 이 슬라이스에는 @Service 빈이 로드되지 않아, MenuService를
// MockitoBean으로 대체하지 않으면 컨텍스트 기동이 실패한다. HeaderMenuControllerAdvice의 동작
// 자체는 이 테스트의 검증 대상이 아니므로(ExceptionTestController는 assignableTypes에 없어 어차피
// 호출되지 않음) mock으로 충분하다.
@WebMvcTest(controllers = ExceptionTestController.class)
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MenuService menuService;

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
}
