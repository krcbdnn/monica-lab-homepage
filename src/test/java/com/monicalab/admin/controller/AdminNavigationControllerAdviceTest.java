package com.monicalab.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.monicalab.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// P14-T9A: HeaderMenuControllerAdviceTest/ThemeControllerAdviceTest와 동일한 패턴 - Advice가
// 공급하는 model attribute 자체를 렌더링된 HTML과 별개로 직접 검증한다(AdminViewControllerTest의
// active-nav 테스트는 "결과 HTML"을, 이 클래스는 "model attribute 자체"를 검증해 서로 보완한다).
@AutoConfigureMockMvc
class AdminNavigationControllerAdviceTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void currentAdminPathReflectsActualRequestUriForAdminViewControllers() throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/boards")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getModelAndView()).isNotNull();
        assertThat(result.getModelAndView().getModel().get("currentAdminPath")).isEqualTo("/admin/boards");
    }

    // ControllerAdvice의 assignableTypes 스코핑이 실제로 public View에는 적용되지 않는지 확인한다
    // (HeaderMenuControllerAdviceTest.headerMenuModelAttributeIsNotAppliedToAdminViewControllers와
    // 반대 방향의 동일한 검증).
    @Test
    void currentAdminPathIsNotAppliedToPublicViewControllers() throws Exception {
        MvcResult result = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getModelAndView()).isNotNull();
        assertThat(result.getModelAndView().getModel()).doesNotContainKey("currentAdminPath");
    }
}
