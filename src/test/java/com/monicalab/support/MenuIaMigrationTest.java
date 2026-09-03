package com.monicalab.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MariaDBContainer;

// P13-T30D(Task C): V5가 만든 3-GROUP IA(연구소 소개/프로그램/게시판)를 V6(Board.program_type
// 컬럼)/V7(Menu.target_subvalue 컬럼)/V8(최종 IA 데이터 전환) migration이 실제로 최종 8-item 공개
// IA(HOME 정적 링크 + 연구소 소개 GROUP + 공지사항/갤러리/자료실 top-level LEAF + 수강 신청 GROUP +
// 강의 후기 GROUP + 전체메뉴 정적 트리거)로 정확히 전환하는지 검증한다.
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
        for (String version : List.of("6", "7", "8")) {
            Integer appliedCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = true",
                    Integer.class, version);
            assertThat(appliedCount).as("version %s applied successfully", version).isEqualTo(1);
        }
    }

    // 기존 V5가 만든 13행(GROUP 3 + child 10)이 하나도 삭제되지 않고, '특강 후기' 1행만 새로 추가되어
    // 최종적으로 정확히 14행이어야 한다(DELETE 미사용 + row 재사용 전략의 핵심 검증).
    @Test
    void menuTableHasExactlyFourteenRowsAfterMigration() {
        Integer totalCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM menu", Integer.class);

        assertThat(totalCount).isEqualTo(14);
    }

    // Row identity 보존 검증: DELETE+재삽입이었다면 원래 13개 행의 id가 사라지고 새로운 id로
    // 다시 생성됐을 것이다. V5는 '연구소 소개' GROUP을 항상 자신이 새로 만든 13행 중 가장 먼저
    // INSERT하므로(V5__update_menu_ia.sql 실제 순서), 그 id를 기준으로 원래 13개 행의 id 범위를
    // 특정 상수(예: "5") 없이 상대적으로 계산해 그 범위가 전부 그대로 남아있는지, 신규 행은 그
    // 범위 밖의(더 큰) id를 받았는지 확인한다.
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
                .as("original V5-created 13 ids must all still exist unmodified-in-identity")
                .isEqualTo(13);

        Integer newRowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM menu WHERE id > ?", Integer.class, originalRangeEndInclusive);
        assertThat(newRowCount).as("exactly one new row ('특강 후기') beyond the original range").isEqualTo(1);
    }

    // 사용자 요구: "SQL 파일 자체에도 DELETE가 들어가지 않는지" 확인. 순수 텍스트 검사이며 실제 SQL
    // DELETE 문 패턴(DELETE ... FROM)만 찾는다 - 이 파일의 한글 설명 주석에 "DELETE는 사용하지
    // 않는다" 같은 문구가 그대로 포함돼 있어 단순 "DELETE" 단어 포함 여부로는 오탐이 나기 때문이다.
    @Test
    void dataMigrationSqlFileContainsNoActualDeleteStatement() throws IOException {
        String sql = readClasspathResource("db/migration/V8__finalize_menu_ia.sql");

        assertThat(Pattern.compile("(?i)delete\\s+from").matcher(sql).find())
                .as("V8 데이터 migration에는 실제 DELETE FROM 문이 없어야 한다")
                .isFalse();
    }

    private String readClasspathResource(String path) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(is).as("classpath resource %s must exist", path).isNotNull();
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // 최종 공개 IA의 6개 top-level Menu row(HOME/전체메뉴는 header.html 정적 마크업이라 Menu row가
    // 아니다)가 정확한 라벨/유형/순서로 존재해야 한다.
    @Test
    void sixTopLevelItemsExistInFinalOrder() {
        List<Map<String, Object>> topLevel = jdbcTemplate.queryForList(
                "SELECT label, target_type FROM menu WHERE parent_id IS NULL ORDER BY sort_order");

        assertThat(topLevel).hasSize(6);
        assertThat(topLevel).extracting(row -> row.get("label"))
                .containsExactly("연구소 소개", "공지사항", "갤러리", "자료실", "수강 신청", "강의 후기");
        assertThat(topLevel).extracting(row -> row.get("target_type"))
                .containsExactly("GROUP", "BOARD_LIST", "BOARD_LIST", "BOARD_LIST", "GROUP", "GROUP");
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

    @Test
    void aboutGroupChildrenAreUnchanged() {
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

    @Test
    void reviewGroupChildrenHaveCorrectBoardListTargetsAndSubvalues() {
        List<Map<String, Object>> children = jdbcTemplate.queryForList(
                "SELECT label, target_type, target_value, target_subvalue FROM menu "
                        + "WHERE parent_id = (SELECT id FROM menu WHERE parent_id IS NULL "
                        + "AND label = '강의 후기' AND target_type = 'GROUP') ORDER BY sort_order");

        assertThat(children).hasSize(2);
        assertThat(children).extracting(row -> row.get("label")).containsExactly("수강 후기", "특강 후기");
        assertThat(children).extracting(row -> row.get("target_type")).containsOnly("BOARD_LIST");
        assertThat(children).extracting(row -> row.get("target_value")).containsOnly("REVIEW");
        assertThat(children).extracting(row -> row.get("target_subvalue")).containsExactly("COURSE", "SPECIAL");
    }

    @Test
    void noticeGalleryArchiveAreTopLevelBoardListLeaves() {
        List<Map<String, Object>> leaves = jdbcTemplate.queryForList(
                "SELECT label, target_value, is_visible FROM menu "
                        + "WHERE parent_id IS NULL AND target_type = 'BOARD_LIST' ORDER BY sort_order");

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

    @Test
    void allFourteenRowsAreVisibleAndNotOpenInNewTab() {
        Integer nonCompliantCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM menu WHERE is_visible = FALSE OR open_in_new_tab = TRUE",
                Integer.class);

        assertThat(nonCompliantCount).isZero();
    }
}
