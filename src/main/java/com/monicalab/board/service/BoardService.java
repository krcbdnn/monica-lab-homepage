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
import com.monicalab.program.entity.ProgramType;
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
        validateProgramType(request.boardType(), request.programType());
        Board board = Board.builder()
                .boardType(request.boardType())
                .title(request.title())
                .content(HtmlSanitizer.sanitize(request.content()))
                .thumbnail(request.thumbnail())
                .attachment(request.attachment())
                .isPublic(request.isPublic() != null ? request.isPublic() : false)
                .programType(request.programType())
                .build();

        return BoardResponse.from(boardRepository.save(board));
    }

    @Transactional(readOnly = true)
    public PageResponse<BoardResponse> getAdminList(BoardType boardType, ProgramType programType, String keyword,
            Pageable pageable) {
        validateSort(pageable.getSort());
        BoardSearchCondition condition = new BoardSearchCondition(boardType, effectiveProgramType(boardType, programType),
                keyword, null);
        Page<Board> page = boardRepository.search(condition, pageable);
        return PageResponse.of(page, BoardResponse::from);
    }

    @Transactional(readOnly = true)
    public BoardResponse getAdminById(Long id) {
        return BoardResponse.from(getEntity(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<BoardResponse> getPublicList(BoardType boardType, ProgramType programType, String keyword,
            Pageable pageable) {
        validateSort(pageable.getSort());
        BoardSearchCondition condition = new BoardSearchCondition(boardType, effectiveProgramType(boardType, programType),
                keyword, true);
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
        validateProgramType(request.boardType(), request.programType());
        Board board = getEntity(id);
        board.update(
                request.boardType(),
                request.title(),
                HtmlSanitizer.sanitize(request.content()),
                request.thumbnail(),
                request.attachment(),
                request.isPublic(),
                request.programType());
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

    // P13-T30D(Task C): boardType=REVIEW일 때만 programType이 의미를 갖는다(COURSE/SPECIAL/NULL 전부
    // 허용). REVIEW가 아닌 boardType(NOTICE/GALLERY/ARCHIVE)에서 programType이 NULL이 아니면 저장을
    // 거부한다 - 별도 ErrorCode 없이 기존 400 계약(INVALID_INPUT_VALUE)을 그대로 재사용한다.
    private void validateProgramType(BoardType boardType, ProgramType programType) {
        if (boardType != BoardType.REVIEW && programType != null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    // 공개/관리자 목록 조회에서는 저장 시점 검증과 달리 400으로 거부하지 않는다. boardType=REVIEW가
    // 아닌데 programType 쿼리 파라미터가 함께 들어와도(예: 잘못 만들어진 URL이나 필터 전환 잔여
    // 파라미터) 검색 조건에서 조용히 무시한다 - stale programType이 NOTICE/GALLERY/ARCHIVE 결과를
    // 오염시키지 않도록 하는 정규화다.
    private ProgramType effectiveProgramType(BoardType boardType, ProgramType programType) {
        return boardType == BoardType.REVIEW ? programType : null;
    }
}
