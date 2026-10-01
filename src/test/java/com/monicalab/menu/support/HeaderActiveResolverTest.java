package com.monicalab.menu.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.monicalab.menu.dto.HeaderMenuItem;
import com.monicalab.menu.entity.MenuTargetType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// P13-T37: HeaderActiveResolver는 Spring 컨텍스트/DB 없이 순수 Java로 동작하므로 pure JUnit으로
// 검증한다(추가 DB 조회를 하지 않는다는 계약 자체가 이 클래스에는 Repository 의존성이 전혀 없다는
// 사실로 이미 보장된다).
class HeaderActiveResolverTest {

    private final HeaderActiveResolver resolver = new HeaderActiveResolver();

    private HeaderMenuItem leaf(String href, MenuTargetType targetType) {
        return new HeaderMenuItem(1L, "라벨", href, false, targetType, List.of());
    }

    private HeaderMenuItem group(List<HeaderMenuItem> children) {
        return new HeaderMenuItem(1L, "그룹", null, false, MenuTargetType.GROUP, children);
    }

    // ---------- HOME ----------

    @Test
    void homeActiveWhenPathIsExactlyRoot() {
        assertThat(resolver.isHomeActive(new CurrentLocation("/", null))).isTrue();
    }

    @Test
    void homeActiveIgnoresQuery() {
        // D. HOME + query
        assertThat(resolver.isHomeActive(new CurrentLocation("/", "utm_source=test"))).isTrue();
    }

    @Test
    void homeInactiveOnOtherPaths() {
        assertThat(resolver.isHomeActive(new CurrentLocation("/boards", null))).isFalse();
    }

    // ---------- PAGE ----------

    @Test
    void pageActiveOnExactPathMatch() {
        HeaderMenuItem item = leaf("/pages/INTRODUCTION", MenuTargetType.PAGE);
        assertThat(resolver.isActive(item, new CurrentLocation("/pages/INTRODUCTION", null), null, null)).isTrue();
    }

    @Test
    void pageActiveIgnoresNoiseQuery() {
        // B. PAGE + noise query
        HeaderMenuItem item = leaf("/pages/INTRODUCTION", MenuTargetType.PAGE);
        assertThat(resolver.isActive(item, new CurrentLocation("/pages/INTRODUCTION", "utm_source=test"), null, null))
                .isTrue();
    }

    @Test
    void pageInactiveOnPrefixCollision() {
        // C. PAGE prefix false-positive
        HeaderMenuItem item = leaf("/pages/INTRODUCTION", MenuTargetType.PAGE);
        assertThat(resolver.isActive(item, new CurrentLocation("/pages/INTRODUCTION2", null), null, null)).isFalse();
    }

    // ---------- BOARD_LIST ----------

    @Test
    void boardListActiveOnExactBoardTypeMatch() {
        HeaderMenuItem item = leaf("/boards?boardType=NOTICE", MenuTargetType.BOARD_LIST);
        assertThat(resolver.isActive(item, new CurrentLocation("/boards", "boardType=NOTICE"), null, null)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "boardType=REVIEW&page=2, true",
            "boardType=REVIEW&keyword=test&page=1, true",
            "boardType=REVIEW&size=20&pageJump=3, true",
            "boardType=NOTICE, false",
            "'', false",
    })
    void boardListActiveIgnoresNoiseQuery(String query, boolean expected) {
        HeaderMenuItem item = leaf("/boards?boardType=REVIEW", MenuTargetType.BOARD_LIST);
        assertThat(resolver.isActive(item, new CurrentLocation("/boards", query), null, null)).isEqualTo(expected);
    }

    @Test
    void boardListInactiveWhenNoBoardTypeInRequest() {
        HeaderMenuItem item = leaf("/boards?boardType=NOTICE", MenuTargetType.BOARD_LIST);
        assertThat(resolver.isActive(item, new CurrentLocation("/boards", null), null, null)).isFalse();
    }

    @Test
    void boardListDetailUsesEntityGroundTruthOverRequestQuery() {
        // Board 상세: 목록에서 클릭 진입(query 있음) - query와 엔티티가 일치하는 정상 케이스
        HeaderMenuItem item = leaf("/boards?boardType=REVIEW", MenuTargetType.BOARD_LIST);
        assertThat(resolver.isActive(item,
                new CurrentLocation("/boards/42", "boardType=REVIEW&keyword=x&page=0"), "REVIEW", null)).isTrue();
    }

