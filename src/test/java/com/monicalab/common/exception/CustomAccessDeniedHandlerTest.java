package com.monicalab.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

class CustomAccessDeniedHandlerTest {

    @Test
    void handleWritesAccessDeniedJsonBody() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        CustomAccessDeniedHandler handler = new CustomAccessDeniedHandler(objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("접근 거부"));

        assertThat(response.getStatus()).isEqualTo(403);

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("error").path("code").asText()).isEqualTo("ACCESS_DENIED");
        assertThat(body.path("error").path("message").asText()).isEqualTo(ErrorCode.ACCESS_DENIED.getMessage());
    }
}
