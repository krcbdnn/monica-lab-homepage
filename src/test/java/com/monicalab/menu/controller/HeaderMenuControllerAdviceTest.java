package com.monicalab.menu.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.monicalab.menu.entity.Menu;
import com.monicalab.menu.entity.MenuTargetType;
import com.monicalab.menu.repository.MenuRepository;
import com.monicalab.support.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class HeaderMenuControllerAdviceTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MenuRepository menuRepository;

    @BeforeEach
    void setUp() {
        menuRepository.deleteAll();
    }

    @Test
    void publicHeaderShowsOnlyVisibleTopLevelMenus() throws Exception {
        menuRepository.saveAndFlush(topLevel("보이는 메뉴", MenuTargetType.HOME, null, true));
        menuRepository.saveAndFlush(topLevel("숨김 메뉴", MenuTargetType.HOME, null, false));

        Document document = renderHome();
        Elements links = document.select("#quick-menu > li a");

        assertThat(links).hasSize(1);
        assertThat(links.text()).isEqualTo("보이는 메뉴");
    }

    @Test
    void hiddenGroupHidesAllChildrenEvenIfChildIsVisible() throws Exception {
        Menu group = menuRepository.saveAndFlush(group("ABOUT", false));
        menuRepository.saveAndFlush(child(group.getId(), "연구소 소개", MenuTargetType.HOME, null, true));

        Document document = renderHome();

        assertThat(document.select("#quick-menu > li")).isEmpty();
        // #site-nav로 범위를 좁힌다 - footer.html도 별도로 "연구소 소개" 하드코딩 링크를 갖고 있어(P13-T30A
        // 무변경 범위) document 전체 텍스트로 검사하면 header와 무관한 footer 텍스트와 우연히 일치한다.
        assertThat(document.select("#site-nav").text()).doesNotContain("ABOUT", "연구소 소개");
    }

    @Test
    void groupWithAllChildrenHiddenIsHiddenItself() throws Exception {
        Menu group = menuRepository.saveAndFlush(group("ABOUT", true));
        menuRepository.saveAndFlush(child(group.getId(), "연구소 소개", MenuTargetType.HOME, null, false));

        Document document = renderHome();

        assertThat(document.select("#quick-menu > li")).isEmpty();
    }

    @Test
    void groupWithVisibleLeafChildRendersTriggerButtonAndSubmenu() throws Exception {
        Menu group = menuRepository.saveAndFlush(group("ABOUT", true));
        menuRepository.saveAndFlush(child(group.getId(), "연구소 소개", MenuTargetType.HOME, null, true));

        Document document = renderHome();

        Elements groupLi = document.select("#quick-menu > li.has-submenu");
        assertThat(groupLi).hasSize(1);

        Elements trigger = groupLi.select("button.site-nav__trigger");
        assertThat(trigger).hasSize(1);
        assertThat(trigger.text()).isEqualTo("ABOUT");
        assertThat(trigger.attr("aria-expanded")).isEqualTo("false");
        assertThat(trigger.attr("aria-controls")).isEqualTo("submenu-" + group.getId());
        assertThat(trigger.attr("type")).isEqualTo("button");

        Elements submenu = document.select("#submenu-" + group.getId());
        assertThat(submenu).hasSize(1);
        Elements submenuLink = submenu.select("a");
        assertThat(submenuLink.text()).isEqualTo("연구소 소개");
        assertThat(submenuLink.attr("href")).isEqualTo("/");
    }

    // 사용자 조건부 승인 반영: GROUP이 다른 GROUP의 child로 DB에 비정상적으로 들어있는 경우, children
    // 구성 시 targetType!=GROUP 조건으로 명시 제외해야 한다. 정상 CRUD로는 만들 수 없는 상태라
    // repository로 직접 구성한다.
    @Test
    void nonGroupChildRenderedButGroupTypeChildIsExcludedFromSubmenu() throws Exception {
        Menu group = menuRepository.saveAndFlush(group("ABOUT", true));
        menuRepository.saveAndFlush(child(group.getId(), "정상 자식", MenuTargetType.HOME, null, true));
        menuRepository.saveAndFlush(Menu.builder()
                .label("비정상 GROUP 자식").parentId(group.getId()).targetType(MenuTargetType.GROUP)
                .sortOrder(1).isVisible(true).build());

        Document document = renderHome();

        assertThat(document.select("#quick-menu > li.has-submenu")).hasSize(1);
        assertThat(document.select(".site-nav__trigger")).hasSize(1);
        Elements submenuLinks = document.select("#submenu-" + group.getId() + " a");
        assertThat(submenuLinks).hasSize(1);
        assertThat(submenuLinks.text()).isEqualTo("정상 자식");
        assertThat(document.select("#submenu-" + group.getId() + " button")).isEmpty();
        assertThat(document.text()).doesNotContain("비정상 GROUP 자식");
    }

    @Test
    void orphanChildPointingToNonExistentParentIsExcluded() throws Exception {
        menuRepository.saveAndFlush(child(999_999L, "고아 메뉴", MenuTargetType.HOME, null, true));

        Document document = renderHome();

        assertThat(document.select("#quick-menu > li")).isEmpty();
        assertThat(document.text()).doesNotContain("고아 메뉴");
    }

    @Test
    void childOfNonGroupParentIsExcluded() throws Exception {
        Menu nonGroupParent = menuRepository.saveAndFlush(topLevel("일반 메뉴", MenuTargetType.HOME, null, true));
        menuRepository.saveAndFlush(child(nonGroupParent.getId(), "잘못된 자식", MenuTargetType.HOME, null, true));

        Document document = renderHome();
        Elements links = document.select("#quick-menu > li a");

        assertThat(links).hasSize(1);
        assertThat(links.text()).isEqualTo("일반 메뉴");
        assertThat(document.text()).doesNotContain("잘못된 자식");
    }

    @Test
    void hrefIsComputedPerTargetType() throws Exception {
        menuRepository.saveAndFlush(topLevel("HOME", MenuTargetType.HOME, null, true));
        menuRepository.saveAndFlush(topLevel("PAGE", MenuTargetType.PAGE, "INTRODUCTION", true));
        menuRepository.saveAndFlush(topLevel("전체 프로그램", MenuTargetType.PROGRAM_LIST, null, true));
        menuRepository.saveAndFlush(topLevel("특정 프로그램", MenuTargetType.PROGRAM_LIST, "COURSE", true));
        menuRepository.saveAndFlush(topLevel("전체 게시판", MenuTargetType.BOARD_LIST, null, true));
        menuRepository.saveAndFlush(topLevel("특정 게시판", MenuTargetType.BOARD_LIST, "REVIEW", true));
        menuRepository.saveAndFlush(topLevel("내부 링크", MenuTargetType.INTERNAL_URL, "/boards", true));
        menuRepository.saveAndFlush(topLevel("외부 링크", MenuTargetType.EXTERNAL_URL, "https://example.com", true));

        Document document = renderHome();
        Elements links = document.select("#quick-menu > li a");

        assertThat(links.eachAttr("href")).containsExactly(
                "/",
                "/pages/INTRODUCTION",
                "/programs",
                "/programs?programType=COURSE",
                "/boards",
                "/boards?boardType=REVIEW",
                "/boards",
                "https://example.com");
    }

    @Test
    void openInNewTabRendersTargetBlankAndRelNoopenerOnlyWhenTrue() throws Exception {
        menuRepository.saveAndFlush(Menu.builder()
                .label("새 탭").targetType(MenuTargetType.EXTERNAL_URL).targetValue("https://example.com")
                .sortOrder(0).isVisible(true).openInNewTab(true).build());
        menuRepository.saveAndFlush(Menu.builder()
                .label("같은 탭").targetType(MenuTargetType.EXTERNAL_URL).targetValue("https://example.org")
                .sortOrder(1).isVisible(true).openInNewTab(false).build());

        Document document = renderHome();

        Elements newTabLink = document.select("a[href=https://example.com]");
        assertThat(newTabLink.attr("target")).isEqualTo("_blank");
        assertThat(newTabLink.attr("rel")).isEqualTo("noopener noreferrer");

        Elements sameTabLink = document.select("a[href=https://example.org]");
        assertThat(sameTabLink.hasAttr("target")).isFalse();
        assertThat(sameTabLink.hasAttr("rel")).isFalse();
    }

    @Test
    void zeroMenuRendersEmptyQuickMenuWithoutError() throws Exception {
        Document document = renderHome();

        assertThat(document.select("#quick-menu")).hasSize(1);
        assertThat(document.select("#quick-menu > li")).isEmpty();
        assertThat(document.select("#nav-toggle")).hasSize(1);
    }

    @Test
    void headerMenuModelAttributeIsAppliedToAllFourPublicViewControllers() throws Exception {
        menuRepository.saveAndFlush(topLevel("연구소 소개", MenuTargetType.PAGE, "INTRODUCTION", true));

        for (String path : new String[] {"/", "/pages/INTRODUCTION", "/programs", "/boards"}) {
            MvcResult result = mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andReturn();
            assertThat(result.getModelAndView()).isNotNull();
            assertThat(result.getModelAndView().getModel()).containsKey("headerMenuItems");

            Document document = Jsoup.parse(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
            assertThat(document.select("#quick-menu > li a").text()).isEqualTo("연구소 소개");
        }
    }

    // ControllerAdvice의 assignableTypes 스코핑이 실제로 관리자 View에는 적용되지 않는지(불필요한 Menu
    // 조회/model attribute 주입이 없는지) 확인한다.
    @Test
    void headerMenuModelAttributeIsNotAppliedToAdminViewControllers() throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/banners")
                        .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getModelAndView()).isNotNull();
        assertThat(result.getModelAndView().getModel()).doesNotContainKey("headerMenuItems");
    }

    private Document renderHome() throws Exception {
        String body = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return Jsoup.parse(body);
    }

    private Menu topLevel(String label, MenuTargetType targetType, String targetValue, boolean visible) {
        return Menu.builder()
                .label(label).targetType(targetType).targetValue(targetValue)
                .sortOrder(0).isVisible(visible).build();
    }

    private Menu group(String label, boolean visible) {
        return Menu.builder().label(label).targetType(MenuTargetType.GROUP).sortOrder(0).isVisible(visible).build();
    }

    private Menu child(Long parentId, String label, MenuTargetType targetType, String targetValue,
            boolean visible) {
        return Menu.builder()
                .label(label).parentId(parentId).targetType(targetType).targetValue(targetValue)
                .sortOrder(0).isVisible(visible).build();
    }
}
