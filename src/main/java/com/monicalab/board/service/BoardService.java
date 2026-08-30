package com.monicalab.board.service;

import com.monicalab.board.dto.BoardRequest;
import com.monicalab.board.dto.BoardResponse;
import com.monicalab.board.dto.BoardSearchCondition;
import com.monicalab.board.dto.BoardVisibilityRequest;
import com.monicalab.board.entity.Board;
import com.monicalab.board.entity.BoardType;
import com.monicalab.board.repository.BoardRepository;
import com.monicalab.common.dto.PageResponse;
import com.monicalab.common.exception.CustomException;
import com.monicalab.common.exception.ErrorCode;
import com.monicalab.common.util.HtmlSanitizer;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BoardService {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of("createdAt", "title");

    private final BoardRepository boardRepository;

    @Transactional
    public BoardResponse create(BoardRequest request) {
        Board board = Board.builder()
                .boardType(request.boardType())
                .title(request.title())
                .content(HtmlSanitizer.sanitize(request.content()))
                .thumbnail(request.thumbnail())
                .attachment(request.attachment())
                .isPublic(request.isPublic() != null ? request.isPublic() : false)
                .build();

        return BoardResponse.from(boardRepository.save(board));
    }

    @Transactional(readOnly = true)
    public PageResponse<BoardResponse> getAdminList(BoardType boardType, String keyword, Pageable pageable) {
        validateSort(pageable.getSort());
        BoardSearchCondition condition = new BoardSearchCondition(boardType, keyword, null);
        Page<Board> page = boardRepository.search(condition, pageable);
        return PageResponse.of(page, BoardResponse::from);
    }

    @Transactional(readOnly = true)
    public BoardResponse getAdminById(Long id) {
        return BoardResponse.from(getEntity(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<BoardResponse> getPublicList(BoardType boardType, String keyword, Pageable pageable) {
        validateSort(pageable.getSort());
        BoardSearchCondition condition = new BoardSearchCondition(boardType, keyword, true);
        Page<Board> page = boardRepository.search(condition, pageable);
        return PageResponse.of(page, BoardResponse::from);
    }

    @Transactional(readOnly = true)
    public BoardResponse getPublicById(Long id) {
        return BoardResponse.from(boardRepository.findByIdAndIsPublicTrue(id)
                .orElseThrow(() -> new CustomException(ErrorCode.BOARD_NOT_FOUND)));
    }

    @Transactional
    public BoardResponse update(Long id, BoardRequest request) {
        Board board = getEntity(id);
        board.update(
                request.boardType(),
                request.title(),
                HtmlSanitizer.sanitize(request.content()),
                request.thumbnail(),
                request.attachment(),
                request.isPublic());
        return BoardResponse.from(board);
    }

    @Transactional
    public BoardResponse updateVisibility(Long id, BoardVisibilityRequest request) {
        Board board = getEntity(id);
        board.updateVisibility(request.isPublic());
        return BoardResponse.from(board);
    }

    @Transactional
    public void delete(Long id) {
        boardRepository.delete(getEntity(id));
    }

    private Board getEntity(Long id) {
        return boardRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.BOARD_NOT_FOUND));
    }

    private void validateSort(Sort sort) {
        boolean invalid = sort.stream().anyMatch(order -> !ALLOWED_SORT_PROPERTIES.contains(order.getProperty()));
        if (invalid) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
