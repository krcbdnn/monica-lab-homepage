package com.monicalab.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.monicalab.support.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// P15-T7A: /robots.txt는 static 리소스로 인증 없이 제공되는 도메인 비의존 crawler 지침이다(접근 통제 아님).
// 관리자 화면/API만 disallow하고 sitemap/도메인은 두지 않는다.
@AutoConfigureMockMvc
class RobotsTxtTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void robotsTxtDisallowsOnlyAdminPathsWithoutSitemapOrDomain() throws Exception {
        String body = mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        List<String> lines = body.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        assertThat(lines).containsExactly("User-agent: *", "Disallow: /admin/", "Disallow: /api/admin/");
        assertThat(body).doesNotContainIgnoringCase("sitemap");
        assertThat(body).doesNotContain("http");
    }
}
