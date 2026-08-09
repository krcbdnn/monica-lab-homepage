package com.monicalab.board.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.monicalab.board.entity.Board;
import com.monicalab.board.entity.BoardType;
import org.junit.jupiter.api.Test;

/**
 * ERD.md/API.md 계약(boardType, title, content, thumbnail, attachment,
 * viewCount, isPublic 필드)이 Entity/DTO 매핑에 그대로 반영되는지
 * 확인하는 정적/단위 테스트(TASK.md P6-T1 DoD 기준).
 */
class BoardResponseMappingTest {

    @Test
    void fromMapsEveryEntityFieldToTheResponse() {
        Board board = Board.builder()
                .boardType(BoardType.NOTICE)
                .title("공지사항 제목")
                .content("<p>내용</p>")
                .thumbnail("/api/files/1")
                .attachment("/api/files/2")
                .viewCount(3)
                .isPublic(true)
                .build();

        BoardResponse response = BoardResponse.from(board);

        // id/createdAt/updatedAt은 BaseEntity/JPA Auditing이 영속화 시점에 채우므로
        // 영속화 이전 상태에서는 null이 그대로 매핑되는 것이 정상이다(영속화 후 검증은
        // Repository 통합 테스트에서 수행).
        assertThat(response.id()).isNull();
        assertThat(response.boardType()).isEqualTo(BoardType.NOTICE);
        assertThat(response.title()).isEqualTo("공지사항 제목");
        assertThat(response.content()).isEqualTo("<p>내용</p>");
        assertThat(response.thumbnail()).isEqualTo("/api/files/1");
        assertThat(response.attachment()).isEqualTo("/api/files/2");
        assertThat(response.viewCount()).isEqualTo(3);
        assertThat(response.isPublic()).isTrue();
    }
}
