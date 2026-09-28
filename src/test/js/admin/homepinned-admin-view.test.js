// package.json 없이 Node.js 내장 테스트 러너로 실행한다:
//   node --test src/test/js/admin/homepinned-admin-view.test.js
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

function readTemplate(relativePath) {
    return fs.readFileSync(path.join(__dirname, '../../../main/resources/templates', relativePath), 'utf8');
}

function html() {
    return readTemplate('admin/homepinned/list.html');
}

test('templates/admin/homepinned/list.html inherits the common admin layout', () => {
    assert.match(html(), /th:replace="~\{admin\/layout\/default :: layout\(/);
});

test('templates/admin/homepinned/list.html has a #searchSubtype select next to #searchTargetType', () => {
    const targetTypeIndex = html().indexOf('id="searchTargetType"');
    const subtypeIndex = html().indexOf('id="searchSubtype"');
    assert.notEqual(targetTypeIndex, -1);
    assert.notEqual(subtypeIndex, -1);
    assert.ok(targetTypeIndex < subtypeIndex, '#searchSubtype must come after #searchTargetType in the search form');
});

// P13-T38C: searchState는 targetType/subtype/keyword를 committed 값으로 보관한다. keyword는
// searchForm의 submit(검색 버튼/Enter)에서만 갱신되고, targetType/subtype의 change 리스너는 keyword를
// 절대 다시 읽지 않는다.
test('templates/admin/homepinned/list.html defines searchState with BOARD/empty-subtype/empty-keyword defaults', () => {
    assert.match(html(), /var searchState = \{targetType: 'BOARD', subtype: '', keyword: ''\};/);
});

test('templates/admin/homepinned/list.html SUBTYPE_OPTIONS maps BOARD to NOTICE/GALLERY/ARCHIVE/REVIEW with an empty "전체" option first', () => {
    const source = html();
    const boardOptionsIndex = source.indexOf('BOARD: [');
    const programOptionsIndex = source.indexOf('PROGRAM: [');
    assert.notEqual(boardOptionsIndex, -1);
    assert.notEqual(programOptionsIndex, -1);
    assert.ok(boardOptionsIndex < programOptionsIndex);

    const boardOptionsBlock = source.slice(boardOptionsIndex, programOptionsIndex);
    assert.match(boardOptionsBlock, /\{value: '', label: '전체'\}/);
    assert.match(boardOptionsBlock, /\{value: 'NOTICE', label: '공지사항'\}/);
    assert.match(boardOptionsBlock, /\{value: 'GALLERY', label: '갤러리'\}/);
    assert.match(boardOptionsBlock, /\{value: 'ARCHIVE', label: '자료실'\}/);
    assert.match(boardOptionsBlock, /\{value: 'REVIEW', label: '강의 후기'\}/);
});

// COURSE 라벨은 공개 Menu/문서("수강 프로그램")가 아니라, 같은 관리자 CMS 화면인
// admin/program/list.html·admin/program/form.html과 동일한 "정규 강좌"를 쓴다(내부 일관성 우선).
test('templates/admin/homepinned/list.html SUBTYPE_OPTIONS maps PROGRAM to COURSE("정규 강좌")/SPECIAL with an empty "전체" option first', () => {
    const source = html();
    const programOptionsIndex = source.indexOf('PROGRAM: [');
    const programOptionsEnd = source.indexOf('};', programOptionsIndex);
    assert.notEqual(programOptionsIndex, -1);

    const programOptionsBlock = source.slice(programOptionsIndex, programOptionsEnd);
    assert.match(programOptionsBlock, /\{value: '', label: '전체'\}/);
    assert.match(programOptionsBlock, /\{value: 'COURSE', label: '정규 강좌'\}/);
    assert.match(programOptionsBlock, /\{value: 'SPECIAL', label: '특강'\}/);
});

test('templates/admin/homepinned/list.html has a populateSubtypeOptions() function that rebuilds #searchSubtype from SUBTYPE_OPTIONS[searchState.targetType]', () => {
    const source = html();
    const fnIndex = source.indexOf('function populateSubtypeOptions()');
    assert.notEqual(fnIndex, -1);
    const fnEnd = source.indexOf('\n        }', fnIndex);
    const fnBody = source.slice(fnIndex, fnEnd);

    assert.match(fnBody, /getElementById\('searchSubtype'\)/);
    assert.match(fnBody, /SUBTYPE_OPTIONS\[searchState\.targetType\]/);
});

// buildSearchParams()가 targetType에 따라 boardType 또는 programType 중 정확히 하나의 파라미터
// 이름으로만 subtype을 실어 보내는지 소스 레벨에서 확인한다(BOARD/PROGRAM 파라미터가 절대 섞이지
// 않는다는 구조적 보장).
test('templates/admin/homepinned/list.html buildSearchParams() sends boardType only for BOARD and programType only for PROGRAM, never a subtype parameter', () => {
    const source = html();
    const fnIndex = source.indexOf('function buildSearchParams()');
    const fnEnd = source.indexOf('\n        }', fnIndex);
    assert.notEqual(fnIndex, -1);
    const fnBody = source.slice(fnIndex, fnEnd);

    assert.match(fnBody, /params\.set\('size', 20\)/);
    assert.match(fnBody, /searchState\.targetType === 'BOARD'/);
    assert.match(fnBody, /params\.set\('boardType', searchState\.subtype\)/);
    assert.match(fnBody, /params\.set\('programType', searchState\.subtype\)/);
    assert.doesNotMatch(fnBody, /'subtype'/);
});

test('templates/admin/homepinned/list.html buildSearchParams() omits boardType/programType entirely when subtype is empty ("전체")', () => {
    const source = html();
    const fnIndex = source.indexOf('function buildSearchParams()');
    const fnEnd = source.indexOf('\n        }', fnIndex);
    const fnBody = source.slice(fnIndex, fnEnd);

    assert.match(fnBody, /if \(searchState\.subtype\) \{/);
});

test('templates/admin/homepinned/list.html runSearch() builds the request from searchState only, never reading the keyword input directly', () => {
    const source = html();
    const fnIndex = source.indexOf('function runSearch()');
    const fnEnd = source.indexOf('\n        }', fnIndex);
    assert.notEqual(fnIndex, -1);
    const fnBody = source.slice(fnIndex, fnEnd);

    assert.match(fnBody, /searchState\.targetType === 'BOARD'/);
    assert.match(fnBody, /buildSearchParams\(\)/);
    assert.doesNotMatch(fnBody, /getElementById\('searchKeyword'\)/);
    assert.doesNotMatch(fnBody, /getElementById\('searchTargetType'\)/);
});

// targetType change: targetType 갱신 + subtype 초기화 + subtype option 재구성 + 즉시 재조회.
// keyword는 이 핸들러 안에서 절대 다시 읽지 않는다(§keyword 정책, admin/board/list.html의 P13-T31과 동일).
test('templates/admin/homepinned/list.html #searchTargetType change listener resets subtype, rebuilds subtype options, and re-fetches without touching keyword', () => {
    const source = html();
    const changeHandlerIndex = source.indexOf("document.getElementById('searchTargetType').addEventListener('change'");
    assert.notEqual(changeHandlerIndex, -1, 'searchTargetType must have a change listener');

    const handlerEnd = source.indexOf('});', changeHandlerIndex);
    const handlerBody = source.slice(changeHandlerIndex, handlerEnd);

    assert.match(handlerBody, /searchState\.targetType = this\.value;/);
    assert.match(handlerBody, /searchState\.subtype = '';/);
    assert.match(handlerBody, /populateSubtypeOptions\(\);/);
    assert.match(handlerBody, /runSearch\(\);/);
    assert.doesNotMatch(handlerBody, /searchState\.keyword/);
});

// subtype change: subtype만 갱신 + 즉시 재조회. keyword/targetType은 건드리지 않는다.
test('templates/admin/homepinned/list.html #searchSubtype change listener updates subtype and re-fetches without touching keyword or targetType', () => {
    const source = html();
    const changeHandlerIndex = source.indexOf("document.getElementById('searchSubtype').addEventListener('change'");
    assert.notEqual(changeHandlerIndex, -1, 'searchSubtype must have a change listener');

    const handlerEnd = source.indexOf('});', changeHandlerIndex);
    const handlerBody = source.slice(changeHandlerIndex, handlerEnd);

    assert.match(handlerBody, /searchState\.subtype = this\.value;/);
    assert.match(handlerBody, /runSearch\(\);/);
    assert.doesNotMatch(handlerBody, /searchState\.keyword/);
    assert.doesNotMatch(handlerBody, /searchState\.targetType/);
});

// 기존 submit 핸들러(검색 버튼/Enter)는 targetType/subtype/keyword를 전부 함께 committed state로
// 반영한다 - 이 계약은 P13-T38C 이후에도 유지돼야 한다.
test('templates/admin/homepinned/list.html search form submit commits targetType, subtype, and keyword together', () => {
    const source = html();
    const submitHandlerIndex = source.indexOf("document.getElementById('searchForm').addEventListener('submit'");
    const changeHandlerIndex = source.indexOf("document.getElementById('searchTargetType').addEventListener('change'");
    assert.notEqual(submitHandlerIndex, -1);
    assert.ok(submitHandlerIndex < changeHandlerIndex, 'submit handler must remain defined before the new change listeners');

    const submitHandlerBody = source.slice(submitHandlerIndex, changeHandlerIndex);
    assert.match(submitHandlerBody, /searchState\.targetType = document\.getElementById\('searchTargetType'\)\.value;/);
    assert.match(submitHandlerBody, /searchState\.subtype = document\.getElementById\('searchSubtype'\)\.value;/);
    assert.match(submitHandlerBody, /searchState\.keyword = document\.getElementById\('searchKeyword'\)\.value;/);
});

test('templates/admin/homepinned/list.html initializes subtype options for the default BOARD targetType before the first render', () => {
    const source = html();
    const initCallIndex = source.lastIndexOf('populateSubtypeOptions();');
    const loadPinnedCallIndex = source.lastIndexOf('loadPinned();');
    assert.notEqual(initCallIndex, -1);
    assert.notEqual(loadPinnedCallIndex, -1);
    assert.ok(initCallIndex < loadPinnedCallIndex, 'populateSubtypeOptions() must run once at load time, before loadPinned()');
});

// ---------------------------------------------------------------------------------------------------------------
// P14-T9C-1: Admin List Presentation & Feedback 공통 계약(7개 목록이 같은 형태로 검증된다).
// ---------------------------------------------------------------------------------------------------------------
// 작업 트리(CRLF, Windows autocrlf)와 CI(LF) 어디서나 같은 marker로 자르도록 줄바꿈을 LF로 정규화한다.
function sliceBetween(rawSource, startMarker, endMarker) {
    const source = rawSource.replace(/\r\n/g, '\n');
    const start = source.indexOf(startMarker);
    assert.notEqual(start, -1, `marker not found: ${startMarker}`);
    const end = source.indexOf(endMarker, start + startMarker.length);
    assert.notEqual(end, -1, `end marker not found after ${startMarker}: ${endMarker}`);
    return source.slice(start, end);
}

function escapeRegExp(text) {
    return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

const T9C1 = {
    "template": "admin/homepinned/list.html",
    "loadFn": "loadPinned",
    "columnCountVar": "PINNED_COLUMN_COUNT",
    "columnCount": 6,
    "firstTheadColumns": 4,
    "tableCount": 2,
    "emptyMessageExpr": "'고정된 콘텐츠가 없습니다.'",
    "errorMessage": "고정 콘텐츠 목록을 불러오지 못했습니다.",
    "deleteSuccess": "고정이 해제되었습니다.",
    "deleteErrorVar": "UNPIN_ERROR",
    "patchActions": [
        {
            "marker": "orderButton.addEventListener('click'",
            "errorVar": "ORDER_CHANGE_ERROR"
        },
        {
            "marker": "visibilityButton.addEventListener('click'",
            "errorVar": "STATUS_CHANGE_ERROR"
        }
    ]
};

test(`${T9C1.template} uses the shared #admin-list-status instead of a list-local #errorMessage/showError`, () => {
    const html = readTemplate(T9C1.template);
    assert.match(html, /<div id="admin-list-status" hidden><\/div>/);
    assert.match(html, /var listStatus = document\.getElementById\('admin-list-status'\);/);
    assert.doesNotMatch(html, /id="errorMessage"/);
    assert.doesNotMatch(html, /function showError/);
    assert.doesNotMatch(html, /function clearError/);
    assert.doesNotMatch(html, /text-bg-/);
});

test(`${T9C1.template} marks every column header with scope="col" and the list table matches ${T9C1.columnCountVar}`, () => {
    const html = readTemplate(T9C1.template);
    assert.doesNotMatch(html, /<th>/);
    assert.match(html, new RegExp(`var ${T9C1.columnCountVar} = ${T9C1.columnCount};`));
    const listThead = sliceBetween(html, `<thead>`, '</thead>');
    assert.equal((listThead.match(/<th scope="col">/g) || []).length, T9C1.firstTheadColumns);
});

test(`${T9C1.template} wraps every table in .table-responsive`, () => {
    const html = readTemplate(T9C1.template);
    const tables = html.match(/<table class="table">/g) || [];
    const wrapped = html.match(/<div class="table-responsive">\s*<table class="table">/g) || [];
    assert.equal(tables.length, T9C1.tableCount);
    assert.equal(wrapped.length, T9C1.tableCount);
});

test(`${T9C1.template} ${T9C1.loadFn}() checks response.ok, renders an empty row, and replaces rows with an error row on failure`, () => {
    const html = readTemplate(T9C1.template);
    const body = sliceBetween(html, `function ${T9C1.loadFn}() {`, '\n        }\n');
    assert.match(body, /if \(!response\.ok\) \{\s*throw new Error\('HTTP ' \+ response\.status\);\s*\}\s*return response\.json\(\);/);
    assert.match(body, new RegExp(`AdminDisplay\\.renderEmptyRow\\(document, tbody, ${T9C1.columnCountVar}, ${escapeRegExp(T9C1.emptyMessageExpr)}\\);`));
    assert.match(body, new RegExp(`\\.catch\\(function \\(\\) \\{\\s*AdminDisplay\\.renderErrorRow\\(document, tbody, ${T9C1.columnCountVar}, '${escapeRegExp(T9C1.errorMessage)}'\\);`));
    // 목록 재조회는 inline status를 절대 건드리지 않는다(삭제 성공 메시지가 reload 성공/실패 후에도 남는 계약).
    assert.doesNotMatch(body, /clearStatus|showStatus/);
});

test(`${T9C1.template} reads failed-response messages safely (non-JSON body falls back) via AdminDisplay.apiErrorMessage`, () => {
    const html = readTemplate(T9C1.template);
    const body = sliceBetween(html, 'function showResponseError(response, fallback) {', '\n        }\n');
    assert.match(body, /return response\.json\(\)\s*\.catch\(function \(\) \{\s*return null;\s*\}\)/);
    assert.match(body, /AdminDisplay\.showStatus\(listStatus, AdminDisplay\.apiErrorMessage\(body, fallback\), 'error'\);/);
});

test(`${T9C1.template} delete clears status first, then on 2xx reloads and shows "${T9C1.deleteSuccess}"; failures show an error status`, () => {
    const html = readTemplate(T9C1.template);
    const handler = sliceBetween(html, "deleteButton.addEventListener('click'", '\n            });\n');
    const clearIndex = handler.indexOf('AdminDisplay.clearStatus(listStatus);');
    const deleteIndex = handler.indexOf("{method: 'DELETE'}");
    assert.ok(clearIndex !== -1 && clearIndex < deleteIndex, 'clearStatus must run before the DELETE request');
    assert.match(handler, new RegExp(`if \\(response\\.ok\\) \\{\\s*(?:\\/\\/[^\\n]*\\s*)?${T9C1.loadFn}\\(\\);\\s*(?:\\/\\/[^\\n]*\\s*)?AdminDisplay\\.showStatus\\(listStatus, '${escapeRegExp(T9C1.deleteSuccess)}', 'success'\\);`));
    assert.match(handler, new RegExp(`return showResponseError\\(response, ${T9C1.deleteErrorVar}\\);`));
    assert.match(handler, new RegExp(`\\.catch\\(function \\(\\) \\{\\s*AdminDisplay\\.showStatus\\(listStatus, ${T9C1.deleteErrorVar}, 'error'\\);`));
});

test(`${T9C1.template} PATCH actions check response.ok and surface failures instead of blindly reloading`, () => {
    const html = readTemplate(T9C1.template);
    assert.doesNotMatch(html, new RegExp(`\\.then\\(${T9C1.loadFn}\\)`));
    if (T9C1.patchActions.length === 0) {
        assert.doesNotMatch(html, /method: 'PATCH'/);
        return;
    }
    const reload = sliceBetween(html, 'function reloadOrShowError(response, fallback) {', '\n        }\n');
    assert.match(reload, new RegExp(`if \\(response\\.ok\\) \\{\\s*${T9C1.loadFn}\\(\\);\\s*return undefined;\\s*\\}\\s*return showResponseError\\(response, fallback\\);`));
    assert.equal((html.match(/method: 'PATCH'/g) || []).length, T9C1.patchActions.length);
    for (const action of T9C1.patchActions) {
        const handler = sliceBetween(html, action.marker, '\n            });\n');
        const clearIndex = handler.indexOf('AdminDisplay.clearStatus(listStatus);');
        const patchIndex = handler.indexOf("method: 'PATCH'");
        assert.ok(clearIndex !== -1 && patchIndex !== -1 && clearIndex < patchIndex, `${action.marker}: clearStatus must run before PATCH`);
        assert.match(handler, new RegExp(`return reloadOrShowError\\(response, ${action.errorVar}\\);`));
        assert.match(handler, new RegExp(`\\.catch\\(function \\(\\) \\{\\s*AdminDisplay\\.showStatus\\(listStatus, ${action.errorVar}, 'error'\\);`));
    }
});

// P14-T9C-1: 타입은 neutral, 노출 여부는 positive/muted, 원본 상태는 공개(positive) 외에는 관리자 확인이 필요한
// attention이다(유형별 다색 badge 없음).
test('templates/admin/homepinned/list.html maps target type/visibility/source status to semantic badge tones', () => {
    const source = html();
    assert.match(source, /return AdminDisplay\.createBadge\(document, TARGET_TYPE_LABELS\[targetType\] \|\| targetType, 'neutral'\);/);
    assert.match(source, /return AdminDisplay\.createStateBadge\(document, visible, '노출', '숨김'\);/);
    assert.match(source, /PUBLIC: \{text: '공개', tone: 'positive'\}/);
    assert.match(source, /PRIVATE: \{text: '비공개', tone: 'attention'\}/);
    assert.match(source, /DELETED: \{text: '원본 없음', tone: 'attention'\}/);
    assert.match(source, /SOURCE_STATUS_LABELS\[sourceStatus\] \|\| \{text: sourceStatus, tone: 'neutral'\}/);
    assert.match(source, /publicTd\.appendChild\(AdminDisplay\.createStateBadge\(document, item\.isPublic, '공개', '비공개'\)\);/);
});

test('templates/admin/homepinned/list.html runSearch() checks response.ok and renders empty/error rows in the search table', () => {
    const source = html().replace(/\r\n/g, '\n');
    const body = source.slice(source.indexOf('function runSearch() {'), source.indexOf("document.getElementById('toggleAddPanel')"));
    assert.match(body, /if \(!response\.ok\) \{\s*throw new Error\('HTTP ' \+ response\.status\);/);
    assert.match(body, /AdminDisplay\.renderEmptyRow\(document, tbody, SEARCH_COLUMN_COUNT, '검색 결과가 없습니다\.'\);/);
    assert.match(body, /\.catch\(function \(\) \{\s*AdminDisplay\.renderErrorRow\(document, tbody, SEARCH_COLUMN_COUNT, '검색 결과를 불러오지 못했습니다\.'\);/);
    assert.match(source, /var SEARCH_COLUMN_COUNT = 4;/);
});

test('templates/admin/homepinned/list.html pin (POST) checks response.ok and shows an error status on failure', () => {
    const source = html().replace(/\r\n/g, '\n');
    const handler = source.slice(source.indexOf("addButton.addEventListener('click'"), source.indexOf('actionTd.appendChild(addButton);'));
    const clearIndex = handler.indexOf('AdminDisplay.clearStatus(listStatus);');
    assert.ok(clearIndex !== -1 && clearIndex < handler.indexOf("method: 'POST'"));
    assert.match(handler, /if \(response\.ok\) \{\s*loadPinned\(\);\s*runSearch\(\);\s*return undefined;\s*\}\s*return showResponseError\(response, PIN_ERROR\);/);
    assert.match(handler, /\.catch\(function \(\) \{\s*AdminDisplay\.showStatus\(listStatus, PIN_ERROR, 'error'\);/);
});
