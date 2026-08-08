package com.monicalab.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

class CustomAuthenticationEntryPointTest {

    @Test
    void commenceWritesUnauthorizedJsonBody() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        CustomAuthenticationEntryPoint entryPoint = new CustomAuthenticationEntryPoint(objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new InsufficientAuthenticationException("인증 필요"));

        assertThat(response.getStatus()).isEqualTo(401);

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("error").path("code").asText()).isEqualTo("UNAUTHORIZED");
    }
}