    @Test
    void boardListDetailActiveWithoutAnyQuery() {
        // Board 상세 직접 URL 진입(query 전혀 없음) - 엔티티만으로 판정
        HeaderMenuItem item = leaf("/boards?boardType=NOTICE", MenuTargetType.BOARD_LIST);
        assertThat(resolver.isActive(item, new CurrentLocation("/boards/7", null), "NOTICE", null)).isTrue();
    }

    @Test
    void boardListDetailEntityOverridesStaleRequestQuery() {
        // 엔티티 값이 request query와 달라도(예: 공유된 오래된 링크) 엔티티가 우선
        HeaderMenuItem item = leaf("/boards?boardType=REVIEW", MenuTargetType.BOARD_LIST);
        assertThat(resolver.isActive(item,
                new CurrentLocation("/boards/9", "boardType=NOTICE"), "REVIEW", null)).isTrue();
    }

    @Test
    void boardListGenericReviewNotActiveWhenProgramTypePresent() {
        HeaderMenuItem generic = leaf("/boards?boardType=REVIEW", MenuTargetType.BOARD_LIST);
        assertThat(resolver.isActive(generic,
                new CurrentLocation("/boards", "boardType=REVIEW&programType=COURSE"), null, null)).isFalse();
    }

    @Test
    void boardListNeverCandidateOutsideBoardsPath() {
        HeaderMenuItem item = leaf("/boards?boardType=NOTICE", MenuTargetType.BOARD_LIST);
        assertThat(resolver.isActive(item, new CurrentLocation("/pages/HISTORY", "boardType=NOTICE"), null, null))
                .isFalse();
    }

    // ---------- PROGRAM_LIST ----------

    @Test
    void programListActiveOnExactProgramTypeMatch() {
        HeaderMenuItem item = leaf("/programs?programType=COURSE", MenuTargetType.PROGRAM_LIST);
        assertThat(resolver.isActive(item, new CurrentLocation("/programs", "programType=COURSE"), null, null))
                .isTrue();
    }

    @Test
    void programListInactiveWhenNoProgramTypeInRequest() {
        HeaderMenuItem item = leaf("/programs?programType=COURSE", MenuTargetType.PROGRAM_LIST);
        assertThat(resolver.isActive(item, new CurrentLocation("/programs", null), null, null)).isFalse();
    }

    @Test
    void programListDetailRequiresEntityOverrideBecauseNoQueryEverExists() {
        // Program 상세는 목록에서 클릭해도 query가 전혀 붙지 않는다(실제 코드 확인) - override 필수
        HeaderMenuItem item = leaf("/programs?programType=SPECIAL", MenuTargetType.PROGRAM_LIST);
        assertThat(resolver.isActive(item, new CurrentLocation("/programs/3", null), null, "SPECIAL")).isTrue();
    }

    @Test
    void programListDetailInactiveForOtherType() {
        HeaderMenuItem item = leaf("/programs?programType=COURSE", MenuTargetType.PROGRAM_LIST);
        assertThat(resolver.isActive(item, new CurrentLocation("/programs/3", null), null, "SPECIAL")).isFalse();
    }

    // ---------- INTERNAL_URL ----------

    @Test
    void internalUrlExactMatch() {
        HeaderMenuItem item = leaf("/foo?a=1", MenuTargetType.INTERNAL_URL);
        assertThat(resolver.isActive(item, new CurrentLocation("/foo", "a=1"), null, null)).isTrue();
    }

    @Test
    void internalUrlInactiveWhenQueryMissing() {
        HeaderMenuItem item = leaf("/foo?a=1", MenuTargetType.INTERNAL_URL);
        assertThat(resolver.isActive(item, new CurrentLocation("/foo", null), null, null)).isFalse();
    }

    @Test
    void internalUrlInactiveWhenExtraQueryParamAdded() {
        HeaderMenuItem item = leaf("/foo?a=1", MenuTargetType.INTERNAL_URL);
        assertThat(resolver.isActive(item, new CurrentLocation("/foo", "a=1&b=2"), null, null)).isFalse();
    }

    @Test
    void internalUrlInactiveOnQueryOrderDifference() {
        // exact string 정책 - "의미상 같은 파라미터 집합"이 아니라 문자열 그대로 비교한다(semantic
        // parameter 비교와 의도적으로 다른 정책).
        HeaderMenuItem item = leaf("/foo?a=1&b=2", MenuTargetType.INTERNAL_URL);
        assertThat(resolver.isActive(item, new CurrentLocation("/foo", "b=2&a=1"), null, null)).isFalse();
    }

