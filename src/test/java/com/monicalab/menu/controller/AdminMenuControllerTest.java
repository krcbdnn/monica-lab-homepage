package com.monicalab.menu.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.monicalab.menu.entity.Menu;
import com.monicalab.menu.entity.MenuTargetType;
import com.monicalab.menu.repository.MenuRepository;
import com.monicalab.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
class AdminMenuControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MenuRepository menuRepository;

    @BeforeEach
    void setUp() {
        menuRepository.deleteAll();
    }

    @Test
    void unauthenticatedAccessToAdminListReturns401() throws Exception {
        mockMvc.perform(get("/api/admin/menus"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createHomeMenuReturns201WithPostDefaults() throws Exception {
        String body = "{\"label\":\"HOME\",\"targetType\":\"HOME\",\"sortOrder\":0}";

        mockMvc.perform(admin(post("/api/admin/menus")).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.visible").value(false))
                .andExpect(jsonPath("$.data.openInNewTab").value(false));
    }

    @Test
    void createWithBlankLabelReturns400() throws Exception {
        String body = "{\"label\":\" \",\"targetType\":\"HOME\",\"sortOrder\":0}";

        mockMvc.perform(admin(post("/api/admin/menus")).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void createHomeWithTargetValueReturns400() throws Exception {
        String body = "{\"label\":\"HOME\",\"targetType\":\"HOME\",\"targetValue\":\"unexpected\",\"sortOrder\":0}";

        mockMvc.perform(admin(post("/api/admin/menus")).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void createGroupWithTargetValueReturns400() throws Exception {
        String body = "{\"label\":\"ABOUT\",\"targetType\":\"GROUP\",\"targetValue\":\"x\",\"sortOrder\":0}";

        mockMvc.perform(admin(post("/api/admin/menus")).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void createGroupWithParentIdReturns400() throws Exception {
        Menu topGroup = menuRepository.saveAndFlush(groupMenu("상위 그룹"));

        String body = "{\"label\":\"하위 그룹\",\"parentId\":" + topGroup.getId()
                + ",\"targetType\":\"GROUP\",\"sortOrder\":0}";

        mockMvc.perform(admin(post("/api/admin/menus")).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void createWithNonExistentParentReturns400() throws Exception {
        String body = "{\"label\":\"HOME\",\"parentId\":999999,\"targetType\":\"HOME\",\"sortOrder\":0}";

        mockMvc.perform(admin(post("/api/admin/menus")).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void createWithParentThatIsNotGroupReturns400() throws Exception {
        Menu homeMenu = menuRepository.saveAndFlush(Menu.builder()
                .label("HOME").targetType(MenuTargetType.HOME).sortOrder(0).isVisible(true).build());

        String body = "{\"label\":\"자식\",\"parentId\":" + homeMenu.getId()
                + ",\"targetType\":\"HOME\",\"sortOrder\":0}";

        mockMvc.perform(admin(post("/api/admin/menus")).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    // 실질적인 "2-depth 초과" 시나리오: GROUP의 자식(B, GROUP이 아님)을 다시 parent로 지정하려는
    // 시도는 "parent는 반드시 GROUP이어야 한다" 규칙에 의해 이미 거부된다(별도 depth 카운터 불필요).
    @Test
    void createWithGrandparentThroughNonGroupChildReturns400() throws Exception {
        Menu group = menuRepository.saveAndFlush(groupMenu("ABOUT"));
        Menu child = menuRepository.saveAndFlush(Menu.builder()
                .label("연구소 소개").parentId(group.getId()).targetType(MenuTargetType.HOME)
                .sortOrder(0).isVisible(true).build());

        String body = "{\"label\":\"손자\",\"parentId\":" + child.getId()
                + ",\"targetType\":\"HOME\",\"sortOrder\":0}";

        mockMvc.perform(admin(post("/api/admin/menus")).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    // GROUP/2-depth 계약 재검증(사용자 지정 케이스 B): 이미 존재하는 GROUP을 다른 GROUP의 자식으로
    // "수정"하려는 시도도 create와 동일하게 거부되어야 한다(targetType=GROUP이면 parentId는 항상
    // null이어야 한다는 규칙은 create/update 양쪽에서 동일한 validateParentAndGroupPlacement를 탄다).
    @Test
    void updateGroupToBeChildOfAnotherGroupReturns400() throws Exception {
        Menu topGroup = menuRepository.saveAndFlush(groupMenu("상위 그룹"));
        Menu otherGroup = menuRepository.saveAndFlush(groupMenu("다른 그룹"));

        String body = "{\"label\":\"다른 그룹\",\"parentId\":" + topGroup.getId()
                + ",\"targetType\":\"GROUP\",\"sortOrder\":0,\"visible\":true,\"openInNewTab\":false}";

        mockMvc.perform(admin(put("/api/admin/menus/{id}", otherGroup.getId())).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    // GROUP/2-depth 계약 재검증(사용자 지정 케이스 C): 일반(비GROUP) 메뉴를 GROUP의 자식으로 생성하는
    // 것은 정상적으로 성공해야 한다. 기존 인터리빙/삭제 테스트들은 이 부모-자식 관계를 repository로
    // 직접 구성했을 뿐, 실제 POST API가 이 조합을 허용하는지는 검증하지 않았다 - 이 테스트로 보완한다.
    @Test
    void createNonGroupMenuUnderGroupParentReturns201() throws Exception {
        Menu group = menuRepository.saveAndFlush(groupMenu("ABOUT"));

        String body = "{\"label\":\"연구소 소개\",\"parentId\":" + group.getId()
                + ",\"targetType\":\"HOME\",\"sortOrder\":0}";

        mockMvc.perform(admin(post("/api/admin/menus")).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.parentId").value(group.getId()));
    }

    @Test
    void createWithSelfAsParentOnUpdateReturns400() throws Exception {
        Menu group = menuRepository.saveAndFlush(groupMenu("ABOUT"));
        Menu child = menuRepository.saveAndFlush(Menu.builder()
                .label("연구소 소개").parentId(group.getId()).targetType(MenuTargetType.HOME)
                .sortOrder(0).isVisible(true).build());

        String body = "{\"label\":\"연구소 소개\",\"parentId\":" + child.getId()
                + ",\"targetType\":\"HOME\",\"sortOrder\":0,\"visible\":true,\"openInNewTab\":false}";

        mockMvc.perform(admin(put("/api/admin/menus/{id}", child.getId())).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void createPageWithoutTargetValueReturns400() throws Exception {
        String body = "{\"label\":\"연구소 소개\",\"targetType\":\"PAGE\",\"sortOrder\":0}";

        mockMvc.perform(admin(post("/api/admin/menus")).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void createPageWithInvalidPageTypeReturns400() throws Exception {
        String body = "{\"label\":\"연구소 소개\",\"targetType\":\"PAGE\",\"targetValue\":\"NOT_A_TYPE\",\"sortOrder\":0}";

        mockMvc.perform(admin(post("/api/admin/menus")).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void createPageWithValidPageTypeReturns201() throws Exception {
        String body = "{\"label\":\"연구소 소개\",\"targetType\":\"PAGE\",\"targetValue\":\"INTRODUCTION\",\"sortOrder\":0}";

        mockMvc.perform(admin(post("/api/admin/menus")).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.targetValue").value("INTRODUCTION"));
    }

    @Test
    void createProgramListWithoutTargetValueReturns201WithNullTargetValue() throws Exception {
        String body = "{\"label\":\"프로그램\",\"targetType\":\"PROGRAM_LIST\",\"sortOrder\":0}";

        mockMvc.perform(admin(post("/api/admin/menus")).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.targetValue").doesNotExist());
    }

    @Test
    void createProgramListWithInvalidProgramTypeReturns400() throws Exception {
        String body = "{\"label\":\"프로그램\",\"targetType\":\"PROGRAM_LIST\",\"targetValue\":\"NOT_A_TYPE\",\"sortOrder\":0}";

        mockMvc.perform(admin(post("/api/admin/menus")).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void createBoardListWithValidBoardTypeReturns201() throws Exception {
        String body = "{\"label\":\"강의 후기\",\"targetType\":\"BOARD_LIST\",\"targetValue\":\"REVIEW\",\"sortOrder\":0}";

        mockMvc.perform(admin(post("/api/admin/menus")).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.targetValue").value("REVIEW"));
    }

    @Test
    void createInternalUrlRejectsProtocolRelativeValue() throws Exception {
        String body = "{\"label\":\"내부\",\"targetType\":\"INTERNAL_URL\",\"targetValue\":\"//evil.com\",\"sortOrder\":0}";

        mockMvc.perform(admin(post("/api/admin/menus")).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    // 실측 확인: 원본 backslash("/\evil.com")는 이미 java.net.URI가 URISyntaxException을 던져
    // isValidInternalPath에서 거부되지만(JDK 구현에 기대는 것이라 MenuService에도 명시적 방어를 추가함),
    // 아래 테스트로 이 동작을 회귀 검증한다.
    @Test
    void createInternalUrlRejectsRawBackslash() throws Exception {
        String body = "{\"label\":\"내부\",\"targetType\":\"INTERNAL_URL\",\"targetValue\":\"/\\\\evil.com\",\"sortOrder\":0}";

        mockMvc.perform(admin(post("/api/admin/menus")).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    // 실측 확인: percent-encoded backslash("/%5Cevil.com")는 원본 backslash와 달리 java.net.URI 파싱을
    // 통과하고 host/scheme도 모두 null이라 기존 검사만으로는 통과했다(실제 실행으로 확인). 브라우저가
    // 원본 backslash를 forward slash로 정규화하는 동작과 결합될 위험을 막기 위해 명시적으로 거부하도록
    // 수정했다.
    @Test
    void createInternalUrlRejectsPercentEncodedBackslash() throws Exception {
        String body = "{\"label\":\"내부\",\"targetType\":\"INTERNAL_URL\",\"targetValue\":\"/%5Cevil.com\",\"sortOrder\":0}";

        mockMvc.perform(admin(post("/api/admin/menus")).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void createInternalUrlRejectsValueNotStartingWithSlash() throws Exception {
        String body = "{\"label\":\"내부\",\"targetType\":\"INTERNAL_URL\",\"targetValue\":\"boards\",\"sortOrder\":0}";

        mockMvc.perform(admin(post("/api/admin/menus")).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void createInternalUrlWithValidPathReturns201() throws Exception {
        String body = "{\"label\":\"내부\",\"targetType\":\"INTERNAL_URL\",\"targetValue\":\"/boards\",\"sortOrder\":0}";

        mockMvc.perform(admin(post("/api/admin/menus")).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.targetValue").value("/boards"));
    }

    @Test
    void createExternalUrlRejectsJavascriptScheme() throws Exception {
        String body = "{\"label\":\"외부\",\"targetType\":\"EXTERNAL_URL\","
                + "\"targetValue\":\"javascript:alert(1)\",\"sortOrder\":0}";

        mockMvc.perform(admin(post("/api/admin/menus")).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void createExternalUrlWithHttpsReturns201AndOpenInNewTabIsSelectableIndependently() throws Exception {
        String body = "{\"label\":\"외부\",\"targetType\":\"EXTERNAL_URL\","
                + "\"targetValue\":\"https://example.com\",\"sortOrder\":0,\"openInNewTab\":false}";

        // EXTERNAL_URL이라고 해서 openInNewTab이 강제로 true가 되지 않는지 확인한다.
        mockMvc.perform(admin(post("/api/admin/menus")).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.openInNewTab").value(false));
    }

    @Test
    void adminListReturnsChildrenInterleavedRightAfterTheirParent() throws Exception {
        Menu group = menuRepository.saveAndFlush(groupMenu("ABOUT"));
        Menu home = menuRepository.saveAndFlush(Menu.builder()
                .label("HOME").targetType(MenuTargetType.HOME).sortOrder(1).isVisible(true).build());
        Menu child = menuRepository.saveAndFlush(Menu.builder()
                .label("연구소 소개").parentId(group.getId()).targetType(MenuTargetType.HOME)
                .sortOrder(0).isVisible(true).build());

        mockMvc.perform(admin(get("/api/admin/menus")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(group.getId()))
                .andExpect(jsonPath("$.data[1].id").value(child.getId()))
                .andExpect(jsonPath("$.data[2].id").value(home.getId()));
    }

    // P13-T30A DoD: orphan(부모가 없거나 GROUP이 아닌 부모를 가리키는) 행이 있어도 관리자 목록
    // 응답에서 조용히 사라지지 않아야 한다. 정상 CRUD로는 만들 수 없는 상태라 repository로 직접 구성한다.
    @Test
    void adminListStillIncludesOrphanRowsInsteadOfSilentlyDroppingThem() throws Exception {
        Menu orphan = menuRepository.saveAndFlush(Menu.builder()
                .label("고아 메뉴").parentId(999_999L).targetType(MenuTargetType.HOME)
                .sortOrder(0).isVisible(true).build());

        mockMvc.perform(admin(get("/api/admin/menus")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(orphan.getId()));
    }

    @Test
    void putRequiresVisibleAndOpenInNewTab() throws Exception {
        Menu menu = menuRepository.saveAndFlush(Menu.builder()
                .label("HOME").targetType(MenuTargetType.HOME).sortOrder(0).isVisible(false).build());

        String missingFieldsBody = "{\"label\":\"HOME\",\"targetType\":\"HOME\",\"sortOrder\":0}";

        mockMvc.perform(admin(put("/api/admin/menus/{id}", menu.getId())).content(missingFieldsBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));

        String validBody = "{\"label\":\"HOME\",\"targetType\":\"HOME\",\"sortOrder\":0,"
                + "\"visible\":true,\"openInNewTab\":false}";

        mockMvc.perform(admin(put("/api/admin/menus/{id}", menu.getId())).content(validBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visible").value(true));
    }

    // AdminMenuController.update()가 @Validated({Default.class, OnUpdate.class})를 선언하고 있어
    // PUT에서도 Default 그룹(label/targetType/sortOrder/targetValue) 제약이 OnUpdate 제약(visible/
    // openInNewTab)과 함께 모두 실행되는지 재검증한다. Default.class를 빠뜨리면 이 중 상당수가
    // 조용히 통과해버릴 수 있어 회귀 방지 목적으로 8개 필드 위반을 모두 명시적으로 확인한다.
    @Test
    void putEnforcesAllDefaultGroupConstraintsAlongsideOnUpdateConstraints() throws Exception {
        Menu menu = menuRepository.saveAndFlush(Menu.builder()
                .label("HOME").targetType(MenuTargetType.HOME).sortOrder(0).isVisible(false).build());

        assertPutReturns400(menu.getId(),
                "{\"label\":\" \",\"targetType\":\"HOME\",\"sortOrder\":0,"
                        + "\"visible\":true,\"openInNewTab\":false}");
        assertPutReturns400(menu.getId(),
                "{\"label\":\"HOME\",\"sortOrder\":0,\"visible\":true,\"openInNewTab\":false}");
        assertPutReturns400(menu.getId(),
                "{\"label\":\"HOME\",\"targetType\":\"HOME\",\"visible\":true,\"openInNewTab\":false}");
        assertPutReturns400(menu.getId(),
                "{\"label\":\"HOME\",\"targetType\":\"HOME\",\"sortOrder\":-1,"
                        + "\"visible\":true,\"openInNewTab\":false}");
        assertPutReturns400(menu.getId(),
                "{\"label\":\"" + "가".repeat(51) + "\",\"targetType\":\"HOME\",\"sortOrder\":0,"
                        + "\"visible\":true,\"openInNewTab\":false}");
        assertPutReturns400(menu.getId(),
                "{\"label\":\"내부\",\"targetType\":\"INTERNAL_URL\",\"targetValue\":\"/" + "a".repeat(255)
                        + "\",\"sortOrder\":0,\"visible\":true,\"openInNewTab\":false}");
        assertPutReturns400(menu.getId(),
                "{\"label\":\"HOME\",\"targetType\":\"HOME\",\"sortOrder\":0,\"openInNewTab\":false}");
        assertPutReturns400(menu.getId(),
                "{\"label\":\"HOME\",\"targetType\":\"HOME\",\"sortOrder\":0,\"visible\":true}");
    }

    private void assertPutReturns400(Long id, String body) throws Exception {
        mockMvc.perform(admin(put("/api/admin/menus/{id}", id)).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void changingGroupTargetTypeAwayWhileItHasChildrenReturns409() throws Exception {
        Menu group = menuRepository.saveAndFlush(groupMenu("ABOUT"));
        menuRepository.saveAndFlush(Menu.builder()
                .label("연구소 소개").parentId(group.getId()).targetType(MenuTargetType.HOME)
                .sortOrder(0).isVisible(true).build());

        String body = "{\"label\":\"ABOUT\",\"targetType\":\"HOME\",\"sortOrder\":0,"
                + "\"visible\":true,\"openInNewTab\":false}";

        mockMvc.perform(admin(put("/api/admin/menus/{id}", group.getId())).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MENU_HAS_CHILDREN"));
    }

    @Test
    void patchVisibilityUpdatesSingleField() throws Exception {
        Menu menu = menuRepository.saveAndFlush(Menu.builder()
                .label("HOME").targetType(MenuTargetType.HOME).sortOrder(0).isVisible(false).build());

        mockMvc.perform(admin(patch("/api/admin/menus/{id}/visibility", menu.getId()))
                        .content("{\"visible\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visible").value(true));
    }

    @Test
    void patchOrderUpdatesSortOrder() throws Exception {
        Menu menu = menuRepository.saveAndFlush(Menu.builder()
                .label("HOME").targetType(MenuTargetType.HOME).sortOrder(0).isVisible(true).build());

        mockMvc.perform(admin(patch("/api/admin/menus/{id}/order", menu.getId()))
                        .content("{\"sortOrder\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sortOrder").value(5));
    }

    @Test
    void deleteMenuWithChildrenReturns409() throws Exception {
        Menu group = menuRepository.saveAndFlush(groupMenu("ABOUT"));
        menuRepository.saveAndFlush(Menu.builder()
                .label("연구소 소개").parentId(group.getId()).targetType(MenuTargetType.HOME)
                .sortOrder(0).isVisible(true).build());

        mockMvc.perform(admin(delete("/api/admin/menus/{id}", group.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MENU_HAS_CHILDREN"));
    }

    @Test
    void deleteMenuWithoutChildrenRemovesItAndSubsequentGetReturns404() throws Exception {
        Menu menu = menuRepository.saveAndFlush(Menu.builder()
                .label("삭제 대상").targetType(MenuTargetType.HOME).sortOrder(0).isVisible(false).build());

        mockMvc.perform(admin(delete("/api/admin/menus/{id}", menu.getId())))
                .andExpect(status().isNoContent());

        mockMvc.perform(admin(get("/api/admin/menus/{id}", menu.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MENU_NOT_FOUND"));
    }

    private Menu groupMenu(String label) {
        return Menu.builder().label(label).targetType(MenuTargetType.GROUP).sortOrder(0).isVisible(true).build();
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder
                .with(user("admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON);
    }
}
