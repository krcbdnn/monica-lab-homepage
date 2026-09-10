package com.monicalab.pinned.dto;

/**
 * HomePinnedContent 응답 조립 시점에 원본(Board/Program)을 다시 조회해 계산하는 값이다.
 * DB persisted 컬럼이 아니다.
 */
public enum SourceStatus {
    PUBLIC,
    PRIVATE,
    DELETED
}
