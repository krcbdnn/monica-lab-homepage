package com.monicalab.menu.support;

/**
 * P13-T37: 공개 헤더의 active(현재 페이지) 판정을 위해 request에서 있는 그대로 뽑아온 값 객체.
 * 파싱/판정 로직은 전혀 갖지 않는다 - {@code HeaderMenuControllerAdvice}가 요청 시점에만 알 수 있는
 * 원시 정보(경로, raw query string)를 담아 model에 공급하고, 실제 매칭은 {@link HeaderActiveResolver}
 * 하나가 전담한다. API/Service DTO가 아니라 공개 Header 렌더링만을 지원하는 view-support 객체라
 * {@code menu.dto}가 아닌 {@code menu.support}에 둔다.
 */
public record CurrentLocation(String path, String queryString) {
}
