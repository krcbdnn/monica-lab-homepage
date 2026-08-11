package com.monicalab.popup.service;

import com.monicalab.common.exception.CustomException;
import com.monicalab.common.exception.ErrorCode;
import com.monicalab.common.util.HtmlSanitizer;
import com.monicalab.popup.dto.PopupRequest;
import com.monicalab.popup.dto.PopupResponse;
import com.monicalab.popup.dto.PopupVisibilityRequest;
import com.monicalab.popup.entity.Popup;
import com.monicalab.popup.repository.PopupRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PopupService {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of("createdAt", "startDate", "endDate", "title");
    private static final Sort PUBLIC_SORT = Sort.by(Sort.Order.desc("createdAt"));

    private final PopupRepository popupRepository;

    @Transactional
    public PopupResponse create(PopupRequest request) {
        Popup popup = Popup.builder()
                .title(request.title())
                .content(HtmlSanitizer.sanitize(request.content()))
                .startDate(request.startDate())
                .endDate(request.endDate())
                .isVisible(request.isVisible() != null ? request.isVisible() : false)
                .build();

        return PopupResponse.from(popupRepository.save(popup));
    }

    @Transactional(readOnly = true)
    public List<PopupResponse> getAdminList(Sort sort) {
        validateSort(sort);
        return popupRepository.findAll(sort).stream()
                .map(PopupResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PopupResponse getAdminById(Long id) {
        return PopupResponse.from(getEntity(id));
    }

    @Transactional(readOnly = true)
    public List<PopupResponse> getPublicList() {
        return popupRepository.findVisibleAndWithinPeriod(LocalDateTime.now(), PUBLIC_SORT).stream()
                .map(PopupResponse::from)
                .toList();
    }

    @Transactional
    public PopupResponse update(Long id, PopupRequest request) {
        Popup popup = getEntity(id);
        popup.update(
                request.title(),
                HtmlSanitizer.sanitize(request.content()),
                request.startDate(),
                request.endDate(),
                request.isVisible());
        return PopupResponse.from(popup);
    }

    @Transactional
    public PopupResponse updateVisibility(Long id, PopupVisibilityRequest request) {
        Popup popup = getEntity(id);
        popup.updateVisibility(request.isVisible());
        return PopupResponse.from(popup);
    }

    @Transactional
    public void delete(Long id) {
        popupRepository.delete(getEntity(id));
    }

    private Popup getEntity(Long id) {
        return popupRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.POPUP_NOT_FOUND));
    }

    private void validateSort(Sort sort) {
        boolean invalid = sort.stream().anyMatch(order -> !ALLOWED_SORT_PROPERTIES.contains(order.getProperty()));
        if (invalid) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
