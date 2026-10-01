package com.monicalab.board;

import static org.assertj.core.api.Assertions.assertThat;

import com.monicalab.board.dto.BoardRequest;
import com.monicalab.board.dto.BoardResponse;
import com.monicalab.board.entity.Board;
import com.monicalab.board.entity.BoardType;
import com.monicalab.board.repository.BoardRepository;
import com.monicalab.board.service.BoardService;
import com.monicalab.program.entity.ProgramType;
import com.monicalab.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.springframework.beans.factory.annotation.Autowired;

class BoardIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BoardRepository boardRepository;

    @Autowired
    private BoardService boardService;

    @ParameterizedTest
    @EnumSource(BoardType.class)
    void everyBoardTypeRoundTripsThroughRepository(BoardType boardType) {
        Board saved = boardRepository.saveAndFlush(Board.builder()
                .boardType(boardType)
                .title(boardType + " 게시글")
                .isPublic(true)
                .build());

        Board found = boardRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getBoardType()).isEqualTo(boardType);
        assertThat(found.isPublic()).isTrue();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void createAppliesPostDefaultsWhenIsPublicIsOmitted() {
        BoardRequest request = new BoardRequest(BoardType.NOTICE, "공지사항", null, null, null, null, null);

        BoardResponse response = boardService.create(request);

        assertThat(response.isPublic()).isFalse();

        Board persisted = boardRepository.findById(response.id()).orElseThrow();
        assertThat(persisted.isPublic()).isFalse();
    }

    @Test
    void createHonorsExplicitIsPublicWhenProvided() {
        BoardRequest request = new BoardRequest(BoardType.ARCHIVE, "공개 자료", null, null, null, true, null);

        BoardResponse response = boardService.create(request);

        assertThat(response.isPublic()).isTrue();
    }

    // P13-T30D(Task C): REVIEW는 programType(COURSE/SPECIAL/NULL) 전부 저장/조회가 정상 round-trip
    // 되어야 한다(NULL은 기존 legacy REVIEW 게시글과의 호환을 위해 계속 허용).
    @ParameterizedTest
    @EnumSource(ProgramType.class)
    @NullSource
    void reviewProgramTypeRoundTripsThroughRepository(ProgramType programType) {
        Board saved = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.REVIEW)
                .title("강의 후기")
                .isPublic(true)
                .programType(programType)
                .build());

        Board found = boardRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getProgramType()).isEqualTo(programType);
    }
}