    @Test
    void internalUrlInactiveOnPathSuffixMismatch() {
        HeaderMenuItem item = leaf("/foo?a=1", MenuTargetType.INTERNAL_URL);
        assertThat(resolver.isActive(item, new CurrentLocation("/foo/bar", "a=1"), null, null)).isFalse();
    }

    // A. targetType 구별 회귀 테스트 - href의 생김새가 BOARD_LIST/PROGRAM_LIST/PAGE와 똑같아도
    // targetType이 INTERNAL_URL이면 오직 exact 정책만 적용된다(semantic parameter 비교로 새지 않음).
    @Test
    void internalUrlLookingLikeBoardListDoesNotUseSemanticMatching() {
        HeaderMenuItem item = leaf("/boards", MenuTargetType.INTERNAL_URL);
        // boardType=NOTICE가 붙은 실제 게시판 목록 방문 - BOARD_LIST였다면 활성화됐겠지만
        // INTERNAL_URL의 href("/boards", query 없음)와 정확히 일치하지 않으므로 비활성.
        assertThat(resolver.isActive(item, new CurrentLocation("/boards", "boardType=NOTICE"), null, null))
                .isFalse();
        // href와 완전히 동일한 query 없는 "/boards"만 active.
        assertThat(resolver.isActive(item, new CurrentLocation("/boards", null), null, null)).isTrue();
    }

    @Test
    void internalUrlLookingLikeProgramListDoesNotUseSemanticMatching() {
        HeaderMenuItem item = leaf("/programs?programType=COURSE", MenuTargetType.INTERNAL_URL);
        assertThat(resolver.isActive(item, new CurrentLocation("/programs", "programType=COURSE&page=2"), null, null))
                .isFalse();
        assertThat(resolver.isActive(item, new CurrentLocation("/programs", "programType=COURSE"), null, null))
                .isTrue();
    }

    @Test
    void internalUrlLookingLikePageDoesNotUsePageNoiseIgnorePolicy() {
        HeaderMenuItem item = leaf("/pages/INTRODUCTION", MenuTargetType.INTERNAL_URL);
        // PAGE였다면 query 무시로 active였겠지만 INTERNAL_URL은 query까지 완전 일치해야 한다.
        assertThat(resolver.isActive(item, new CurrentLocation("/pages/INTRODUCTION", "utm_source=x"), null, null))
                .isFalse();
        assertThat(resolver.isActive(item, new CurrentLocation("/pages/INTRODUCTION", null), null, null)).isTrue();
    }

    // ---------- EXTERNAL_URL ----------

    @Test
    void externalUrlAlwaysInactive() {
        HeaderMenuItem item = leaf("https://example.com", MenuTargetType.EXTERNAL_URL);
        assertThat(resolver.isActive(item, new CurrentLocation("/", null), null, null)).isFalse();
    }

    // ---------- GROUP / hasActiveChild ----------

    @Test
    void groupItselfNeverActive() {
        HeaderMenuItem group = group(List.of(leaf("/pages/INTRODUCTION", MenuTargetType.PAGE)));
        assertThat(resolver.isActive(group, new CurrentLocation("/pages/INTRODUCTION", null), null, null)).isFalse();
    }

    @Test
    void hasActiveChildTrueWhenAnyChildActive() {
        HeaderMenuItem group = group(List.of(
                leaf("/pages/INTRODUCTION", MenuTargetType.PAGE),
                leaf("/pages/HISTORY", MenuTargetType.PAGE)));
        assertThat(resolver.hasActiveChild(group, new CurrentLocation("/pages/HISTORY", null), null, null)).isTrue();
    }

    @Test
    void hasActiveChildFalseWhenNoChildActive() {
        HeaderMenuItem group = group(List.of(
                leaf("/pages/INTRODUCTION", MenuTargetType.PAGE),
                leaf("/pages/HISTORY", MenuTargetType.PAGE)));
        assertThat(resolver.hasActiveChild(group, new CurrentLocation("/pages/LOCATION", null), null, null))
                .isFalse();
    }

    @Test
    void hasActiveChildFalseForNonGroupItem() {
        HeaderMenuItem item = leaf("/pages/INTRODUCTION", MenuTargetType.PAGE);
        assertThat(resolver.hasActiveChild(item, new CurrentLocation("/pages/INTRODUCTION", null), null, null))
                .isFalse();
    }
}
