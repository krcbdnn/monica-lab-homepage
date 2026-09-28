// P14-T9C-2: 관리자 목록(Board/Program/File)의 URL state helper. URL query를 목록 state의 canonical 표현으로 쓰고
// (page는 API와 같은 0-based, 기본값은 생략), history는 replaceState만 쓴다 - pushState/popstate/router는 없다.
// 목록 base path는 아래 config에 고정되어 있고 allowlist를 통과한 값만 query로 직렬화하므로 임의 returnUrl/경로가
// 만들어지지 않는다. 표시(presentation)는 admin-display.js의 책임이며 이 파일은 DOM을 직접 바꾸지 않는다.
(function (root, factory) {
    if (typeof module === 'object' && module.exports) {
        module.exports = factory();
    } else {
        root.AdminListState = factory();
    }
})(typeof self !== 'undefined' ? self : this, function () {
    'use strict';

    // filterName은 목록 template의 state 필드명이자 API/URL query 이름이다(서버 enum 값만 허용).
    var BOARD_LIST = Object.freeze({
        path: '/admin/boards',
        filterName: 'boardType',
        filterValues: Object.freeze(['NOTICE', 'GALLERY', 'ARCHIVE', 'REVIEW']),
        hasKeyword: true
    });
    var PROGRAM_LIST = Object.freeze({
        path: '/admin/programs',
        filterName: 'programType',
        filterValues: Object.freeze(['COURSE', 'SPECIAL']),
        hasKeyword: true
    });
    var FILE_LIST = Object.freeze({
        path: '/admin/files',
        filterName: null,
        filterValues: Object.freeze([]),
        hasKeyword: false
    });

    // Spring은 int로 파싱할 수 없는 page를 조용히 0으로 바꾸므로, 9자리 이하 음이 아닌 정수만 받아 URL과 실제 조회
    // page가 어긋나지 않게 한다(-1, abc, 1.5, 1e3, 10자리 이상은 전부 0).
    function parsePage(value) {
        return typeof value === 'string' && /^\d{1,9}$/.test(value) ? Number(value) : 0;
    }

    // 공백뿐인 keyword는 API에서도 "필터 없음"이라 canonical 기본값으로 본다. 그 외 keyword는 검색 의미가 바뀌지
    // 않도록 trim하지 않고 그대로 쓴다.
    function isBlankKeyword(keyword) {
        return typeof keyword !== 'string' || keyword.trim() === '';
    }

    function readState(config, search) {
        var params = new URLSearchParams(search || '');
        var state = {};
        if (config.filterName) {
            var filter = params.get(config.filterName);
            state[config.filterName] = config.filterValues.indexOf(filter) !== -1 ? filter : '';
        }
        if (config.hasKeyword) {
            var keyword = params.get('keyword');
            state.keyword = isBlankKeyword(keyword) ? '' : keyword;
        }
        state.page = parsePage(params.get('page'));
        return state;
    }

    // 고정 순서(filter → keyword → page)로 allowlist 값만 직렬화하고 기본값은 생략한다. 입력 state는 바꾸지 않는다.
    function toSearch(config, state) {
        var params = new URLSearchParams();
        if (config.filterName && config.filterValues.indexOf(state[config.filterName]) !== -1) {
            params.set(config.filterName, state[config.filterName]);
        }
        if (config.hasKeyword && !isBlankKeyword(state.keyword)) {
            params.set('keyword', state.keyword);
        }
        var page = Number(state.page);
        if (Number.isInteger(page) && page > 0 && page <= 999999999) {
            params.set('page', String(page));
        }
        var search = params.toString();
        return search ? '?' + search : '';
    }

    function listHref(config, state) {
        return config.path + toSearch(config, state);
    }

    // 목록 → 상세 링크. 상세 URL의 query는 "목록으로" 복귀에만 쓰인다(상세 조회에는 관여하지 않음).
    function detailHref(config, id, state) {
        return config.path + '/' + encodeURIComponent(String(id)) + toSearch(config, state);
    }

    // 현재 주소가 canonical 목록 URL과 다를 때만 replaceState한다(replaceState는 load/popstate를 일으키지 않는다).
    function replaceUrl(win, config, state) {
        var href = listHref(config, state);
        if (win.location.pathname + win.location.search === href) {
            return false;
        }
        win.history.replaceState(win.history.state, '', href);
        return true;
    }

    // 정상 PageResponse를 받은 뒤 실제로 사용할 page를 정한다(조회 실패 시에는 호출하지 않는다).
    // - CASE A: 데이터는 있는데 요청 page가 범위 밖 → 마지막 유효 page로 1회 재조회(reload: true, allowReload일 때만).
    // - CASE B: 전체 0건인데 page > 0 → 이미 받은 빈 응답을 그대로 쓰고 page만 0으로 정규화(reload: false).
    // - 그 외: 서버가 실제로 사용한 data.page를 따른다.
    function resolvePage(requestedPage, data, allowReload) {
        if (allowReload && requestedPage > 0 && data.content.length === 0
                && data.totalElements > 0 && data.totalPages > 0) {
            return {page: data.totalPages - 1, reload: true};
        }
        if (requestedPage > 0 && data.totalElements === 0) {
            return {page: 0, reload: false};
        }
        return {page: data.page, reload: false};
    }

    return {
        BOARD_LIST: BOARD_LIST,
        PROGRAM_LIST: PROGRAM_LIST,
        FILE_LIST: FILE_LIST,
        parsePage: parsePage,
        readState: readState,
        toSearch: toSearch,
        listHref: listHref,
        detailHref: detailHref,
        replaceUrl: replaceUrl,
        resolvePage: resolvePage
    };
});
