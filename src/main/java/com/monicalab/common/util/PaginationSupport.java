package com.monicalab.common.util;

import com.monicalab.common.dto.PageResponse;
import java.util.function.Function;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

public final class PaginationSupport {

    private PaginationSupport() {
    }

    // pageJump가 없으면(직접 이동 폼을 쓰지 않은 일반 page= 파라미터 요청) 이 메서드는 아무것도
    // 바꾸지 않고 그대로 조회 결과를 반환한다 - 기존 ?page=999 같은 요청이 "마지막 유효 페이지로
    // clamp"되는 동작으로 범위가 넓어지면 안 되기 때문이다(P13-T14 범위는 pageJump 처리에만 한정).
    // pageJump가 있을 때만: 1-based 입력을 0-based로 변환하고, 조회 결과 totalPages를 벗어나면
    // 마지막 유효 페이지로 1회 재조회한다(빈 목록으로 보내지 않기 위함).
    public static <T> PageResponse<T> resolve(
            Pageable pageable, String pageJump, Function<Pageable, PageResponse<T>> query) {
        if (pageJump == null || pageJump.isBlank()) {
            return query.apply(pageable);
        }

        Pageable target = applyJump(pageable, pageJump);
        PageResponse<T> result = query.apply(target);
        if (result.getTotalPages() > 0 && target.getPageNumber() >= result.getTotalPages()) {
            Pageable lastPage = PageRequest.of(result.getTotalPages() - 1, target.getPageSize(), target.getSort());
            return query.apply(lastPage);
        }
        return result;
    }

    private static Pageable applyJump(Pageable pageable, String pageJump) {
        try {
            // Integer.MIN_VALUE - 1은 오버플로되어 양수로 wrap되므로, 뺄셈 전에 분기해 회피한다.
            int requested = Integer.parseInt(pageJump.trim());
            int zeroBased = requested <= 1 ? 0 : requested - 1;
            return PageRequest.of(zeroBased, pageable.getPageSize(), pageable.getSort());
        } catch (NumberFormatException e) {
            return pageable;
        }
    }
}
