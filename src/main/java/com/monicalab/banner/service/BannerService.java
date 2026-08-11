package com.monicalab.banner.service;

import com.monicalab.banner.dto.BannerOrderRequest;
import com.monicalab.banner.dto.BannerRequest;
import com.monicalab.banner.dto.BannerResponse;
import com.monicalab.banner.dto.BannerVisibilityRequest;
import com.monicalab.banner.entity.Banner;
import com.monicalab.banner.repository.BannerRepository;
import com.monicalab.common.exception.CustomException;
import com.monicalab.common.exception.ErrorCode;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BannerService {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of("sortOrder", "createdAt", "title");
    private static final Sort PUBLIC_SORT = Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.desc("createdAt"));

    private final BannerRepository bannerRepository;

    @Transactional
    public BannerResponse create(BannerRequest request) {
        Banner banner = Banner.builder()
                .title(request.title())
                .image(request.image())
                .linkUrl(request.linkUrl())
                .sortOrder(request.sortOrder())
                .isVisible(request.isVisible() != null ? request.isVisible() : false)
                .build();

        return BannerResponse.from(bannerRepository.save(banner));
    }

    @Transactional(readOnly = true)
    public List<BannerResponse> getAdminList(Sort sort) {
        validateSort(sort);
        return bannerRepository.findAll(sort).stream()
                .map(BannerResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public BannerResponse getAdminById(Long id) {
        return BannerResponse.from(getEntity(id));
    }

    @Transactional(readOnly = true)
    public List<BannerResponse> getPublicList() {
        return bannerRepository.findByIsVisibleTrue(PUBLIC_SORT).stream()
                .map(BannerResponse::from)
                .toList();
    }

    @Transactional
    public BannerResponse update(Long id, BannerRequest request) {
        Banner banner = getEntity(id);
        banner.update(
                request.title(),
                request.image(),
                request.linkUrl(),
                request.sortOrder(),
                request.isVisible());
        return BannerResponse.from(banner);
    }

    @Transactional
    public BannerResponse updateVisibility(Long id, BannerVisibilityRequest request) {
        Banner banner = getEntity(id);
        banner.updateVisibility(request.isVisible());
        return BannerResponse.from(banner);
    }

    @Transactional
    public BannerResponse updateOrder(Long id, BannerOrderRequest request) {
        Banner banner = getEntity(id);
        banner.updateOrder(request.sortOrder());
        return BannerResponse.from(banner);
    }

    @Transactional
    public void delete(Long id) {
        bannerRepository.delete(getEntity(id));
    }

    private Banner getEntity(Long id) {
        return bannerRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.BANNER_NOT_FOUND));
    }

    private void validateSort(Sort sort) {
        boolean invalid = sort.stream().anyMatch(order -> !ALLOWED_SORT_PROPERTIES.contains(order.getProperty()));
        if (invalid) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
