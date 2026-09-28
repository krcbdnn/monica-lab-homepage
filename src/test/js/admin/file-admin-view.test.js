// package.json 없이 Node.js 내장 테스트 러너로 실행한다:
//   node --test src/test/js/admin/file-admin-view.test.js
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

function readTemplate(relativePath) {
    return fs.readFileSync(path.join(__dirname, '../../../main/resources/templates', relativePath), 'utf8');
}

test('templates/admin/file/list.html inherits the common admin layout', () => {
    const html = readTemplate('admin/file/list.html');
    assert.match(html, /th:replace="~\{admin\/layout\/default :: layout\(/);
});

test('templates/admin/file/list.html loads files from the admin file API via common fetch with page/size pagination', () => {
    const html = readTemplate('admin/file/list.html');
    assert.match(html, /AdminFetch\.adminFetch\('\/api\/admin\/files\?' \+ buildQuery\(\)\)/);
    assert.match(html, /params\.set\('page', state\.page\)/);
    assert.match(html, /params\.set\('size', state\.size\)/);
});

test('templates/admin/file/list.html renders rows from the paginated response and toggles prev/next based on page/last', () => {
    const html = readTemplate('admin/file/list.html');
    assert.match(html, /data\.content\.forEach\(function \(file\)/);
    // P14-T9C-2: 이전 버튼은 정규화된 state.page(범위 밖/전체 0건 보정 후) 기준이다.
    assert.match(html, /document\.getElementById\('prev-page'\)\.disabled = state\.page <= 0/);
    assert.match(html, /document\.getElementById\('next-page'\)\.disabled = data\.last/);
});

test('templates/admin/file/list.html links originalName to the existing download URL, without an image thumbnail preview', () => {
    const html = readTemplate('admin/file/list.html');
    assert.match(html, /nameLink\.href = file\.url/);
    assert.match(html, /nameLink\.textContent = file\.originalName/);
    assert.doesNotMatch(html, /<img/);
    assert.doesNotMatch(html, /\.src = file\./);
});

test('templates/admin/file/list.html opens the download link in a new tab with rel="noopener noreferrer"', () => {
    const html = readTemplate('admin/file/list.html');
    assert.match(html, /nameLink\.target = '_blank'/);
    assert.match(html, /nameLink\.rel = 'noopener noreferrer'/);
});

test('templates/admin/file/list.html formats size with a local formatFileSize helper (B/KB/MB), no shared util', () => {
    const html = readTemplate('admin/file/list.html');
    assert.match(html, /function formatFileSize\(bytes\)/);
    assert.match(html, /sizeTd\.textContent = formatFileSize\(file\.size\)/);
    assert.match(html, /' B'/);
    assert.match(html, /' KB'/);
    assert.match(html, /' MB'/);
});

test('templates/admin/file/list.html wires the delete action to DELETE /api/admin/files/{id} and reloads on success', () => {
    const html = readTemplate('admin/file/list.html');
    assert.match(html, /AdminFetch\.adminFetch\('\/api\/admin\/files\/' \+ file\.id, \{method: 'DELETE'\}\)/);
    assert.match(html, /if \(response\.ok\) \{\s*loadFiles\(\);/);
});

// P14-T9C-1: 목록 전용 #errorMessage/showError는 공통 #admin-list-status + AdminDisplay.showStatus로 대체됐다.
// 삭제 실패 시 서버의 사용자용 error.message(없으면 fallback)를 error status로 보여주는 계약은 그대로다.
test('templates/admin/file/list.html shows an error status with the server message when the delete request fails', () => {
    const html = readTemplate('admin/file/list.html');
    assert.match(html, /<div id="admin-list-status" hidden><\/div>/);
    assert.match(html, /return showResponseError\(response, DELETE_ERROR\);/);
    assert.match(html, /AdminDisplay\.showStatus\(listStatus, AdminDisplay\.apiErrorMessage\(body, fallback\), 'error'\);/);
    assert.match(html, /var DELETE_ERROR = '삭제 중 오류가 발생했습니다\.';/);
});

test('templates/admin/file/list.html renders fileType as a neutral badge with the admin display label', () => {
    const html = readTemplate('admin/file/list.html');
    assert.match(html, /typeTd\.appendChild\(AdminDisplay\.createBadge\(document,\s*AdminDisplay\.label\(AdminDisplay\.FILE_TYPE_LABELS, file\.fileType\), 'neutral'\)\);/);
    assert.doesNotMatch(html, /typeTd\.textContent = file\.fileType/);
    // MIME/업로드일은 이번 Task에서 형식을 바꾸지 않는다.
    assert.match(html, /contentTypeTd\.textContent = file\.contentType/);
    assert.match(html, /createdAtTd\.textContent = file\.createdAt/);
});

test('templates/admin/file/list.html labels the pagination nav for assistive technology', () => {
    const html = readTemplate('admin/file/list.html');
    assert.match(html, /<nav id="pagination" class="d-flex gap-2" aria-label="페이지 이동">/);
});

test('templates/admin/file/list.html has no registration/edit/upload/search UI (not part of the P9-T2g scope)', () => {
    const html = readTemplate('admin/file/list.html');
    assert.doesNotMatch(html, /href="\/admin\/files\/new"/);
    assert.doesNotMatch(html, /\/edit/);
    assert.doesNotMatch(html, /<input[^>]*type="file"/);
    assert.doesNotMatch(html, /searchForm/);
    assert.doesNotMatch(html, /keyword/i);
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
    "template": "admin/file/list.html",
    "loadFn": "loadFiles",
    "columnCountVar": "COLUMN_COUNT",
    "columnCount": 6,
    "firstTheadColumns": 6,
    "tableCount": 1,
    "emptyMessageExpr": "'등록된 파일이 없습니다.'",
    "errorMessage": "파일 목록을 불러오지 못했습니다.",
    "deleteSuccess": "삭제되었습니다.",
    "deleteErrorVar": "DELETE_ERROR",
    "patchActions": []
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
    // P14-T9C-2: load 함수가 fallbackDone 인자를 받으므로 이름으로만 찾는다.
    const body = sliceBetween(html, `function ${T9C1.loadFn}(`, '\n        }\n');
    assert.match(body, /if \(!response\.ok\) \{\s*throw new Error\('HTTP ' \+ response\.status\);\s*\}\s*return response\.json\(\);/);
    assert.match(body, new RegExp(`AdminDisplay\\.renderEmptyRow\\(document, tbody, ${T9C1.columnCountVar}, ${escapeRegExp(T9C1.emptyMessageExpr)}\\);`));
    // P14-T9C-2: catch 첫 줄의 stale-response guard(`if (seq !== loadSeq) { return; }`)는 T9C-2 테스트가 별도로 강제한다.
    assert.match(body, new RegExp(`\\.catch\\(function \\(\\) \\{\\s*(?:if \\(seq !== loadSeq\\) \\{\\s*return;\\s*\\}\\s*)?AdminDisplay\\.renderErrorRow\\(document, tbody, ${T9C1.columnCountVar}, '${escapeRegExp(T9C1.errorMessage)}'\\);`));
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

// ---------------------------------------------------------------------------------------------------------------
// P14-T9C-2: Admin List State & Navigation 계약(URL = 목록 state, replaceState만, race guard, pagination fallback).
// ---------------------------------------------------------------------------------------------------------------
const T9C2 = {
    "template": "admin/file/list.html",
    "config": "FILE_LIST",
    "loadFn": "loadFiles",
    "restores": [
        "state.page = AdminListState.readState(AdminListState.FILE_LIST, window.location.search).page;"
    ]
};

function t9c2Source() {
    return readTemplate(T9C2.template).replace(/\r\n/g, '\n');
}

test(`${T9C2.template} loads admin-list-state.js right before its inline script`, () => {
    const source = t9c2Source();
    const helperIndex = source.indexOf('<script src="/js/admin/admin-list-state.js"></script>');
    const inlineIndex = source.indexOf('<script>');
    assert.notEqual(helperIndex, -1);
    assert.ok(helperIndex < inlineIndex, 'the helper must load before the inline script that uses AdminListState');
    assert.equal(source.indexOf('AdminListState'), source.indexOf('AdminListState', inlineIndex),
        'AdminListState must not be referenced before the inline script');
});

test(`${T9C2.template} restores state from the URL once, before the single initial load`, () => {
    const source = t9c2Source();
    const readIndex = source.indexOf(`AdminListState.readState(AdminListState.${T9C2.config}, window.location.search)`);
    const initialLoadIndex = source.lastIndexOf(`${T9C2.loadFn}();`);
    assert.notEqual(readIndex, -1);
    assert.ok(readIndex < initialLoadIndex);
    assert.equal((source.match(/AdminListState\.readState\(/g) || []).length, 1, 'state is read from the URL exactly once');
    for (const restore of T9C2.restores) {
        assert.ok(source.includes(restore), `missing restore: ${restore}`);
    }
    // 진입 시 조회는 파일 끝의 한 번뿐이다(replaceState는 load를 일으키지 않는다).
    assert.match(source, new RegExp(`\\n        ${T9C2.loadFn}\\(\\);\\n    </script>`));
});

test(`${T9C2.template} uses replaceState only (no pushState/popstate) through AdminListState.replaceUrl`, () => {
    const source = t9c2Source();
    assert.doesNotMatch(source, /pushState|popstate|history\.replaceState|location\.href\s*=|returnUrl|redirectUrl/);
    const body = sliceBetween(source, `function ${T9C2.loadFn}(fallbackDone) {`, '\n        }\n');
    const replaceBeforeFetch = body.indexOf(`AdminListState.replaceUrl(window, AdminListState.${T9C2.config}, state);`);
    assert.ok(replaceBeforeFetch !== -1 && replaceBeforeFetch < body.indexOf('AdminFetch.adminFetch('),
        'the URL must reflect the requested state before the request');
});

test(`${T9C2.template} guards against stale responses with a request sequence number in then and catch`, () => {
    const source = t9c2Source();
    assert.match(source, /var loadSeq = 0;/);
    const body = sliceBetween(source, `function ${T9C2.loadFn}(fallbackDone) {`, '\n        }\n');
    assert.match(body, /var seq = \+\+loadSeq;/);
    assert.match(body, /\.then\(function \(body\) \{\s*if \(seq !== loadSeq\) \{\s*return;\s*\}/);
    assert.match(body, /\.catch\(function \(\) \{\s*if \(seq !== loadSeq\) \{\s*return;\s*\}/);
});

test(`${T9C2.template} resolves out-of-range pages only from a successful response and reloads at most once`, () => {
    const source = t9c2Source();
    const body = sliceBetween(source, `function ${T9C2.loadFn}(fallbackDone) {`, '\n        }\n');
    const resolveIndex = body.indexOf('var resolved = AdminListState.resolvePage(requestedPage, data, !fallbackDone);');
    assert.notEqual(resolveIndex, -1);
    assert.ok(resolveIndex > body.indexOf('.then(function (body) {'), 'fallback is decided only after a successful response');
    assert.match(body, new RegExp(`state\\.page = resolved\\.page;\\s*if \\(resolved\\.reload\\) \\{\\s*${T9C2.loadFn}\\(true\\);\\s*return;\\s*\\}`));
    // 재조회가 필요하면 중간 빈 목록을 그리지 않고 반환한다(렌더 코드는 reload 분기 뒤).
    assert.ok(body.indexOf("tbody.innerHTML = '';") > body.indexOf('if (resolved.reload)'));
    const catchBody = body.slice(body.indexOf('.catch('));
    assert.doesNotMatch(catchBody, /state\.page|resolvePage|replaceUrl/, 'a failed request must not change the page');
    assert.match(body, /document\.getElementById\('prev-page'\)\.disabled = state\.page <= 0;/);
    assert.match(body, /document\.getElementById\('next-page'\)\.disabled = data\.last;/);
});
