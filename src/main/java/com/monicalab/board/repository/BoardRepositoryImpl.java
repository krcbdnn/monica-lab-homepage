package com.monicalab.board.repository;

import static com.monicalab.board.entity.QBoard.board;

import com.monicalab.board.dto.BoardSearchCondition;
import com.monicalab.board.entity.Board;
import com.monicalab.board.entity.BoardType;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;

@RequiredArgsConstructor
public class BoardRepositoryImpl implements BoardRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Board> search(BoardSearchCondition condition, Pageable pageable) {
        BooleanBuilder where = new BooleanBuilder();
        where.and(boardTypeEq(condition.boardType()));
        where.and(keywordContains(condition.keyword()));
        where.and(isPublicEq(condition.isPublic()));

        List<Board> content = queryFactory.selectFrom(board)
                .where(where)
                .orderBy(orderSpecifiers(pageable.getSort()))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        return PageableExecutionUtils.getPage(content, pageable, () -> queryFactory
                .select(board.count())
                .from(board)
                .where(where)
                .fetchOne());
    }

    private BooleanBuilder boardTypeEq(BoardType boardType) {
        return boardType == null ? new BooleanBuilder() : new BooleanBuilder(board.boardType.eq(boardType));
    }

    private BooleanBuilder keywordContains(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return new BooleanBuilder();
        }
        return new BooleanBuilder(board.title.containsIgnoreCase(keyword)
                .or(board.content.containsIgnoreCase(keyword)));
    }

    private BooleanBuilder isPublicEq(Boolean isPublic) {
        return isPublic == null ? new BooleanBuilder() : new BooleanBuilder(board.isPublic.eq(isPublic));
    }

    private OrderSpecifier<?>[] orderSpecifiers(Sort sort) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            boolean asc = order.isAscending();
            switch (order.getProperty()) {
                case "title" -> orders.add(asc ? board.title.asc() : board.title.desc());
                case "viewCount" -> orders.add(asc ? board.viewCount.asc() : board.viewCount.desc());
                case "createdAt" -> orders.add(asc ? board.createdAt.asc() : board.createdAt.desc());
                default -> {
                }
            }
        }
        if (orders.isEmpty()) {
            orders.add(board.createdAt.desc());
        }
        return orders.toArray(new OrderSpecifier[0]);
    }
}
