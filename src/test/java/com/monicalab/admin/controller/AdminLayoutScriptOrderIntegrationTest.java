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
    private static final String ADMIN_DISPLAY_SRC = "/js/admin/admin-display.js";

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

    // P14-T9C-1: 목록 화면은 페이지 진입 즉시(로드 성공/빈 목록/실패 모두) AdminDisplay를 사용하는 인라인 스크립트를
    // 실행하므로 admin-display.js도 같은 순서 계약(<head>에서 먼저 로드)을 지켜야 한다.
    @Test
    void adminListsLoadAdminDisplayBeforeTheyUseIt() throws Exception {
        for (String url : new String[] {"/admin/boards", "/admin/programs", "/admin/files", "/admin/banners",
                "/admin/popups", "/admin/menus", "/admin/home-pinned-contents"}) {
            assertScriptLoadsBeforeFirstInlineUsage(url, ADMIN_DISPLAY_SRC, "AdminDisplay");
        }
    }

    private void assertCommonFetchLoadsBeforeFirstAdminFetchUsage(String url) throws Exception {
        assertScriptLoadsBeforeFirstInlineUsage(url, COMMON_FETCH_SRC, "AdminFetch");
    }

    private void assertScriptLoadsBeforeFirstInlineUsage(String url, String src, String globalName) throws Exception {
        String body = mockMvc.perform(get(url)
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        Elements scripts = document.select("script");

        int srcIndex = -1;
        int firstUsageIndex = -1;

        for (int i = 0; i < scripts.size(); i++) {
            Element script = scripts.get(i);
            boolean hasSrc = script.hasAttr("src");

            if (srcIndex == -1 && hasSrc && src.equals(script.attr("src"))) {
                srcIndex = i;
            }

            // 인라인 스크립트(src 없음)이면서 실제로 해당 전역을 사용하는 코드만 대상으로 삼는다.
            // 무관한 인라인 스크립트(예: 다른 전역만 쓰는 코드)가 섞여 있어도 흔들리지 않도록 한다.
            if (firstUsageIndex == -1 && !hasSrc && script.data().contains(globalName)) {
                firstUsageIndex = i;
            }
        }

        assertThat(srcIndex)
                .as("%s 응답에 %s <script src> 태그가 존재해야 한다", url, src)
                .isNotEqualTo(-1);
        assertThat(firstUsageIndex)
                .as("%s 응답에 %s를 사용하는 인라인 스크립트가 존재해야 한다", url, globalName)
                .isNotEqualTo(-1);
        assertThat(srcIndex)
                .as("%s: %s(index=%d)가 %s를 사용하는 첫 인라인 스크립트(index=%d)보다 먼저 로드되어야 한다",
                        url, src, srcIndex, globalName, firstUsageIndex)
                .isLessThan(firstUsageIndex);
    }
}
