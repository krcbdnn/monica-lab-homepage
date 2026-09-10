package com.monicalab.menu.support;

import com.monicalab.menu.dto.HeaderMenuItem;
import com.monicalab.menu.entity.MenuTargetType;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * P13-T37: 공개 Header의 "현재 페이지" active 판정을 전담하는 stateless view-support 컴포넌트.
 * Menu Entity/Repository/Service를 전혀 호출하지 않고(추가 DB 조회 없음), {@link HeaderMenuItem}이
 * 이미 계산해 둔 {@code href}/{@code targetType}과 요청 시점 정보({@link CurrentLocation}), 그리고
 * Board/Program 상세 페이지가 이미 조회해 둔 실제 타입(선택적 override 인자)만으로 판정한다.
 *
 * <p>{@code header.html}은 이 클래스의 메서드 호출 결과(boolean)만 소비하며, URL/query 파싱이나
 * targetType별 분기는 템플릿에 작성하지 않는다.
 *
 * <p>전체 query string equality가 아니라 "타입을 결정하는 semantic parameter"만 비교한다 - BOARD_LIST는
 * boardType(+REVIEW일 때 programType), PROGRAM_LIST는 programType만 보고, {@code page}/{@code size}/
 * {@code keyword}/{@code pageJump} 같은 목록 상태 값은 비교 대상에 아예 포함하지 않는다.
 */
@Component
public class HeaderActiveResolver {

    private static final Pattern BOARD_DETAIL_PATH = Pattern.compile("^/boards/\\d+$");
    private static final Pattern PROGRAM_DETAIL_PATH = Pattern.compile("^/programs/\\d+$");

    /**
     * item이 현재 위치와 정확히 같은 목적지를 가리키는지 판정한다. GROUP은 그 자체로는 목적지가
     * 없으므로 항상 false이며(부모 GROUP 강조는 {@link #hasActiveChild}가 별도로 판정한다).
     *
     * @param detailBoardType   Board 상세 페이지에서 이미 조회된 {@code board.boardType()}(문자열,
     *                          enum name). 상세 페이지가 아니면 null.
     * @param detailProgramType Program 상세 또는 Board(REVIEW) 상세에서 이미 조회된
     *                          {@code program.programType()}/{@code board.programType()}. 없으면 null.
     */
    public boolean isActive(HeaderMenuItem item, CurrentLocation location,
            String detailBoardType, String detailProgramType) {
        if (item.href() == null) {
            return false;
        }
        MenuTargetType targetType = item.targetType();
        if (targetType == null) {
            return false;
        }

        String hrefPath = pathOf(item.href());
        String hrefQuery = queryOf(item.href());

        return switch (targetType) {
            case GROUP -> false;
            case EXTERNAL_URL -> false;
            case HOME -> "/".equals(location.path());
            case PAGE -> hrefPath.equals(location.path());
            case BOARD_LIST -> matchesBoardList(hrefQuery, location, detailBoardType, detailProgramType);
            case PROGRAM_LIST -> matchesProgramList(hrefQuery, location, detailProgramType);
            case INTERNAL_URL -> hrefPath.equals(location.path()) && Objects.equals(hrefQuery, location.queryString());
        };
    }

    /** GROUP 자신은 목적지가 없으므로 별도로 판정한다 - children 중 하나라도 active면 true. */
    public boolean hasActiveChild(HeaderMenuItem item, CurrentLocation location,
            String detailBoardType, String detailProgramType) {
        if (item.targetType() != MenuTargetType.GROUP) {
            return false;
        }
        return item.children().stream()
                .anyMatch(child -> isActive(child, location, detailBoardType, detailProgramType));
    }

    /**
     * HOME("/")은 header.html에 정적으로 하드코딩된 링크라 대응하는 {@link HeaderMenuItem}이 없다
     * (Menu DB와 무관). 다른 판정과 동일하게 이 클래스에 캡슐화해 template이 직접 경로를 비교하지
     * 않도록 한다.
     */
    public boolean isHomeActive(CurrentLocation location) {
        return "/".equals(location.path());
    }

    // BOARD_LIST는 목록 경로("/boards")나 상세 경로("/boards/{id}")일 때만 후보다 - 이 가드가 없으면
    // 전혀 무관한 페이지(예: /pages/HISTORY)에서 boardType이 둘 다 null인 우연의 일치로 false-positive가
    // 날 수 있다. 상세 페이지에서는 request query보다 이미 조회된 엔티티 값(detailBoardType/
    // detailProgramType)을 우선한다 - "상세 페이지인지" 여부를 별도로 분기하지 않고 "override 값이
    // 있으면 그것을 쓴다"는 우선순위 하나로 목록/상세 양쪽을 동일한 코드로 처리한다.
    private boolean matchesBoardList(String hrefQuery, CurrentLocation location,
            String detailBoardType, String detailProgramType) {
        if (!"/boards".equals(location.path()) && !BOARD_DETAIL_PATH.matcher(location.path()).matches()) {
            return false;
        }
        Map<String, String> hrefParams = parseQuery(hrefQuery);
        String hrefBoardType = hrefParams.get("boardType");
        String hrefProgramType = hrefParams.get("programType");

        Map<String, String> requestParams = parseQuery(location.queryString());
        String effectiveBoardType = detailBoardType != null ? detailBoardType : requestParams.get("boardType");
        String effectiveProgramType = detailProgramType != null ? detailProgramType : requestParams.get("programType");

        return Objects.equals(hrefBoardType, effectiveBoardType) && Objects.equals(hrefProgramType, effectiveProgramType);
    }

    private boolean matchesProgramList(String hrefQuery, CurrentLocation location, String detailProgramType) {
        if (!"/programs".equals(location.path()) && !PROGRAM_DETAIL_PATH.matcher(location.path()).matches()) {
            return false;
        }
        Map<String, String> hrefParams = parseQuery(hrefQuery);
        String hrefProgramType = hrefParams.get("programType");

        Map<String, String> requestParams = parseQuery(location.queryString());
        String effectiveProgramType = detailProgramType != null ? detailProgramType : requestParams.get("programType");

        return Objects.equals(hrefProgramType, effectiveProgramType);
    }

    private String pathOf(String href) {
        int idx = href.indexOf('?');
        return idx < 0 ? href : href.substring(0, idx);
    }

    private String queryOf(String href) {
        int idx = href.indexOf('?');
        return idx < 0 ? null : href.substring(idx + 1);
    }

    private Map<String, String> parseQuery(String queryString) {
        Map<String, String> params = new LinkedHashMap<>();
        if (queryString == null || queryString.isBlank()) {
            return params;
        }
        for (String pair : queryString.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            params.put(decode(key), decode(value));
        }
        return params;
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }
}
