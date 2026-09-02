package com.monicalab.menu.service;

import com.monicalab.board.entity.BoardType;
import com.monicalab.common.exception.CustomException;
import com.monicalab.common.exception.ErrorCode;
import com.monicalab.menu.dto.MenuOrderRequest;
import com.monicalab.menu.dto.MenuRequest;
import com.monicalab.menu.dto.MenuResponse;
import com.monicalab.menu.dto.MenuVisibilityRequest;
import com.monicalab.menu.entity.Menu;
import com.monicalab.menu.entity.MenuTargetType;
import com.monicalab.menu.repository.MenuRepository;
import com.monicalab.page.entity.PageType;
import com.monicalab.program.entity.ProgramType;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MenuService {

    private static final Sort ADMIN_LIST_SORT =
            Sort.by(Sort.Order.asc("parentId"), Sort.Order.asc("sortOrder"), Sort.Order.asc("id"));

    private final MenuRepository menuRepository;

    @Transactional
    public MenuResponse create(MenuRequest request) {
        validateParentAndGroupPlacement(null, request.parentId(), request.targetType());
        String normalizedTargetValue = normalize(request.targetValue());
        validateTarget(request.targetType(), normalizedTargetValue);

        Menu menu = Menu.builder()
                .label(request.label())
                .parentId(request.parentId())
                .targetType(request.targetType())
                .targetValue(normalizedTargetValue)
                .sortOrder(request.sortOrder())
                .isVisible(request.visible() != null ? request.visible() : false)
                .openInNewTab(request.openInNewTab() != null ? request.openInNewTab() : false)
                .build();

        return MenuResponse.from(menuRepository.save(menu));
    }

    // P13-T30A: 관리자 목록은 "부모 바로 뒤에 그 자식들"이 오는 트리 순서로 응답한다. 한 번의 조회 후
    // 애플리케이션에서 인터리빙(interleave)한다 - 단일 ORDER BY로는 이 순서를 표현할 수 없다.
    // 정상 CRUD는 validateParentAndGroupPlacement가 orphan(부모가 없거나 GROUP이 아닌 부모를 가리키는
    // 행)을 만들 수 없게 막지만, 방어적으로 그런 행이 존재하더라도 응답에서 조용히 사라지지 않고
    // 끝에 그대로 포함되도록 한다(과도한 복구 로직 없이 "누락 없음"만 보장).
    @Transactional(readOnly = true)
    public List<MenuResponse> getAdminList() {
        List<Menu> all = menuRepository.findAll(ADMIN_LIST_SORT);
        Map<Long, List<Menu>> childrenByParentId = all.stream()
                .filter(menu -> menu.getParentId() != null)
                .collect(Collectors.groupingBy(Menu::getParentId));

        List<Menu> ordered = new ArrayList<>();
        Set<Long> visitedIds = new HashSet<>();
        for (Menu menu : all) {
            if (menu.getParentId() == null) {
                ordered.add(menu);
                visitedIds.add(menu.getId());
                for (Menu child : childrenByParentId.getOrDefault(menu.getId(), List.of())) {
                    ordered.add(child);
                    visitedIds.add(child.getId());
                }
            }
        }
        for (Menu menu : all) {
            if (!visitedIds.contains(menu.getId())) {
                ordered.add(menu);
            }
        }

        return ordered.stream().map(MenuResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public MenuResponse getAdminById(Long id) {
        return MenuResponse.from(getEntity(id));
    }

    @Transactional
    public MenuResponse update(Long id, MenuRequest request) {
        Menu menu = getEntity(id);
        validateParentAndGroupPlacement(id, request.parentId(), request.targetType());
        String normalizedTargetValue = normalize(request.targetValue());
        validateTarget(request.targetType(), normalizedTargetValue);
        if (request.targetType() != MenuTargetType.GROUP && menuRepository.existsByParentId(id)) {
            throw new CustomException(ErrorCode.MENU_HAS_CHILDREN);
        }

        menu.update(
                request.label(),
                request.parentId(),
                request.targetType(),
                normalizedTargetValue,
                request.sortOrder(),
                request.visible(),
                request.openInNewTab());
        return MenuResponse.from(menu);
    }

    @Transactional
    public MenuResponse updateVisibility(Long id, MenuVisibilityRequest request) {
        Menu menu = getEntity(id);
        menu.updateVisibility(request.visible());
        return MenuResponse.from(menu);
    }

    @Transactional
    public MenuResponse updateOrder(Long id, MenuOrderRequest request) {
        Menu menu = getEntity(id);
        menu.updateOrder(request.sortOrder());
        return MenuResponse.from(menu);
    }

    @Transactional
    public void delete(Long id) {
        Menu menu = getEntity(id);
        if (menuRepository.existsByParentId(id)) {
            throw new CustomException(ErrorCode.MENU_HAS_CHILDREN);
        }
        menuRepository.delete(menu);
    }

    private Menu getEntity(Long id) {
        return menuRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.MENU_NOT_FOUND));
    }

    // GROUP은 항상 최상위여야 하고(자기 자신이 GROUP이면 parentId는 반드시 null), 그 외 모든 타입은
    // parentId를 가지려면 그 parent가 실제 존재하는 GROUP이어야 한다(부모 역할은 GROUP만 가능 -
    // parent.parentId가 자동으로 null임이 보장되므로 별도 depth 검사가 필요 없다). 자기 자신을 parent로
    // 지정하는 것도 여기서 함께 막는다.
    private void validateParentAndGroupPlacement(Long selfId, Long parentId, MenuTargetType targetType) {
        if (targetType == MenuTargetType.GROUP) {
            if (parentId != null) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            }
            return;
        }
        if (parentId == null) {
            return;
        }
        if (parentId.equals(selfId)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Menu parent = menuRepository.findById(parentId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT_VALUE));
        if (parent.getTargetType() != MenuTargetType.GROUP) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateTarget(MenuTargetType targetType, String targetValue) {
        boolean blank = targetValue == null;
        switch (targetType) {
            case GROUP, HOME -> {
                if (!blank) {
                    throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
                }
            }
            case PAGE -> {
                if (blank) {
                    throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
                }
                if (!isEnumValue(targetValue, PageType.class)) {
                    throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
                }
            }
            case PROGRAM_LIST -> {
                if (!blank && !isEnumValue(targetValue, ProgramType.class)) {
                    throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
                }
            }
            case BOARD_LIST -> {
                if (!blank && !isEnumValue(targetValue, BoardType.class)) {
                    throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
                }
            }
            case INTERNAL_URL -> {
                if (blank) {
                    throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
                }
                if (!isValidInternalPath(targetValue)) {
                    throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
                }
            }
            case EXTERNAL_URL -> {
                if (blank) {
                    throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
                }
                if (!targetValue.matches("^https?://.+")) {
                    throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
                }
            }
        }
    }

    private <E extends Enum<E>> boolean isEnumValue(String value, Class<E> enumType) {
        try {
            Enum.valueOf(enumType, value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // INTERNAL_URL은 이 애플리케이션 내부의 상대 경로만 표현해야 한다. "/"로 시작해야 하고,
    // "//example.com"처럼 프로토콜 상대(scheme-relative) URL로 해석될 수 있는 값은 실제로는 외부
    // 호스트를 가리키므로 거부한다. URI로 파싱했을 때 host/scheme이 없는지까지 함께 확인해
    // 단순 문자열 접두사 검사만으로 놓칠 수 있는 변형을 한 번 더 방어한다. 새 URL validator
    // 라이브러리는 도입하지 않는다.
    //
    // 원본 backslash(\)는 java.net.URI가 Illegal character로 URISyntaxException을 던져 아래
    // try/catch에서 이미 거부되지만(실측 확인), 이는 JDK 구현 세부사항에 기대는 것이라 명시적으로도
    // 거부한다. 그와 별개로 percent-encoded backslash(%5C/%5c)는 URI 파싱을 통과하고
    // host/scheme도 모두 null이라 기존 검사만으로는 통과했다 - 브라우저(WHATWG URL 파서)가 http/https
    // 같은 "special scheme" 기준 URL을 해석할 때 원본 backslash를 forward slash와 동일하게 취급하는
    // 정규화 동작이 있어, 저장된 값이 이후 실제 backslash로 디코딩/치환되는 경로가 생기면
    // "/\evil.com" 같은 값이 protocol-relative "//evil.com"처럼 해석될 위험이 있다. INTERNAL_URL에는
    // backslash를 쓸 실사용 이유가 없으므로 원본/percent-encoded 형태 모두 명시적으로 거부한다.
    private boolean isValidInternalPath(String value) {
        if (!value.startsWith("/") || value.startsWith("//")) {
            return false;
        }
        if (value.contains("\\") || value.contains("%5C") || value.contains("%5c")) {
            return false;
        }
        try {
            URI uri = new URI(value);
            return uri.getHost() == null && uri.getScheme() == null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
