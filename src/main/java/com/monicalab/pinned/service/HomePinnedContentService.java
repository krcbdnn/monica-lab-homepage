package com.monicalab.pinned.service;

import com.monicalab.board.entity.Board;
import com.monicalab.board.repository.BoardRepository;
import com.monicalab.common.exception.CustomException;
import com.monicalab.common.exception.ErrorCode;
import com.monicalab.pinned.dto.HomePinnedContentOrderRequest;
import com.monicalab.pinned.dto.HomePinnedContentRequest;
import com.monicalab.pinned.dto.HomePinnedContentResponse;
import com.monicalab.pinned.dto.HomePinnedContentVisibilityRequest;
import com.monicalab.pinned.dto.SourceStatus;
import com.monicalab.pinned.entity.HomePinnedContent;
import com.monicalab.pinned.entity.HomeTargetType;
import com.monicalab.pinned.repository.HomePinnedContentRepository;
import com.monicalab.program.entity.Program;
import com.monicalab.program.repository.ProgramRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HomePinnedContentService {

    private static final Sort SORT = Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("id"));

    private final HomePinnedContentRepository repository;
    private final BoardRepository boardRepository;
    private final ProgramRepository programRepository;

    // 생성 시점에는 PUBLIC source만 허용한다(기존 findByIdAndIsPublicTrue 재사용, 존재하지 않거나
    // 비공개면 INVALID_INPUT_VALUE 400). 검증 과정에서 이미 조회한 Board/Program을 그대로 응답 조립에
    // 재사용해 별도 재조회를 하지 않는다.
    //
    // (target_type, target_id) 중복은 2단계로 방어한다: 1) 서비스 사전 검증(existsBy), 2) DB UNIQUE
    // 제약. IDENTITY 채번 전략상 save()가 INSERT를 즉시 실행하므로, saveAndFlush에서 발생하는
    // DataIntegrityViolationException을 이 메서드 범위 안에서만 잡아 동시 요청 race condition까지
    // HOME_PINNED_CONTENT_DUPLICATE(409)로 변환한다. GlobalExceptionHandler에 범용 매핑을 추가하지
    // 않는다 - 다른 도메인의 무관한 DB 무결성 오류까지 "중복"으로 잘못 분류되는 것을 막기 위함이다.
    @Transactional
    public HomePinnedContentResponse create(HomePinnedContentRequest request) {
        Board board = null;
        Program program = null;
        if (request.targetType() == HomeTargetType.BOARD) {
            board = boardRepository.findByIdAndIsPublicTrue(request.targetId())
                    .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT_VALUE));
        } else {
            program = programRepository.findByIdAndIsPublicTrue(request.targetId())
                    .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT_VALUE));
        }

        if (repository.existsByTargetTypeAndTargetId(request.targetType(), request.targetId())) {
            throw new CustomException(ErrorCode.HOME_PINNED_CONTENT_DUPLICATE);
        }

        HomePinnedContent pin = HomePinnedContent.builder()
                .targetType(request.targetType())
                .targetId(request.targetId())
                .sortOrder(request.sortOrder())
                .isVisible(request.visible() == null ? true : request.visible())
                .build();
        try {
            pin = repository.saveAndFlush(pin);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.HOME_PINNED_CONTENT_DUPLICATE);
        }

        return board != null ? buildResponse(pin, board) : buildResponse(pin, program);
    }

    // 관리자 목록은 원본(Board/Program) 상태(PUBLIC/PRIVATE/DELETED)를 함께 보여줘야 하므로, pin
    // 전체 조회 1회 후 targetType별로 그룹핑해 Board/Program을 각각 findAllById로 배치 조회한다.
    // 핀 개수와 무관하게 항상 [pin 조회 1] + [BOARD 배치 조회 0~1] + [PROGRAM 배치 조회 0~1] 수준으로
    // 처리되어 N+1이 발생하지 않는다.
    @Transactional(readOnly = true)
    public List<HomePinnedContentResponse> getAdminList() {
        List<HomePinnedContent> pins = repository.findAll(SORT);
        if (pins.isEmpty()) {
            return List.of();
        }

        Set<Long> boardIds = pins.stream()
                .filter(pin -> pin.getTargetType() == HomeTargetType.BOARD)
                .map(HomePinnedContent::getTargetId)
                .collect(Collectors.toSet());
        Set<Long> programIds = pins.stream()
                .filter(pin -> pin.getTargetType() == HomeTargetType.PROGRAM)
                .map(HomePinnedContent::getTargetId)
                .collect(Collectors.toSet());

        Map<Long, Board> boardsById = boardIds.isEmpty() ? Map.of()
                : boardRepository.findAllById(boardIds).stream().collect(Collectors.toMap(Board::getId, Function.identity()));
        Map<Long, Program> programsById = programIds.isEmpty() ? Map.of()
                : programRepository.findAllById(programIds).stream()
                        .collect(Collectors.toMap(Program::getId, Function.identity()));

        return pins.stream()
                .map(pin -> pin.getTargetType() == HomeTargetType.BOARD
                        ? buildResponse(pin, boardsById.get(pin.getTargetId()))
                        : buildResponse(pin, programsById.get(pin.getTargetId())))
                .toList();
    }

    @Transactional
    public HomePinnedContentResponse updateOrder(Long id, HomePinnedContentOrderRequest request) {
        HomePinnedContent pin = getEntity(id);
        pin.updateOrder(request.sortOrder());
        return toResponse(pin);
    }

    @Transactional
    public HomePinnedContentResponse updateVisibility(Long id, HomePinnedContentVisibilityRequest request) {
        HomePinnedContent pin = getEntity(id);
        pin.updateVisibility(request.visible());
        return toResponse(pin);
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(getEntity(id));
    }

    private HomePinnedContent getEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.HOME_PINNED_CONTENT_NOT_FOUND));
    }

    // 단건 조회(order/visibility 변경 응답)는 목록 배치 조회와 달리 pin 1건당 원본 1건만 다시 조회하면
    // 되므로 findById 단건으로 충분하다(N+1 우려 없음 - 애초에 루프 안에서 호출되지 않음).
    private HomePinnedContentResponse toResponse(HomePinnedContent pin) {
        return pin.getTargetType() == HomeTargetType.BOARD
                ? buildResponse(pin, boardRepository.findById(pin.getTargetId()).orElse(null))
                : buildResponse(pin, programRepository.findById(pin.getTargetId()).orElse(null));
    }

    private HomePinnedContentResponse buildResponse(HomePinnedContent pin, Board board) {
        return board == null
                ? HomePinnedContentResponse.of(pin, SourceStatus.DELETED, null, null)
                : HomePinnedContentResponse.of(pin, board.isPublic() ? SourceStatus.PUBLIC : SourceStatus.PRIVATE,
                        board.getTitle(), "/admin/boards/" + pin.getTargetId() + "/edit");
    }

    private HomePinnedContentResponse buildResponse(HomePinnedContent pin, Program program) {
        return program == null
                ? HomePinnedContentResponse.of(pin, SourceStatus.DELETED, null, null)
                : HomePinnedContentResponse.of(pin, program.isPublic() ? SourceStatus.PUBLIC : SourceStatus.PRIVATE,
                        program.getTitle(), "/admin/programs/" + pin.getTargetId() + "/edit");
    }
}
