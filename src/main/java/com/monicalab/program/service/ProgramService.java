package com.monicalab.program.service;

import com.monicalab.common.dto.PageResponse;
import com.monicalab.common.exception.CustomException;
import com.monicalab.common.exception.ErrorCode;
import com.monicalab.common.util.HtmlSanitizer;
import com.monicalab.program.dto.ProgramRequest;
import com.monicalab.program.dto.ProgramResponse;
import com.monicalab.program.dto.ProgramSearchCondition;
import com.monicalab.program.dto.ProgramStatusRequest;
import com.monicalab.program.dto.ProgramVisibilityRequest;
import com.monicalab.program.entity.Program;
import com.monicalab.program.entity.ProgramType;
import com.monicalab.program.entity.RecruitStatus;
import com.monicalab.program.repository.ProgramRepository;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProgramService {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of("createdAt", "title", "recruitStatus");

    private final ProgramRepository programRepository;

    @Transactional
    public ProgramResponse create(ProgramRequest request) {
        Program program = Program.builder()
                .programType(request.programType())
                .title(request.title())
                .content(HtmlSanitizer.sanitize(request.content()))
                .thumbnail(request.thumbnail())
                .attachment(request.attachment())
                .googleFormUrl(request.googleFormUrl())
                .recruitStatus(request.recruitStatus() != null ? request.recruitStatus() : RecruitStatus.OPEN)
                .isPublic(request.isPublic() != null ? request.isPublic() : false)
                .build();

        return ProgramResponse.from(programRepository.save(program));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProgramResponse> getAdminList(ProgramType programType, String keyword, Pageable pageable) {
        validateSort(pageable.getSort());
        ProgramSearchCondition condition = new ProgramSearchCondition(programType, keyword, null);
        Page<Program> page = programRepository.search(condition, pageable);
        return PageResponse.of(page, ProgramResponse::from);
    }

    @Transactional(readOnly = true)
    public ProgramResponse getAdminById(Long id) {
        return ProgramResponse.from(getEntity(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProgramResponse> getPublicList(ProgramType programType, String keyword, Pageable pageable) {
        validateSort(pageable.getSort());
        ProgramSearchCondition condition = new ProgramSearchCondition(programType, keyword, true);
        Page<Program> page = programRepository.search(condition, pageable);
        return PageResponse.of(page, ProgramResponse::from);
    }

    @Transactional(readOnly = true)
    public ProgramResponse getPublicById(Long id) {
        Program program = programRepository.findByIdAndIsPublicTrue(id)
                .orElseThrow(() -> new CustomException(ErrorCode.PROGRAM_NOT_FOUND));
        return ProgramResponse.from(program);
    }

    @Transactional
    public ProgramResponse update(Long id, ProgramRequest request) {
        Program program = getEntity(id);
        program.update(
                request.programType(),
                request.title(),
                HtmlSanitizer.sanitize(request.content()),
                request.thumbnail(),
                request.attachment(),
                request.googleFormUrl(),
                request.recruitStatus(),
                request.isPublic());
        return ProgramResponse.from(program);
    }

    @Transactional
    public ProgramResponse updateVisibility(Long id, ProgramVisibilityRequest request) {
        Program program = getEntity(id);
        program.updateVisibility(request.isPublic());
        return ProgramResponse.from(program);
    }

    @Transactional
    public ProgramResponse updateStatus(Long id, ProgramStatusRequest request) {
        Program program = getEntity(id);
        program.updateStatus(request.recruitStatus());
        return ProgramResponse.from(program);
    }

    @Transactional
    public void delete(Long id) {
        programRepository.delete(getEntity(id));
    }

    @Transactional(readOnly = true)
    public Map<RecruitStatus, Long> getRecruitStatusCounts() {
        Map<RecruitStatus, Long> counts = new EnumMap<>(RecruitStatus.class);
        for (RecruitStatus status : RecruitStatus.values()) {
            counts.put(status, programRepository.countByRecruitStatus(status));
        }
        return counts;
    }

    private Program getEntity(Long id) {
        return programRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.PROGRAM_NOT_FOUND));
    }

    private void validateSort(Sort sort) {
        boolean invalid = sort.stream().anyMatch(order -> !ALLOWED_SORT_PROPERTIES.contains(order.getProperty()));
        if (invalid) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
