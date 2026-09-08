package com.monicalab.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MariaDBContainer;

// P13-T30D(Task C): V5가 만든 3-GROUP IA(연구소 소개/프로그램/게시판)를 V6(Board.program_type
// 컬럼)/V7(Menu.target_subvalue 컬럼)/V8(최종 IA 데이터 전환)/V9(top-level 순서 재배치 + 인사말
// 비노출) migration이 실제로 최종 8-item 공개 IA(HOME 정적 링크 + 연구소 소개/수강 신청/강의 후기
// GROUP + 공지사항/갤러리/자료실 top-level LEAF + 전체메뉴 정적 트리거)로 정확히 전환하는지 검증한다.
//
// P13-T33: 발주처 요구사항 재확인 결과 "강의 후기"는 GROUP(수강 후기/특강 후기 child 2개)이 아니라
// 하나의 게시판(BOARD_LIST/REVIEW) top-level LEAF여야 한다는 것이 확인되어, V10이 이를 되돌린다.
// 이 클래스는 V1~최신(V10 포함) 전체를 순서대로 적용하므로, 아래 테스트들은 "V8/V9가 만든 상태"가
// 아니라 "V10까지 전부 적용된 이후의 최종 상태"를 검증한다 - V8/V9 자체의 무결성(DELETE 미사용,
// row 재사용)을 확인하는 테스트는 그대로 유지하되, REVIEW 관련 최종 개수/구조만 V10 반영 값으로
// 갱신했다.
//
// AbstractIntegrationTest의 정적 공유 컨테이너를 재사용하면 다른 테스트 클래스들이 menuRepository.
// deleteAll()로 이 테이블을 자유롭게 비우기 때문에, "migration이 직접 만든 원본 상태"를 실행 순서와
// 무관하게 확인하려면 이 테스트만을 위한 별도 컨테이너/Spring context가 필요하다. 이 클래스는
// AbstractIntegrationTest를 상속하지 않고 자체 @ServiceConnection 컨테이너를 선언해, 다른 어떤
// 테스트도 손댈 수 없는 완전히 격리된 MariaDB에 V1~최신 전체를 처음부터 적용한 뒤 그 결과만 JDBC로
// 직접 조회한다(JPA/Repository 계층을 거치지 않아 Menu 관련 다른 Bean/서비스 로직과도 완전히 독립적).
@ActiveProfiles("test")
@SpringBootTest
class MenuIaMigrationTest {

    @ServiceConnection
    static final MariaDBContainer<?> MARIADB_CONTAINER = new MariaDBContainer<>("mariadb:11.4.12");

    static {
        MARIADB_CONTAINER.start();
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayAppliesAllTaskCMigrationsSuccessfully() {
        for (String version : List.of("6", "7", "8", "9")) {
            Integer appliedCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = true",
                    Integer.class, version);
            assertThat(appliedCount).as("version %s applied successfully", version).isEqualTo(1);
        }
    }

