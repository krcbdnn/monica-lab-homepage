package com.monicalab.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MariaDBContainer;

// P13-T30C: V5 migration이 실제로 정확한 13행(GROUP 3 + child 10)을 만드는지 검증한다.
// AbstractIntegrationTest의 정적 공유 컨테이너를 재사용하면 다른 테스트 클래스들(예:
// AdminMenuControllerTest, HeaderMenuControllerAdviceTest, HomeControllerTest)이 실행 순서와
// 무관하게 menuRepository.deleteAll()로 이 테이블을 자유롭게 비우기 때문에, "migration이 직접
// 만든 원본 상태"를 실행 순서에 관계없이 확인하려면 이 테스트만을 위한 별도 컨테이너/Spring
// context가 필요하다. 이 클래스는 AbstractIntegrationTest를 상속하지 않고 자체 @ServiceConnection
// 컨테이너를 선언해, 다른 어떤 테스트도 손댈 수 없는 완전히 격리된 MariaDB에 V1~V5 전체를 처음부터
// 적용한 뒤 그 결과만 JDBC로 직접 조회한다(JPA/Repository 계층을 거치지 않아 Menu 관련 다른
// Bean/서비스 로직과도 완전히 독립적이다).
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
    void flywayAppliesMenuIaMigrationSuccessfully() {
        Integer appliedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '5' AND success = true",
                Integer.class);

        assertThat(appliedCount).isEqualTo(1);
    }

    @Test
    void menuTableHasExactlyThirteenRowsAfterMigration() {
        Integer totalCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM menu", Integer.class);

        assertThat(totalCount).isEqualTo(13);
    }

    @Test
    void oldV4FlatSeedRowsNoLongerExist() {
        Integer legacyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM menu WHERE parent_id IS NULL "
                        + "AND ((label = '연구소 소개' AND target_type = 'PAGE' AND target_value = 'INTRODUCTION') "
                        + "OR (label = '프로그램' AND target_type = 'PROGRAM_LIST' AND target_value IS NULL) "
                        + "OR (label = '강의 후기' AND target_type = 'BOARD_LIST' AND target_value = 'REVIEW') "
                        + "OR (label = '게시판' AND target_type = 'BOARD_LIST' AND target_value IS NULL))",
                Integer.class);

        assertThat(legacyCount).isZero();
    }

    @Test
    void exactlyThreeTopLevelGroupsExistWithCorrectChildCounts() {
        List<Map<String, Object>> groups = jdbcTemplate.queryForList(
                "SELECT id, label, sort_order FROM menu "
                        + "WHERE parent_id IS NULL AND target_type = 'GROUP' ORDER BY sort_order");

        assertThat(groups).hasSize(3);
        assertThat(groups).extracting(row -> row.get("label"))
                .containsExactly("연구소 소개", "프로그램", "게시판");

        Map<String, Integer> expectedChildCounts = Map.of(
                "연구소 소개", 4,
                "프로그램", 2,
                "게시판", 4);

        for (Map<String, Object> group : groups) {
            String label = (String) group.get("label");
            Long groupId = ((Number) group.get("id")).longValue();
            Integer childCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM menu WHERE parent_id = ?", Integer.class, groupId);
            assertThat(childCount).as("children of %s", label).isEqualTo(expectedChildCounts.get(label));
        }
    }

    @Test
    void aboutGroupChildrenHaveCorrectPageTargetsAndOrder() {
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
    void programGroupChildrenHaveCorrectProgramListTargetsAndOrder() {
        List<Map<String, Object>> children = jdbcTemplate.queryForList(
                "SELECT label, target_type, target_value FROM menu "
                        + "WHERE parent_id = (SELECT id FROM menu WHERE parent_id IS NULL "
                        + "AND label = '프로그램' AND target_type = 'GROUP') ORDER BY sort_order");

        assertThat(children).hasSize(2);
        assertThat(children).extracting(row -> row.get("label")).containsExactly("수강 프로그램", "특강");
        assertThat(children).extracting(row -> row.get("target_type")).containsOnly("PROGRAM_LIST");
        assertThat(children).extracting(row -> row.get("target_value")).containsExactly("COURSE", "SPECIAL");
    }

    @Test
    void boardGroupChildrenHaveCorrectBoardListTargetsAndOrder() {
        List<Map<String, Object>> children = jdbcTemplate.queryForList(
                "SELECT label, target_type, target_value FROM menu "
                        + "WHERE parent_id = (SELECT id FROM menu WHERE parent_id IS NULL "
                        + "AND label = '게시판' AND target_type = 'GROUP') ORDER BY sort_order");

        assertThat(children).hasSize(4);
        assertThat(children).extracting(row -> row.get("label"))
                .containsExactly("공지사항", "갤러리", "자료실", "강의 후기");
        assertThat(children).extracting(row -> row.get("target_type")).containsOnly("BOARD_LIST");
        assertThat(children).extracting(row -> row.get("target_value"))
                .containsExactly("NOTICE", "GALLERY", "ARCHIVE", "REVIEW");
    }

    @Test
    void allThirteenRowsAreVisibleAndNotOpenInNewTab() {
        Integer nonCompliantCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM menu WHERE is_visible = FALSE OR open_in_new_tab = TRUE",
                Integer.class);

        assertThat(nonCompliantCount).isZero();
    }
}
