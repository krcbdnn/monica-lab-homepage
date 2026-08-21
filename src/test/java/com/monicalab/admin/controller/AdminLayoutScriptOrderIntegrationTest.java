package com.monicalab.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.monicalab.support.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

// admin/layout/default.html이 페이지 콘텐츠(${content})보다 뒤에 common-fetch.js를 로드하던 버그의 회귀 테스트.
// 대시보드/각 도메인 목록 화면은 페이지 진입 즉시(이벤트 없이) AdminFetch를 사용하는 인라인 스크립트를 실행하므로,
// 실제 렌더링된 <script> 태그가 문서 순서상 common-fetch.js -> (그 사이 무관한 인라인 스크립트 존재 가능) ->
// AdminFetch를 실제로 사용하는 첫 인라인 스크립트 순으로 배치되는지를 검증한다.
@AutoConfigureMockMvc
class AdminLayoutScriptOrderIntegrationTest extends AbstractIntegrationTest {

    private static final String COMMON_FETCH_SRC = "/js/admin/common-fetch.js";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void dashboardLoadsCommonFetchBeforeItUsesAdminFetch() throws Exception {
        assertCommonFetchLoadsBeforeFirstAdminFetchUsage("/admin/dashboard");
    }

    @Test
    void boardListLoadsCommonFetchBeforeItUsesAdminFetch() throws Exception {
        assertCommonFetchLoadsBeforeFirstAdminFetchUsage("/admin/boards");
    }

    @Test
    void programListLoadsCommonFetchBeforeItUsesAdminFetch() throws Exception {
        assertCommonFetchLoadsBeforeFirstAdminFetchUsage("/admin/programs");
    }

    @Test
    void bannerListLoadsCommonFetchBeforeItUsesAdminFetch() throws Exception {
        assertCommonFetchLoadsBeforeFirstAdminFetchUsage("/admin/banners");
    }

    @Test
    void popupListLoadsCommonFetchBeforeItUsesAdminFetch() throws Exception {
        assertCommonFetchLoadsBeforeFirstAdminFetchUsage("/admin/popups");
    }

    @Test
    void fileListLoadsCommonFetchBeforeItUsesAdminFetch() throws Exception {
        assertCommonFetchLoadsBeforeFirstAdminFetchUsage("/admin/files");
    }

    private void assertCommonFetchLoadsBeforeFirstAdminFetchUsage(String url) throws Exception {
        String body = mockMvc.perform(get(url)
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        Elements scripts = document.select("script");

        int commonFetchIndex = -1;
        int firstAdminFetchUsageIndex = -1;

        for (int i = 0; i < scripts.size(); i++) {
            Element script = scripts.get(i);
            boolean hasSrc = script.hasAttr("src");

            if (commonFetchIndex == -1 && hasSrc && COMMON_FETCH_SRC.equals(script.attr("src"))) {
                commonFetchIndex = i;
            }

            // 인라인 스크립트(src 없음)이면서 실제로 AdminFetch를 사용하는 코드만 대상으로 삼는다.
            // 무관한 인라인 스크립트(예: 다른 전역만 쓰는 코드)가 섞여 있어도 흔들리지 않도록 한다.
            if (firstAdminFetchUsageIndex == -1 && !hasSrc && script.data().contains("AdminFetch")) {
                firstAdminFetchUsageIndex = i;
            }
        }

        assertThat(commonFetchIndex)
                .as("%s 응답에 common-fetch.js <script src> 태그가 존재해야 한다", url)
                .isNotEqualTo(-1);
        assertThat(firstAdminFetchUsageIndex)
                .as("%s 응답에 AdminFetch를 사용하는 인라인 스크립트가 존재해야 한다", url)
                .isNotEqualTo(-1);
        assertThat(commonFetchIndex)
                .as("%s: common-fetch.js(index=%d)가 AdminFetch를 사용하는 첫 인라인 스크립트(index=%d)보다 먼저 로드되어야 한다",
                        url, commonFetchIndex, firstAdminFetchUsageIndex)
                .isLessThan(firstAdminFetchUsageIndex);
    }
}
