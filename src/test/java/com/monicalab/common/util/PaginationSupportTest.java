package com.monicalab.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.monicalab.common.dto.PageResponse;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class PaginationSupportTest {

    // size 10 기준 totalPages = 3(0, 1, 2)이 되도록 맞춘 값.
    private static final int TOTAL_ELEMENTS = 25;

    // 실제 리포지토리처럼 범위를 벗어난 페이지는 빈 content를 반환하도록 흉내낸다. content가 비어있지
    // 않은 채로 offset이 total을 넘는 pageable을 주면 PageImpl이 total을 offset 기준으로 재계산해버려
    // (Spring Data의 "있을 수 없는 페이지" 보정 동작) totalPages가 왜곡되므로, 반드시 이렇게 맞춰야 한다.
    private Function<Pageable, PageResponse<String>> fakeQuery() {
        return pageable -> {
            int totalPages = (int) Math.ceil((double) TOTAL_ELEMENTS / pageable.getPageSize());
            List<String> content = pageable.getPageNumber() < totalPages ? List.of("item") : List.of();
            return PageResponse.of(new PageImpl<>(content, pageable, TOTAL_ELEMENTS));
        };
    }

    @Test
    void returnsOriginalPageableResultWhenPageJumpIsAbsent() {
        Pageable pageable = PageRequest.of(2, 10);

        PageResponse<String> result = PaginationSupport.resolve(pageable, null, fakeQuery());

        assertThat(result.getPage()).isEqualTo(2);
    }

    @Test
    void returnsOriginalPageableResultWhenPageJumpIsBlank() {
        Pageable pageable = PageRequest.of(2, 10);

        PageResponse<String> result = PaginationSupport.resolve(pageable, "   ", fakeQuery());

        assertThat(result.getPage()).isEqualTo(2);
    }

    @Test
    void convertsOneBasedPageJumpToZeroBasedPage() {
        Pageable pageable = PageRequest.of(0, 10);

        PageResponse<String> result = PaginationSupport.resolve(pageable, "2", fakeQuery());

        assertThat(result.getPage()).isEqualTo(1);
    }

    @Test
    void clampsPageJumpOfOneOrLessToFirstPage() {
        Pageable pageable = PageRequest.of(0, 10);

        assertThat(PaginationSupport.resolve(pageable, "1", fakeQuery()).getPage()).isEqualTo(0);
        assertThat(PaginationSupport.resolve(pageable, "0", fakeQuery()).getPage()).isEqualTo(0);
        assertThat(PaginationSupport.resolve(pageable, "-5", fakeQuery()).getPage()).isEqualTo(0);
    }

    @Test
    void clampsIntegerMinValuePageJumpToFirstPageWithoutOverflow() {
        // Integer.MIN_VALUE - 1은 오버플로되어 양수로 wrap된다. requested <= 1 분기로 뺄셈 자체를
        // 피해야 하는 경계값이라 별도로 검증한다.
        Pageable pageable = PageRequest.of(0, 10);

        PageResponse<String> result =
                PaginationSupport.resolve(pageable, String.valueOf(Integer.MIN_VALUE), fakeQuery());

        assertThat(result.getPage()).isEqualTo(0);
    }

    @Test
    void clampsPageJumpBeyondTotalPagesToLastValidPage() {
        // TOTAL_ELEMENTS=25, size=10 -> totalPages=3(0,1,2). pageJump=999는 마지막 유효 페이지(2)로 재조회된다.
        Pageable pageable = PageRequest.of(0, 10);

        PageResponse<String> result = PaginationSupport.resolve(pageable, "999", fakeQuery());

        assertThat(result.getPage()).isEqualTo(2);
    }

    @Test
    void ignoresNonNumericPageJumpAndKeepsOriginalPageable() {
        Pageable pageable = PageRequest.of(1, 10);

        PageResponse<String> result = PaginationSupport.resolve(pageable, "abc", fakeQuery());

        assertThat(result.getPage()).isEqualTo(1);
    }

    @Test
    void ignoresDecimalPageJumpAndKeepsOriginalPageable() {
        Pageable pageable = PageRequest.of(0, 10);

        PageResponse<String> result = PaginationSupport.resolve(pageable, "1.5", fakeQuery());

        assertThat(result.getPage()).isEqualTo(0);
    }

    @Test
    void doesNotClampWhenTotalPagesIsZero() {
        Pageable pageable = PageRequest.of(0, 10);
        Function<Pageable, PageResponse<String>> emptyQuery =
                p -> PageResponse.of(new PageImpl<>(List.of(), p, 0));

        PageResponse<String> result = PaginationSupport.resolve(pageable, "5", emptyQuery);

        assertThat(result.getTotalPages()).isEqualTo(0);
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void doesNotClampPlainPageParameterRequestsThatExceedTotalPages() {
        // pageJump가 없는 순수 page= 요청은 이번 Task 범위 밖 - 기존처럼 빈 목록을 그대로 반환해야 하고,
        // PaginationSupport가 마지막 유효 페이지로 임의 clamp하면 안 된다.
        Pageable outOfRangePageable = PageRequest.of(999, 10);

        PageResponse<String> result = PaginationSupport.resolve(outOfRangePageable, null, fakeQuery());

        assertThat(result.getPage()).isEqualTo(999);
    }
}
