package com.monicalab.board.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.monicalab.board.dto.BoardSearchCondition;
import com.monicalab.board.entity.Board;
import com.monicalab.board.entity.BoardType;
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
                .viewCount(0)
                .isPublic(true)
                .build());

        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.GALLERY)
                .title("특별 전시")
                .content("여름 사진 갤러리")
                .viewCount(0)
                .isPublic(true)
                .build());

        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE)
                .title("겨울 방학 공지")
                .content("공지 안내")
                .viewCount(0)
                .isPublic(true)
                .build());
    }

    @Test
    void keywordOnlyMatchesTitleOrContentAcrossBoardTypes() {
        Page<Board> result = boardRepository.search(
                new BoardSearchCondition(null, "여름", null), PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(Board::getTitle)
                .containsExactlyInAnyOrder("여름 방학 공지", "특별 전시");
    }

    @Test
    void boardTypeOnlyMatchesRegardlessOfKeyword() {
        Page<Board> result = boardRepository.search(
                new BoardSearchCondition(BoardType.NOTICE, null, null), PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(Board::getTitle)
                .containsExactlyInAnyOrder("여름 방학 공지", "겨울 방학 공지");
    }

    @Test
    void keywordAndBoardTypeCombinedNarrowsToMatchingItemsOnly() {
        Page<Board> result = boardRepository.search(
                new BoardSearchCondition(BoardType.NOTICE, "여름", null), PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(Board::getTitle)
                .containsExactly("여름 방학 공지");
    }

    @Test
    void isPublicFilterExcludesPrivateBoardsRegardlessOfOtherConditions() {
        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE)
                .title("여름 비공개 공지")
                .content("비공개 안내")
                .viewCount(0)
                .isPublic(false)
                .build());

        Page<Board> result = boardRepository.search(
                new BoardSearchCondition(null, "여름", true), PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(Board::getTitle)
                .containsExactlyInAnyOrder("여름 방학 공지", "특별 전시");
    }
}
