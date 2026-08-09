package com.monicalab.program.repository;

import static com.monicalab.program.entity.QProgram.program;

import com.monicalab.program.dto.ProgramSearchCondition;
import com.monicalab.program.entity.Program;
import com.monicalab.program.entity.ProgramType;
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
public class ProgramRepositoryImpl implements ProgramRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Program> search(ProgramSearchCondition condition, Pageable pageable) {
        BooleanBuilder where = new BooleanBuilder();
        where.and(programTypeEq(condition.programType()));
        where.and(keywordContains(condition.keyword()));

        List<Program> content = queryFactory.selectFrom(program)
                .where(where)
                .orderBy(orderSpecifiers(pageable.getSort()))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        return PageableExecutionUtils.getPage(content, pageable, () -> queryFactory
                .select(program.count())
                .from(program)
                .where(where)
                .fetchOne());
    }

    private BooleanBuilder programTypeEq(ProgramType programType) {
        return programType == null ? new BooleanBuilder() : new BooleanBuilder(program.programType.eq(programType));
    }

    private BooleanBuilder keywordContains(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return new BooleanBuilder();
        }
        return new BooleanBuilder(program.title.containsIgnoreCase(keyword)
                .or(program.content.containsIgnoreCase(keyword)));
    }

    private OrderSpecifier<?>[] orderSpecifiers(Sort sort) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            boolean asc = order.isAscending();
            switch (order.getProperty()) {
                case "title" -> orders.add(asc ? program.title.asc() : program.title.desc());
                case "recruitStatus" -> orders.add(asc ? program.recruitStatus.asc() : program.recruitStatus.desc());
                case "createdAt" -> orders.add(asc ? program.createdAt.asc() : program.createdAt.desc());
                default -> {
                }
            }
        }
        if (orders.isEmpty()) {
            orders.add(program.createdAt.desc());
        }
        return orders.toArray(new OrderSpecifier[0]);
    }
}
