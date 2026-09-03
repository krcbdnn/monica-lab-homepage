package com.monicalab.board.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.monicalab.board.dto.BoardSearchCondition;
import com.monicalab.board.entity.Board;
import com.monicalab.board.entity.BoardType;
import com.monicalab.program.entity.ProgramType;
import com.monicalab.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

class BoardRepositoryCustomTest extends AbstractIntegrationTest {

    @Autowired
    private BoardRepository boardRepository;

    @BeforeEach
    void setUp() {
        boardRepository.deleteAll();

        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE)
                .title("여름 방학 공지")
                .content("공지 안내")
                .isPublic(true)
                .build());

        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.GALLERY)
                .title("특별 전시")
                .content("여름 사진 갤러리")
                .isPublic(true)
                .build());

        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE)
                .title("겨울 방학 공지")
                .content("공지 안내")
                .isPublic(true)
                .build());
    }

    @Test
    void keywordOnlyMatchesTitleOrContentAcrossBoardTypes() {
        Page<Board> result = boardRepository.search(
                new BoardSearchCondition(null, null, "여름", null), PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(Board::getTitle)
                .containsExactlyInAnyOrder("여름 방학 공지", "특별 전시");
    }

    @Test
    void boardTypeOnlyMatchesRegardlessOfKeyword() {
        Page<Board> result = boardRepository.search(
                new BoardSearchCondition(BoardType.NOTICE, null, null, null), PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(Board::getTitle)
                .containsExactlyInAnyOrder("여름 방학 공지", "겨울 방학 공지");
    }

    @Test
    void keywordAndBoardTypeCombinedNarrowsToMatchingItemsOnly() {
        Page<Board> result = boardRepository.search(
                new BoardSearchCondition(BoardType.NOTICE, null, "여름", null), PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(Board::getTitle)
                .containsExactly("여름 방학 공지");
    }

    @Test
    void isPublicFilterExcludesPrivateBoardsRegardlessOfOtherConditions() {
        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE)
                .title("여름 비공개 공지")
                .content("비공개 안내")
                .isPublic(false)
                .build());

        Page<Board> result = boardRepository.search(
                new BoardSearchCondition(null, null, "여름", true), PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(Board::getTitle)
                .containsExactlyInAnyOrder("여름 방학 공지", "특별 전시");
    }

    // P13-T30D(Task C): programType 필터가 REVIEW 게시글을 COURSE/SPECIAL로 정확히 분리하고,
    // programType이 null이면(전체 조회) subtype과 무관하게 전부 포함해야 한다.
    @Test
    void programTypeFilterNarrowsReviewBoardsToMatchingSubtypeOnly() {
        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.REVIEW).title("수강 후기 A").isPublic(true).programType(ProgramType.COURSE)
                .build());
        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.REVIEW).title("특강 후기 A").isPublic(true).programType(ProgramType.SPECIAL)
                .build());
        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.REVIEW).title("미지정 후기 A").isPublic(true).programType(null)
                .build());

        Page<Board> courseOnly = boardRepository.search(
                new BoardSearchCondition(BoardType.REVIEW, ProgramType.COURSE, null, null), PageRequest.of(0, 20));
        assertThat(courseOnly.getContent()).extracting(Board::getTitle).containsExactly("수강 후기 A");

        Page<Board> specialOnly = boardRepository.search(
                new BoardSearchCondition(BoardType.REVIEW, ProgramType.SPECIAL, null, null), PageRequest.of(0, 20));
        assertThat(specialOnly.getContent()).extracting(Board::getTitle).containsExactly("특강 후기 A");

        Page<Board> allReviews = boardRepository.search(
                new BoardSearchCondition(BoardType.REVIEW, null, null, null), PageRequest.of(0, 20));
        assertThat(allReviews.getContent()).extracting(Board::getTitle)
                .containsExactlyInAnyOrder("수강 후기 A", "특강 후기 A", "미지정 후기 A");
    }
}