    // P13-T33: V9까지 적용된 상태(다른 모든 테스트가 이미 전제하는 baseline)에 이어 V10도 정상
    // 적용되는지 확인한다 - 이 클래스가 매번 V1부터 최신까지 순서대로 전부 적용하므로, 이 단언이
    // 통과한다는 것 자체가 "V9 → V10 forward migration"이 성공했다는 증거다.
    @Test
    void flywayAppliesP13T33MigrationSuccessfully() {
        Integer appliedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '10' AND success = true",
                Integer.class);
        assertThat(appliedCount).as("version 10 applied successfully").isEqualTo(1);
    }

    // 기존 V5가 만든 13행(GROUP 3 + child 10)이 하나도 삭제되지 않고, '특강 후기' 1행만 새로 추가되어
    // V9까지는 정확히 14행이었다(DELETE 미사용 + row 재사용 전략의 핵심 검증). P13-T33(V10)이
    // '수강 후기'/'특강 후기' 자식 2행을 정밀 삭제하므로 최종(V10 이후) 행 수는 12행이다.
    @Test
    void menuTableHasExactlyTwelveRowsAfterAllMigrations() {
        Integer totalCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM menu", Integer.class);

        assertThat(totalCount).isEqualTo(12);
    }

    // Row identity 보존 검증: DELETE+재삽입이었다면 원래 13개 행의 id가 사라지고 새로운 id로
    // 다시 생성됐을 것이다. V5는 '연구소 소개' GROUP을 항상 자신이 새로 만든 13행 중 가장 먼저
    // INSERT하므로(V5__update_menu_ia.sql 실제 순서), 그 id를 기준으로 원래 13개 행의 id 범위를
    // 특정 상수(예: "5") 없이 상대적으로 계산한다.
    //
    // P13-T33(V10)이 이 범위 안의 1개('수강 후기', V5가 만든 13번째 행)와 범위 밖의 1개('특강 후기',
    // V8이 새로 추가한 행)를 정밀 삭제하므로, 이 테스트가 검증하는 "V8까지는 DELETE+재삽입이 아니라
    // row 재사용이었다"는 사실 자체는 그대로 유효하되(남은 12개 id 전부가 여전히 원래 식별자를
    // 유지), 최종 카운트만 V10 삭제분만큼 줄어든다.
    @Test
    void originalThirteenV5RowsAreReusedNotDeletedAndRecreated() {
        Long aboutGroupId = jdbcTemplate.queryForObject(
                "SELECT id FROM menu WHERE label = '연구소 소개' AND target_type = 'GROUP' AND parent_id IS NULL",
                Long.class);
        long originalRangeStart = aboutGroupId;
        long originalRangeEndInclusive = aboutGroupId + 12;

        Integer originalRangeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM menu WHERE id BETWEEN ? AND ?",
                Integer.class, originalRangeStart, originalRangeEndInclusive);
        assertThat(originalRangeCount)
                .as("original V5-created 13 ids minus the one('수강 후기') P13-T33 deletes must still exist")
                .isEqualTo(12);

        Integer newRowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM menu WHERE id > ?", Integer.class, originalRangeEndInclusive);
        assertThat(newRowCount)
                .as("the one new row V8 added('특강 후기') is exactly the row P13-T33 deletes, so none remain")
                .isEqualTo(0);
    }

    // 사용자 요구: "SQL 파일 자체에도 DELETE가 들어가지 않는지" 확인. 순수 텍스트 검사이며 실제 SQL
    // DELETE 문 패턴(DELETE ... FROM)만 찾는다 - 이 파일의 한글 설명 주석에 "DELETE는 사용하지
    // 않는다" 같은 문구가 그대로 포함돼 있어 단순 "DELETE" 단어 포함 여부로는 오탐이 나기 때문이다.
    @Test
    void dataMigrationSqlFileContainsNoActualDeleteStatement() throws IOException {
        String v8Sql = readClasspathResource("db/migration/V8__finalize_menu_ia.sql");
        assertThat(Pattern.compile("(?i)delete\\s+from").matcher(v8Sql).find())
                .as("V8 데이터 migration에는 실제 DELETE FROM 문이 없어야 한다")
                .isFalse();

        String v9Sql = readClasspathResource(
                "db/migration/V9__adjust_menu_ia_home_order_and_greeting_visibility.sql");
        assertThat(Pattern.compile("(?i)delete\\s+from").matcher(v9Sql).find())
                .as("V9 미세조정 migration에도 실제 DELETE FROM 문이 없어야 한다")
                .isFalse();
    }

    // P13-T33(V10)은 V8/V9와 달리 의도적으로 DELETE를 사용한다(승인된 설계). 대신 "광범위한 DELETE"가
    // 아니라 "정확히 2개의, target_subvalue까지 전부 일치하는 정밀 DELETE"만 있어야 한다는 안전
    // 계약을 텍스트 검사로 고정한다 - 예를 들어 `DELETE ... WHERE parent_id = @review_menu_id`처럼
    // target_subvalue 조건 없이 광범위하게 지우는 문장이 실수로 섞여 들어가면 이 테스트가 잡아낸다.
    @Test
    void v10MigrationSqlFileDeletesOnlyExactReviewSubtypeChildren() throws IOException {
        String v10Sql = readClasspathResource("db/migration/V10__collapse_review_menu_to_single_leaf.sql");

        Matcher deleteMatcher = Pattern.compile("(?i)delete\\s+from\\s+menu[\\s\\S]*?;").matcher(v10Sql);
        List<String> deleteStatements = new ArrayList<>();
        while (deleteMatcher.find()) {
            deleteStatements.add(deleteMatcher.group());
        }

        assertThat(deleteStatements).as("V10은 정확히 2개의 DELETE 문(COURSE 자식 1개, SPECIAL 자식 1개)만 가져야 한다")
                .hasSize(2);
        assertThat(deleteStatements.get(0))
                .as("첫 DELETE는 parent_id/target_type/target_value/target_subvalue=COURSE 조합까지 전부 포함해야 한다")
                .containsIgnoringCase("parent_id")
                .containsIgnoringCase("target_type")
                .containsIgnoringCase("target_value")
                .contains("target_subvalue = 'COURSE'");
        assertThat(deleteStatements.get(1))
                .as("두 번째 DELETE는 parent_id/target_type/target_value/target_subvalue=SPECIAL 조합까지 전부 포함해야 한다")
                .containsIgnoringCase("parent_id")
                .containsIgnoringCase("target_type")
                .containsIgnoringCase("target_value")
                .contains("target_subvalue = 'SPECIAL'");
    }

    private String readClasspathResource(String path) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(is).as("classpath resource %s must exist", path).isNotNull();
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // 최종 공개 IA의 6개 top-level Menu row(HOME/전체메뉴는 header.html 정적 마크업이라 Menu row가
    // 아니다)가 정확한 라벨/유형/순서로 존재해야 한다.
    //
    // P13-T33(V10) 반영: "강의 후기"는 더 이상 dropdown GROUP이 아니라 BOARD_LIST top-level LEAF다
    // (V10이 target_type만 바꾸고 sort_order는 건드리지 않으므로 라벨 순서 자체는 V9 그대로 유지).
    // 순서: dropdown이 있는 GROUP 2개(연구소 소개/수강 신청) 먼저, 그 다음 dropdown 없는 top-level
    // LEAF 4개(강의 후기/공지사항/갤러리/자료실).
    @Test
    void sixTopLevelItemsExistInFinalOrder() {
        List<Map<String, Object>> topLevel = jdbcTemplate.queryForList(
                "SELECT label, target_type FROM menu WHERE parent_id IS NULL ORDER BY sort_order");

        assertThat(topLevel).hasSize(6);
        assertThat(topLevel).extracting(row -> row.get("label"))
                .containsExactly("연구소 소개", "수강 신청", "강의 후기", "공지사항", "갤러리", "자료실");
        assertThat(topLevel).extracting(row -> row.get("target_type"))
                .containsExactly("GROUP", "GROUP", "BOARD_LIST", "BOARD_LIST", "BOARD_LIST", "BOARD_LIST");
    }

    // 예전 3-GROUP 구조(top-level '프로그램'/'게시판' GROUP)가 더 이상 존재하지 않아야 한다 -
    // 두 GROUP 모두 재사용(label만 변경)됐으므로 이 라벨 자체가 top-level에 남아있으면 안 된다.
    @Test
    void legacyProgramAndBoardTopLevelGroupsNoLongerExist() {
        Integer legacyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM menu WHERE parent_id IS NULL AND target_type = 'GROUP' "
                        + "AND label IN ('프로그램', '게시판')",
                Integer.class);

        assertThat(legacyCount).isZero();
    }

    // V9: '인사말'은 삭제되지 않고 그대로 row로 남아있다(is_visible만 false) - 전체 4개 자식은
    // 여전히 존재해야 한다(순서/target 자체는 V8 이전과 동일, V9이 건드리는 것은 visibility뿐).
    @Test
    void aboutGroupStillHasAllFourChildrenIncludingHiddenGreeting() {
        List<Map<String, Object>> children = jdbcTemplate.queryForList(
                "SELECT label, target_type, target_value FROM menu "
                        + "WHERE parent_id = (SELECT id FROM menu WHERE parent_id IS NULL "
                        + "AND label = '연구소 소개' AND target_type = 'GROUP') ORDER BY sort_order");

        assertThat(children).hasSize(4);
        assertThat(children).extracting(row -> row.get("label"))
                .containsExactly("인사말", "연구소 소개", "연혁", "오시는 길");
        assertThat(children).extracting(row -> row.get("target_type")).containsOnly("PAGE");
        assertThat(children).extracting(row -> row.get("target_value"))
                .containsExactly("GREETING", "INTRODUCTION", "HISTORY", "LOCATION");
    }

    // V9의 핵심 변경: 공개 navigation에 실제로 노출되는(visible) 연구소 소개 자식은 정확히 3개
    // (인사말 제외)여야 한다.
    @Test
    void aboutGroupVisibleChildrenExcludeGreeting() {
        List<Map<String, Object>> visibleChildren = jdbcTemplate.queryForList(
                "SELECT label FROM menu "
                        + "WHERE parent_id = (SELECT id FROM menu WHERE parent_id IS NULL "
                        + "AND label = '연구소 소개' AND target_type = 'GROUP') "
                        + "AND is_visible = TRUE ORDER BY sort_order");

        assertThat(visibleChildren).hasSize(3);
        assertThat(visibleChildren).extracting(row -> row.get("label"))
                .containsExactly("연구소 소개", "연혁", "오시는 길");
    }

    // '인사말' row identity 보존: 삭제/재삽입이 아니라 UPDATE(is_visible만 변경)임을 확인한다.
    // target_value(GREETING)가 그대로라는 것 자체가 기존 CmsPage(PageType.GREETING) 연결이
    // 끊어지지 않았다는 증거다.
    @Test
    void greetingMenuRowIsHiddenNotDeletedAndKeepsItsPageTypeLink() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT target_value, is_visible FROM menu WHERE label = '인사말' AND target_type = 'PAGE'");

        assertThat(rows).as("인사말 row는 삭제되지 않고 정확히 1개 존재해야 한다").hasSize(1);
        assertThat(rows.get(0).get("target_value")).isEqualTo("GREETING");
        assertThat(rows.get(0).get("is_visible")).isEqualTo(false);
    }

    @Test
    void courseApplicationGroupChildrenHaveCorrectProgramListTargets() {
        List<Map<String, Object>> children = jdbcTemplate.queryForList(
                "SELECT label, target_type, target_value FROM menu "
                        + "WHERE parent_id = (SELECT id FROM menu WHERE parent_id IS NULL "
                        + "AND label = '수강 신청' AND target_type = 'GROUP') ORDER BY sort_order");

        assertThat(children).hasSize(2);
        assertThat(children).extracting(row -> row.get("label")).containsExactly("수강 신청", "특강 신청");
        assertThat(children).extracting(row -> row.get("target_type")).containsOnly("PROGRAM_LIST");
        assertThat(children).extracting(row -> row.get("target_value")).containsExactly("COURSE", "SPECIAL");
    }

    // P13-T33(V10): "강의 후기"는 더 이상 GROUP이 아니므로(그 자체가 하나의 게시판) 이 이름의 GROUP은
    // 존재하지 않아야 하고, 대신 BOARD_LIST/REVIEW top-level LEAF 1개로 존재하며 target_subvalue는
    // 항상 NULL이어야 한다(수강 후기/특강 후기는 이제 Menu가 아니라 home/board/list.html의 게시판
    // 내부 필터 nav에서만 라벨로 쓰인다 - Board.programType 자체는 무변경, Menu 레이어의 이야기다).
    @Test
    void reviewMenuIsNowASingleTopLevelLeafWithNoSubtypeChildren() {
        Integer legacyReviewGroupCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM menu WHERE label = '강의 후기' AND target_type = 'GROUP'",
                Integer.class);
        assertThat(legacyReviewGroupCount).as("'강의 후기' GROUP은 더 이상 존재하지 않아야 한다").isZero();

        List<Map<String, Object>> reviewLeaf = jdbcTemplate.queryForList(
                "SELECT id, sort_order, target_subvalue FROM menu "
                        + "WHERE label = '강의 후기' AND target_type = 'BOARD_LIST' "
                        + "AND target_value = 'REVIEW' AND parent_id IS NULL");
        assertThat(reviewLeaf).as("'강의 후기'는 정확히 1개의 top-level LEAF여야 한다").hasSize(1);
        assertThat(reviewLeaf.get(0).get("target_subvalue")).as("target_subvalue는 NULL이어야 한다").isNull();
        assertThat(reviewLeaf.get(0).get("sort_order")).as("V9이 배치한 sort_order(2)가 V10 이후에도 유지돼야 한다")
                .isEqualTo(2);

        Long reviewMenuId = ((Number) reviewLeaf.get(0).get("id")).longValue();
        Integer childCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM menu WHERE parent_id = ?", Integer.class, reviewMenuId);
        assertThat(childCount).as("'강의 후기'는 더 이상 자식을 가지면 안 된다(수강 후기/특강 후기 Menu 삭제됨)")
                .isZero();

        Integer subvalueRowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM menu WHERE target_subvalue IS NOT NULL", Integer.class);
        assertThat(subvalueRowCount).as("target_subvalue가 값을 가지는 행이 더 이상 하나도 없어야 한다").isZero();
    }

    // 공지사항/갤러리/자료실만을 대상으로 한 검증 - "강의 후기"도 P13-T33 이후 top-level BOARD_LIST가
    // 되므로 target_value를 명시적으로 좁혀 이 세 항목만의 계약(개수 3/라벨/순서)이 계속 유효한지
    // 별도로 확인한다("강의 후기" 자체의 계약은 위 reviewMenuIsNowASingleTopLevelLeafWithNoSubtypeChildren
    // 에서 이미 검증).
    @Test
    void noticeGalleryArchiveAreTopLevelBoardListLeaves() {
        List<Map<String, Object>> leaves = jdbcTemplate.queryForList(
                "SELECT label, target_value, is_visible FROM menu "
                        + "WHERE parent_id IS NULL AND target_type = 'BOARD_LIST' "
                        + "AND target_value IN ('NOTICE', 'GALLERY', 'ARCHIVE') ORDER BY sort_order");

        assertThat(leaves).hasSize(3);
        assertThat(leaves).extracting(row -> row.get("label")).containsExactly("공지사항", "갤러리", "자료실");
        assertThat(leaves).extracting(row -> row.get("target_value")).containsExactly("NOTICE", "GALLERY", "ARCHIVE");
    }

    // 사용자 요구: 자료실(ARCHIVE)은 절대 숨기지 않는다 - 이전 초안(V5의 게시판 GROUP 자식일 때와
    // 동일하게) is_visible=TRUE를 그대로 유지한 채 top-level로 승격돼야 한다.
    @Test
    void archiveRemainsVisibleAfterPromotionToTopLevel() {
        Boolean archiveVisible = jdbcTemplate.queryForObject(
                "SELECT is_visible FROM menu WHERE parent_id IS NULL AND target_type = 'BOARD_LIST' "
                        + "AND target_value = 'ARCHIVE'",
                Boolean.class);

        assertThat(archiveVisible).isTrue();
    }

    // V9 이후에는 '인사말' 1개만 의도적으로 is_visible=false다 - 그 행을 제외한 나머지(V10 이후
    // 총 12행 중 11행)는 전부 여전히 visible이고 open_in_new_tab도 전부 false여야 한다.
    @Test
    void allRowsExceptIntentionallyHiddenGreetingAreVisibleAndNotOpenInNewTab() {
        Integer nonCompliantCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM menu WHERE (is_visible = FALSE OR open_in_new_tab = TRUE) "
                        + "AND NOT (label = '인사말' AND target_type = 'PAGE' AND target_value = 'GREETING')",
                Integer.class);

        assertThat(nonCompliantCount).isZero();
    }
}
