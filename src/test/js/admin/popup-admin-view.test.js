// package.json 없이 Node.js 내장 테스트 러너로 실행한다:
//   node --test src/test/js/admin/popup-admin-view.test.js
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

function readTemplate(relativePath) {
    return fs.readFileSync(path.join(__dirname, '../../../main/resources/templates', relativePath), 'utf8');
}

test('templates/admin/popup/list.html inherits the common admin layout', () => {
    const html = readTemplate('admin/popup/list.html');
    assert.match(html, /th:replace="~\{admin\/layout\/default :: layout\(/);
});

test('templates/admin/popup/list.html loads popups from the admin popup API via common fetch as a plain array (no pagination, no search)', () => {
    const html = readTemplate('admin/popup/list.html');
    assert.match(html, /AdminFetch\.adminFetch\('\/api\/admin\/popups'\)/);
    assert.match(html, /var popups = body\.data;/);
    assert.doesNotMatch(html, /buildQuery/);
    assert.doesNotMatch(html, /data\.content/);
    assert.doesNotMatch(html, /data\.last/);
    assert.doesNotMatch(html, /keyword/i);
});

test('templates/admin/popup/list.html links to the new-popup screen', () => {
    const html = readTemplate('admin/popup/list.html');
    assert.match(html, /href="\/admin\/popups\/new"/);
});

test('templates/admin/popup/list.html links each row to its edit screen', () => {
    const html = readTemplate('admin/popup/list.html');
    assert.match(html, /editLink\.href = '\/admin\/popups\/' \+ popup\.id \+ '\/edit'/);
});

test('templates/admin/popup/list.html wires the delete action to DELETE /api/admin/popups/{id}', () => {
    const html = readTemplate('admin/popup/list.html');
    assert.match(html, /AdminFetch\.adminFetch\('\/api\/admin\/popups\/' \+ popup\.id, \{method: 'DELETE'\}\)/);
});

test('templates/admin/popup/list.html wires the visibility toggle to PATCH .../visibility', () => {
    const html = readTemplate('admin/popup/list.html');
    assert.match(html, /'\/api\/admin\/popups\/' \+ popup\.id \+ '\/visibility'/);
    assert.match(html, /isVisible: !popup\.isVisible/);
});

test('templates/admin/popup/list.html displays startDate/endDate as plain text without a date formatting library', () => {
    const html = readTemplate('admin/popup/list.html');
    assert.match(html, /startDateTd\.textContent = popup\.startDate/);
    assert.match(html, /endDateTd\.textContent = popup\.endDate/);
});

test('templates/admin/popup/list.html has no sortOrder input, /order PATCH, or image upload UI (not part of the Popup API)', () => {
    const html = readTemplate('admin/popup/list.html');
    assert.doesNotMatch(html, /sortOrder/i);
    assert.doesNotMatch(html, /\/order/);
    assert.doesNotMatch(html, /image/i);
    assert.doesNotMatch(html, /thumbnail/i);
});

test('templates/admin/popup/form.html inherits the common admin layout', () => {
    const html = readTemplate('admin/popup/form.html');
    assert.match(html, /th:replace="~\{admin\/layout\/default :: layout\(/);
});

test('templates/admin/popup/form.html parses the editing popup id from the URL path, not from a model attribute', () => {
    const html = readTemplate('admin/popup/form.html');
    assert.match(html, /window\.location\.pathname\.match\(/);
    assert.match(html, /function extractPopupIdFromPath/);
});

test('templates/admin/popup/form.html branches between POST and PUT based on the presence of a popup id', () => {
    const html = readTemplate('admin/popup/form.html');
    assert.match(html, /var method = popupId \? 'PUT' : 'POST';/);
});

test('templates/admin/popup/form.html waits for both the CKEditor instance and the fetched popup before prefilling', () => {
    const html = readTemplate('admin/popup/form.html');
    const editorReadyIndex = html.indexOf('var editorReady =');
    const promiseAllIndex = html.indexOf('Promise.all([');
    const setDataIndex = html.indexOf('editor.setData(popup.content');

    assert.notEqual(editorReadyIndex, -1);
    assert.notEqual(promiseAllIndex, -1);
    assert.notEqual(setDataIndex, -1);
    assert.ok(editorReadyIndex < promiseAllIndex, 'editorReady must be defined before Promise.all waits on it');
    assert.ok(promiseAllIndex < setDataIndex, 'editor.setData must run only after Promise.all resolves');
    assert.match(html, /Promise\.all\(\[[\s\S]*?editorReady[\s\S]*?\]\)/);
});

test('templates/admin/popup/form.html uses native datetime-local inputs for startDate/endDate', () => {
    const html = readTemplate('admin/popup/form.html');
    assert.match(html, /<input type="datetime-local" class="form-control" id="startDate"/);
    assert.match(html, /<input type="datetime-local" class="form-control" id="endDate"/);
});

test('templates/admin/popup/form.html submits the datetime-local input values as-is, without Date/toISOString/UTC conversion', () => {
    const html = readTemplate('admin/popup/form.html');
    assert.match(html, /startDate: document\.querySelector\('#startDate'\)\.value/);
    assert.match(html, /endDate: document\.querySelector\('#endDate'\)\.value/);
    assert.doesNotMatch(html, /new Date\(/);
    assert.doesNotMatch(html, /toISOString/);
    assert.doesNotMatch(html, /\.value\s*\+\s*['"]Z['"]/);
});

test('templates/admin/popup/form.html truncates the fetched startDate/endDate to 16 characters (yyyy-MM-ddTHH:mm) when prefilling, with no step attribute or seconds UI', () => {
    const html = readTemplate('admin/popup/form.html');
    assert.match(html, /popup\.startDate\.slice\(0, 16\)/);
    assert.match(html, /popup\.endDate\.slice\(0, 16\)/);
    assert.doesNotMatch(html, /step=/);
});

test('templates/admin/popup/form.html does not add client-side startDate<=endDate validation beyond the existing server contract', () => {
    const html = readTemplate('admin/popup/form.html');
    assert.doesNotMatch(html, /startDate.*<=.*endDate/);
    assert.doesNotMatch(html, /isValidDateRange/);
});

test('templates/admin/popup/form.html redirects to the list screen after a successful save', () => {
    const html = readTemplate('admin/popup/form.html');
    assert.match(html, /window\.location\.href = '\/admin\/popups'/);
});

test('templates/admin/popup/form.html displays an error message when the save request fails', () => {
    const html = readTemplate('admin/popup/form.html');
    assert.match(html, /function showError/);
    assert.match(html, /errorMessage\.style\.display = 'block'/);
});

test('templates/admin/popup/form.html has no image/thumbnail/attachment upload fields or sortOrder input (not part of the Popup API)', () => {
    const html = readTemplate('admin/popup/form.html');
    assert.doesNotMatch(html, /thumbnail/i);
    assert.doesNotMatch(html, /attachment/i);
    assert.doesNotMatch(html, /imageInput/);
    assert.doesNotMatch(html, /sortOrder/i);
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
    "template": "admin/popup/list.html",
    "loadFn": "loadPopups",
    "columnCountVar": "COLUMN_COUNT",
    "columnCount": 5,
    "firstTheadColumns": 5,
    "tableCount": 1,
    "emptyMessageExpr": "'등록된 팝업이 없습니다.'",
    "errorMessage": "팝업 목록을 불러오지 못했습니다.",
    "deleteSuccess": "삭제되었습니다.",
    "deleteErrorVar": "DELETE_ERROR",
    "patchActions": [
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

// P14-T9C-1: 노출 여부는 state badge(노출 positive / 비노출 muted). 날짜는 원래 값 그대로다.
test('templates/admin/popup/list.html renders visibility as a positive/muted state badge', () => {
    const html = readTemplate('admin/popup/list.html');
    assert.match(html, /visibleTd\.appendChild\(AdminDisplay\.createStateBadge\(document, popup\.isVisible, '노출', '비노출'\)\);/);
    assert.doesNotMatch(html, /visibleTd\.textContent/);
});
