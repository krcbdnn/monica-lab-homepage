// package.json 없이 Node.js 내장 테스트 러너로 실행한다:
//   node --test src/test/js/admin/program-admin-view.test.js
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

function readTemplate(relativePath) {
    return fs.readFileSync(path.join(__dirname, '../../../main/resources/templates', relativePath), 'utf8');
}

test('templates/admin/program/list.html inherits the common admin layout', () => {
    const html = readTemplate('admin/program/list.html');
    assert.match(html, /th:replace="~\{admin\/layout\/default :: layout\(/);
});

test('templates/admin/program/list.html loads programs from the admin program API via common fetch', () => {
    const html = readTemplate('admin/program/list.html');
    assert.match(html, /AdminFetch\.adminFetch\('\/api\/admin\/programs\?' \+ buildQuery\(\)\)/);
});

test('templates/admin/program/list.html links to the new-program screen', () => {
    const html = readTemplate('admin/program/list.html');
    assert.match(html, /href="\/admin\/programs\/new"/);
});

// P14-T9D: 수정 링크는 현재 목록 state(filter + keyword + page)를 싣는다(query 없는 수정 링크 계약을 대체).
test('templates/admin/program/list.html links each row to its edit screen with the current list state', () => {
    const html = readTemplate('admin/program/list.html');
    assert.match(html, /editLink\.href = AdminListState\.editHref\(AdminListState\.PROGRAM_LIST, program\.id, state\);/);
});

// P14-T9D: 등록 링크는 서버 fallback(/admin/programs/new)을 두고, 조회마다 filter + keyword만(page 제외) 실어 재작성한다.
test('templates/admin/program/list.html rewrites the new-program link with filter + keyword only on every load', () => {
    const html = readTemplate('admin/program/list.html').replace(/\r\n/g, '\n');
    assert.match(html, /<a id="admin-list-new-link" href="\/admin\/programs\/new" class="btn btn-primary">새 프로그램 등록<\/a>/);
    const body = html.slice(html.indexOf('function loadPrograms(fallbackDone) {'), html.indexOf('AdminFetch.adminFetch(\'/api/admin/programs?\''));
    assert.match(body, /document\.getElementById\('admin-list-new-link'\)\.href = AdminListState\.newHref\(AdminListState\.PROGRAM_LIST, state\);/);
});

test('templates/admin/program/list.html wires the delete action to DELETE /api/admin/programs/{id}', () => {
    const html = readTemplate('admin/program/list.html');
    assert.match(html, /AdminFetch\.adminFetch\('\/api\/admin\/programs\/' \+ program\.id, \{method: 'DELETE'\}\)/);
});

test('templates/admin/program/list.html wires the visibility toggle to PATCH .../visibility', () => {
    const html = readTemplate('admin/program/list.html');
    assert.match(html, /'\/api\/admin\/programs\/' \+ program\.id \+ '\/visibility'/);
    assert.match(html, /isPublic: !program\.isPublic/);
});

test('templates/admin/program/list.html wires the status toggle to PATCH .../status', () => {
    const html = readTemplate('admin/program/list.html');
    assert.match(html, /'\/api\/admin\/programs\/' \+ program\.id \+ '\/status'/);
    assert.match(html, /recruitStatus: nextStatus/);
});

// P13-T31: programType select는 검색 버튼 없이 변경 즉시 재조회한다(page를 0으로 초기화). keyword는
// 이 change 핸들러에서 다시 읽지 않는다 - 검색어 실행은 여전히 기존 submit 핸들러(검색 버튼)를 통해서만
// 커밋되는 계약을 그대로 유지한다.
test('templates/admin/program/list.html #searchProgramType change listener resets page and re-fetches with the new programType, without re-reading keyword', () => {
    const html = readTemplate('admin/program/list.html');
    const changeHandlerIndex = html.indexOf("document.getElementById('searchProgramType').addEventListener('change'");
    assert.notEqual(changeHandlerIndex, -1, 'searchProgramType must have a change listener');

    const handlerEnd = html.indexOf('});', changeHandlerIndex);
    const handlerBody = html.slice(changeHandlerIndex, handlerEnd);

    assert.match(handlerBody, /state\.page = 0;/);
    assert.match(handlerBody, /state\.programType = document\.getElementById\('searchProgramType'\)\.value;/);
    assert.match(handlerBody, /loadPrograms\(\);/);
    // keyword는 change 핸들러 안에서 다시 읽지 않는다(§keyword 정책) - state.keyword 대입이 없어야 한다.
    assert.doesNotMatch(handlerBody, /state\.keyword/);
});

// 기존 submit 핸들러(검색 버튼/Enter)는 그대로 programType과 keyword를 함께 커밋한다 - P13-T31이
// 이 계약을 바꾸지 않았는지 확인한다.
test('templates/admin/program/list.html existing search form submit still commits both programType and keyword together', () => {
    const html = readTemplate('admin/program/list.html');
    const submitHandlerIndex = html.indexOf("document.getElementById('searchForm').addEventListener('submit'");
    const changeHandlerIndex = html.indexOf("document.getElementById('searchProgramType').addEventListener('change'");
    assert.notEqual(submitHandlerIndex, -1);
    assert.ok(submitHandlerIndex < changeHandlerIndex, 'submit handler must remain defined before the new change listener');

    const submitHandlerBody = html.slice(submitHandlerIndex, changeHandlerIndex);
    assert.match(submitHandlerBody, /state\.programType = document\.getElementById\('searchProgramType'\)\.value;/);
    assert.match(submitHandlerBody, /state\.keyword = document\.getElementById\('searchKeyword'\)\.value;/);
});

test('templates/admin/program/form.html inherits the common admin layout', () => {
    const html = readTemplate('admin/program/form.html');
    assert.match(html, /th:replace="~\{admin\/layout\/default :: layout\(/);
});

test('templates/admin/program/form.html parses the editing program id from the URL path, not from a model attribute', () => {
    const html = readTemplate('admin/program/form.html');
    assert.match(html, /window\.location\.pathname\.match\(/);
    assert.match(html, /function extractProgramIdFromPath/);
});

test('templates/admin/program/form.html waits for both the CKEditor instance and the fetched program before prefilling', () => {
    const html = readTemplate('admin/program/form.html');
    const editorReadyIndex = html.indexOf('var editorReady =');
    const promiseAllIndex = html.indexOf('Promise.all([');
    const setDataIndex = html.indexOf('editor.setData(program.content');

    assert.notEqual(editorReadyIndex, -1);
    assert.notEqual(promiseAllIndex, -1);
    assert.notEqual(setDataIndex, -1);
    assert.ok(editorReadyIndex < promiseAllIndex, 'editorReady must be defined before Promise.all waits on it');
    assert.ok(promiseAllIndex < setDataIndex, 'editor.setData must run only after Promise.all resolves');
    assert.match(html, /Promise\.all\(\[[\s\S]*?editorReady[\s\S]*?\]\)/);
});

// P14-T9D: 저장 후에는 여전히 목록으로 가되, form URL에서 검증한 목록 state(listHref)로 돌아간다.
test('templates/admin/program/form.html redirects to the list screen (with the validated list state) after a successful save', () => {
    const html = readTemplate('admin/program/form.html');
    assert.match(html, /window\.location\.href = listHref;/);
    assert.match(html, /var listHref = AdminListState\.listHref\(AdminListState\.PROGRAM_LIST, listState\);/);
});

test('templates/admin/program/form.html displays an error message when the save request fails', () => {
    const html = readTemplate('admin/program/form.html');
    assert.match(html, /errorMessage\.textContent = /);
    assert.match(html, /errorMessage\.style\.display = 'block'/);
});

// P13-T18: 기존 thumbnail/attachment가 있으면 미리보기가 보이고, 신규 등록 화면(값 없음)에서는
// hidden 속성으로 기본 숨김 상태다.
test('templates/admin/program/form.html loads the shared admin-file-preview.js helper', () => {
    const html = readTemplate('admin/program/form.html');
    assert.match(html, /\/js\/admin\/admin-file-preview\.js/);
});

test('templates/admin/program/form.html preview containers are hidden by default (new-program screen has no existing files)', () => {
    const html = readTemplate('admin/program/form.html');
    assert.match(html, /<div id="thumbnailPreview" class="mb-2" hidden>/);
    assert.match(html, /<div id="attachmentPreview" class="mb-2" hidden>/);
});

// P13-T41: 대표 이미지는 목록/홈/HomePinnedContent 카드 전용 메타데이터이며 상세 본문에는 더 이상
// 자동으로 표시되지 않는다. 기존 "썸네일 이미지" 라벨을 Board와 동일한 "대표 이미지"로 통일하고,
// 관리자가 이 역할 차이를 오해하지 않도록 라벨 아래에 짧은 안내를 둔다.
test('templates/admin/program/form.html labels the thumbnail field "대표 이미지" (unified with Board) and explains it is not auto-inserted into the detail body', () => {
    const html = readTemplate('admin/program/form.html');
    assert.match(html, /<label for="thumbnailInput" class="form-label">대표 이미지<\/label>/);
    assert.doesNotMatch(html, /썸네일 이미지/);
    assert.match(html, /<div class="form-text">목록·메인 카드 등에 표시되는 이미지입니다\. 상세 본문에는 자동으로 표시되지 않습니다\.<\/div>/);
});

test('templates/admin/program/form.html renders the existing thumbnail/attachment preview when prefilling an edited program', () => {
    const html = readTemplate('admin/program/form.html');
    assert.match(html, /AdminFilePreview\.renderImagePreview\(\s*document\.querySelector\('#thumbnailPreview'\),\s*document\.querySelector\('#thumbnailPreviewImage'\),\s*document\.querySelector\('#thumbnailPreviewLink'\),\s*program\.thumbnail\);/);
    assert.match(html, /AdminFilePreview\.renderLinkPreview\(\s*document\.querySelector\('#attachmentPreview'\),\s*document\.querySelector\('#attachmentPreviewLink'\),\s*program\.attachment\);/);
});

test('templates/admin/program/form.html refreshes the preview immediately after a new thumbnail/attachment upload succeeds', () => {
    const html = readTemplate('admin/program/form.html');
    const thumbnailHandlerIndex = html.indexOf("#thumbnailInput').addEventListener('change'");
    const thumbnailPreviewCallIndex = html.indexOf('AdminFilePreview.renderImagePreview', thumbnailHandlerIndex);
    const attachmentHandlerIndex = html.indexOf("#attachmentInput').addEventListener('change'");
    const attachmentPreviewCallIndex = html.indexOf('AdminFilePreview.renderLinkPreview', attachmentHandlerIndex);

    assert.notEqual(thumbnailHandlerIndex, -1);
    assert.notEqual(attachmentHandlerIndex, -1);
    assert.ok(thumbnailPreviewCallIndex > thumbnailHandlerIndex,
        'thumbnail change handler must call renderImagePreview after a successful upload');
    assert.ok(attachmentPreviewCallIndex > attachmentHandlerIndex,
        'attachment change handler must call renderLinkPreview after a successful upload');
});

test('templates/admin/program/form.html attachment link has no target="_blank" (server already responds with Content-Disposition: attachment)', () => {
    const html = readTemplate('admin/program/form.html');
    assert.match(html, /<a id="attachmentPreviewLink" href="#">열기\/다운로드<\/a>/);
});

// P13-T22: 기존 attachment의 원본 파일명을 GET /api/admin/files/{id}로 조회해 표시한다.
// nameWrap은 조회 성공 전까지/실패 시 hidden으로 남아 "현재 첨부파일: (열기/다운로드)"처럼
// 이름이 빈 채로 보이지 않는다.
test('templates/admin/program/form.html has a hidden-by-default attachment name wrap next to the download link', () => {
    const html = readTemplate('admin/program/form.html');
    assert.match(html, /<span id="attachmentPreviewNameWrap" hidden>현재 첨부파일: <span id="attachmentPreviewName"><\/span> <\/span>/);
});

test('templates/admin/program/form.html loads the attachment original name when prefilling an edited program', () => {
    const html = readTemplate('admin/program/form.html');
    assert.match(html, /AdminFilePreview\.loadAttachmentName\(\s*document\.querySelector\('#attachmentPreviewNameWrap'\),\s*document\.querySelector\('#attachmentPreviewName'\),\s*program\.attachment,\s*AdminFetch\.adminFetch\);/);
});

test('templates/admin/program/form.html reloads the attachment original name after a new attachment upload succeeds', () => {
    const html = readTemplate('admin/program/form.html');
    const attachmentHandlerIndex = html.indexOf("#attachmentInput').addEventListener('change'");
    const loadNameCallIndex = html.indexOf('AdminFilePreview.loadAttachmentName', attachmentHandlerIndex);

    assert.notEqual(attachmentHandlerIndex, -1);
    assert.ok(loadNameCallIndex > attachmentHandlerIndex,
        'attachment change handler must call loadAttachmentName after a successful upload');
});

test('templates/admin/program/form.html thumbnail "open in new tab" link uses target="_blank" with rel="noopener noreferrer"', () => {
    const html = readTemplate('admin/program/form.html');
    assert.match(html, /<a id="thumbnailPreviewLink" href="#" target="_blank" rel="noopener noreferrer">/);
});

// P13-T26: thumbnail/attachment는 Program이 가진 URL 참조만 비우는 "제거"(detach) 버튼을 갖는다.
// File 레코드/실제 파일 삭제(DELETE /api/admin/files/{id})와는 무관하며, 버튼은 각 preview
// container 내부에 위치해 container가 hidden될 때 함께 사라진다(별도 가시성 로직 불필요).
test('templates/admin/program/form.html has a "제거" button inside the thumbnail preview container (so it hides together with the preview)', () => {
    const html = readTemplate('admin/program/form.html');
    const previewStart = html.indexOf('<div id="thumbnailPreview"');
    const previewEnd = html.indexOf('<input type="file" class="form-control" id="thumbnailInput"');
    const buttonIndex = html.indexOf('<button type="button" id="thumbnailRemoveButton" class="btn btn-outline-secondary btn-sm">제거</button>');

    assert.notEqual(buttonIndex, -1);
    assert.ok(buttonIndex > previewStart && buttonIndex < previewEnd,
        'remove button must be nested inside the thumbnail preview container');
});

test('templates/admin/program/form.html has a "제거" button inside the attachment preview container (so it hides together with the preview)', () => {
    const html = readTemplate('admin/program/form.html');
    const previewStart = html.indexOf('<div id="attachmentPreview"');
    const previewEnd = html.indexOf('<input type="file" class="form-control" id="attachmentInput">');
    const buttonIndex = html.indexOf('<button type="button" id="attachmentRemoveButton" class="btn btn-outline-secondary btn-sm">제거</button>');

    assert.notEqual(buttonIndex, -1);
    assert.ok(buttonIndex > previewStart && buttonIndex < previewEnd,
        'remove button must be nested inside the attachment preview container');
});

test('templates/admin/program/form.html thumbnail remove button clears the hidden input and hides the preview via the existing renderImagePreview(\'\') path', () => {
    const html = readTemplate('admin/program/form.html');
    assert.match(html, /document\.querySelector\('#thumbnailRemoveButton'\)\.addEventListener\('click', function \(\) \{\s*document\.querySelector\('#thumbnail'\)\.value = '';\s*AdminFilePreview\.renderImagePreview\(\s*document\.querySelector\('#thumbnailPreview'\),\s*document\.querySelector\('#thumbnailPreviewImage'\),\s*document\.querySelector\('#thumbnailPreviewLink'\),\s*''\);\s*\}\);/);
});

test('templates/admin/program/form.html attachment remove button clears the hidden input and hides the preview via the existing renderLinkPreview(\'\')/loadAttachmentName(\'\') path', () => {
    const html = readTemplate('admin/program/form.html');
    assert.match(html, /document\.querySelector\('#attachmentRemoveButton'\)\.addEventListener\('click', function \(\) \{\s*document\.querySelector\('#attachment'\)\.value = '';\s*AdminFilePreview\.renderLinkPreview\(\s*document\.querySelector\('#attachmentPreview'\),\s*document\.querySelector\('#attachmentPreviewLink'\),\s*''\);\s*AdminFilePreview\.loadAttachmentName\(\s*document\.querySelector\('#attachmentPreviewNameWrap'\),\s*document\.querySelector\('#attachmentPreviewName'\),\s*'',\s*AdminFetch\.adminFetch\);\s*\}\);/);
});

test('templates/admin/program/form.html never references the physical file DELETE endpoint (제거 is a detach-only action)', () => {
    const html = readTemplate('admin/program/form.html');
    assert.doesNotMatch(html, /DELETE/);
    assert.doesNotMatch(html, /\/api\/admin\/files/);
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
    "template": "admin/program/list.html",
    "loadFn": "loadPrograms",
    "columnCountVar": "COLUMN_COUNT",
    "columnCount": 5,
    "firstTheadColumns": 5,
    "tableCount": 1,
    "emptyMessageExpr": "emptyMessage()",
    "errorMessage": "프로그램 목록을 불러오지 못했습니다.",
    "deleteSuccess": "삭제되었습니다.",
    "deleteErrorVar": "DELETE_ERROR",
    "patchActions": [
        {
            "marker": "visibilityButton.addEventListener('click'",
            "errorVar": "STATUS_CHANGE_ERROR"
        },
        {
            "marker": "statusButton.addEventListener('click'",
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

// P14-T9C-1: 유형은 neutral, 모집 상태는 모집중(positive)/마감(muted), 공개 여부는 state badge.
test('templates/admin/program/list.html renders programType/recruitStatus/visibility as admin display badges', () => {
    const html = readTemplate('admin/program/list.html');
    assert.match(html, /typeTd\.appendChild\(AdminDisplay\.createBadge\(document,\s*AdminDisplay\.label\(AdminDisplay\.PROGRAM_TYPE_LABELS, program\.programType\), 'neutral'\)\);/);
    assert.match(html, /statusTd\.appendChild\(AdminDisplay\.createBadge\(document,\s*AdminDisplay\.label\(AdminDisplay\.RECRUIT_STATUS_LABELS, program\.recruitStatus\),\s*AdminDisplay\.recruitStatusTone\(program\.recruitStatus\)\)\);/);
    assert.match(html, /publicTd\.appendChild\(AdminDisplay\.createStateBadge\(document, program\.isPublic, '공개', '비공개'\)\);/);
    assert.doesNotMatch(html, /typeTd\.textContent = program\.programType/);
    assert.doesNotMatch(html, /statusTd\.textContent = program\.recruitStatus/);
});

// P14-T9C-2: T9C-1 시점의 "return query 없음/URL state 없음" 경계는 T9C-2 계약(allowlist된 상세 query, replaceState)으로
// 대체됐다 - 제목은 계속 textContent 링크이고, pushState/popstate는 여전히 쓰지 않는다.
test('templates/admin/program/list.html keeps the title detail link (list state via AdminListState only) and distinguishes a filtered empty result', () => {
    const html = readTemplate('admin/program/list.html');
    assert.match(html, /titleLink\.href = AdminListState\.detailHref\(AdminListState\.PROGRAM_LIST, program\.id, state\);/);
    assert.match(html, /titleLink\.textContent = program\.title;/);
    assert.doesNotMatch(html, /pushState|popstate/);
    assert.match(html, /return state\.programType \|\| state\.keyword \? '조건에 맞는 프로그램이 없습니다\.' : '등록된 프로그램이 없습니다\.';/);
    assert.match(html, /<nav id="pagination" class="d-flex gap-2" aria-label="페이지 이동">/);
});

// ---------------------------------------------------------------------------------------------------------------
// P14-T9C-2: Admin List State & Navigation 계약(URL = 목록 state, replaceState만, race guard, pagination fallback).
// ---------------------------------------------------------------------------------------------------------------
const T9C2 = {
    "template": "admin/program/list.html",
    "config": "PROGRAM_LIST",
    "loadFn": "loadPrograms",
    "restores": [
        "state.programType = initialState.programType;",
        "state.keyword = initialState.keyword;",
        "state.page = initialState.page;",
        "document.getElementById('searchProgramType').value = state.programType;",
        "document.getElementById('searchKeyword').value = state.keyword;"
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

test('templates/admin/program/list.html links each title to the admin detail with the allowlisted list state', () => {
    const source = t9c2Source();
    assert.match(source, /titleLink\.href = AdminListState\.detailHref\(AdminListState\.PROGRAM_LIST, program\.id, state\);/);
    assert.match(source, /titleLink\.textContent = program\.title;/);
    assert.doesNotMatch(source, /'\/admin\/programs\/' \+ program\.id \+ '\?/);
});

// P14-T9C-2: 상세 "목록으로"는 상세 URL query를 allowlist 검증·정규화해서만 재작성한다(서버 fallback 유지, 수정/공개 링크 무변경).
// P14-T9D: "목록으로"에 더해 "수정"도 같은 검증된 목록 state를 싣는다(T9C-2의 "수정 링크 query 없음" 계약을 대체).
// 서버 fallback(목록/수정 경로)은 그대로이고, 공개 페이지 링크에는 목록 state를 붙이지 않는다.
test('templates/admin/program/detail.html rewrites the list and edit links from the validated list state (not the public link)', () => {
    const source = readTemplate('admin/program/detail.html').replace(/\r\n/g, '\n');
    assert.match(source, /<a id="admin-detail-list-link" href="\/admin\/programs" class="btn btn-outline-secondary">목록으로<\/a>/);
    assert.match(source, /<a id="admin-detail-edit-link" th:href="@\{\/admin\/programs\/\{id\}\/edit\(id=\$\{program\.id\(\)\}\)\}"/);
    const helperIndex = source.indexOf('<script src="/js/admin/admin-list-state.js"></script>');
    assert.ok(helperIndex !== -1 && helperIndex < source.indexOf('<script>'));
    assert.match(source, /var listState = AdminListState\.readState\(AdminListState\.PROGRAM_LIST, window\.location\.search\);\s*document\.getElementById\('admin-detail-list-link'\)\.href = AdminListState\.listHref\(AdminListState\.PROGRAM_LIST, listState\);/);
    assert.match(source, /document\.getElementById\('admin-detail-edit-link'\)\.href =\s*AdminListState\.editHref\(AdminListState\.PROGRAM_LIST, idMatch\[1\], listState\);/);
    assert.equal((source.match(/window\.location\.search/g) || []).length, 1, 'the raw query is only ever parsed, never copied');
    assert.doesNotMatch(source, /admin-detail-public-link'\)\.href|replaceState|pushState/);
});

// ---------------------------------------------------------------------------------------------------------------
// P14-T9D: Admin Form Composition 공통 계약(.admin-form 800px, 필수 표시, role=alert 오류 + focus, [취소][저장],
// 저장 중 중복 submit 방지). form마다 같은 형태로 검증한다.
// ---------------------------------------------------------------------------------------------------------------
const T9D = {
    "template": "admin/program/form.html",
    "contentId": "program-form-content",
    "cancelHref": "/admin/programs",
    "requiredLabels": [
        "프로그램 구분",
        "제목"
    ],
    "createHeading": "프로그램 등록",
    "editHeading": "프로그램 수정",
    "editingVar": "editingProgramId"
};

function t9dSource() {
    return readTemplate(T9D.template).replace(/\r\n/g, '\n');
}

function t9dEscape(text) {
    return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

test(`${T9D.template} uses the shared .admin-form wrapper instead of an inline max-width`, () => {
    const source = t9dSource();
    assert.match(source, new RegExp(`<div id="${T9D.contentId}" class="admin-form">`));
    assert.doesNotMatch(source, /max-width: 800px/);
});

test(`${T9D.template} marks exactly the required fields and explains the marker once`, () => {
    const source = t9dSource();
    assert.match(source, /<p class="admin-form-required-note">\* 표시는 필수 입력입니다\.<\/p>/);
    const markers = source.match(/<span class="admin-required" aria-hidden="true">\*<\/span>/g) || [];
    assert.equal(markers.length, T9D.requiredLabels.length);
    for (const label of T9D.requiredLabels) {
        assert.match(source, new RegExp(`>${t9dEscape(label)}<span class="admin-required" aria-hidden="true">\\*</span></label>`),
            `missing required marker for ${label}`);
    }
});

test(`${T9D.template} shows errors in a focusable role=alert region`, () => {
    const source = t9dSource();
    assert.match(source, /<div id="errorMessage" class="alert alert-danger" role="alert" tabindex="-1" style="display: none;"><\/div>/);
    assert.match(source, /function showError\(message\) \{\s*errorMessage\.textContent = message;\s*errorMessage\.style\.display = 'block';\s*errorMessage\.focus\(\);\s*\}/);
});

test(`${T9D.template} ends with a normal-flow [취소][저장] action area`, () => {
    const source = t9dSource();
    assert.match(source, new RegExp('<div class="admin-form-actions">\\s*'
        + `<a id="admin-form-cancel-link" href="${t9dEscape(T9D.cancelHref)}" class="btn btn-outline-secondary">취소</a>\\s*`
        + '<button type="submit" id="saveButton" class="btn btn-primary">저장</button>\\s*</div>\\s*</form>'));
    assert.equal((source.match(/type="submit"/g) || []).length, 1);
    assert.doesNotMatch(source, /sticky/);
});

test(`${T9D.template} disables the save button while saving and re-enables it only on failure`, () => {
    const source = t9dSource();
    const handler = source.slice(source.indexOf("addEventListener('submit'"));
    const disableIndex = handler.indexOf('saveButton.disabled = true;');
    const requestIndex = handler.indexOf('AdminFetch.adminFetch(');
    assert.ok(disableIndex !== -1 && disableIndex < requestIndex, 'disable right before the save request');
    assert.match(handler, /if \(result\.ok && result\.body\.success\) \{\s*window\.location\.href = [^;]+;\s*\} else \{\s*saveButton\.disabled = false;\s*showError\(/);
    assert.match(handler, /\.catch\(function \(\) \{\s*saveButton\.disabled = false;\s*showError\('저장 중 오류가 발생했습니다\.'\);/);
    assert.equal((handler.match(/saveButton\.disabled = false;/g) || []).length, 2);
});

test(`${T9D.template} switches the heading (and document title) to the edit wording only in edit mode`, () => {
    const source = t9dSource();
    assert.match(source, new RegExp(`<h3 id="admin-form-heading" class="mb-2">${T9D.createHeading}</h3>`));
    assert.match(source, new RegExp(`if \\(${T9D.editingVar}\\) \\{\\s*document\\.querySelector\\('#admin-form-heading'\\)\\.textContent = '${T9D.editHeading}';\\s*\\}\\s*`
        + "document\\.title = document\\.querySelector\\('#admin-form-heading'\\)\\.textContent;"));
});

// P14-T9D: 목록 state 복귀 - form URL query를 AdminListState로 검증·정규화해 취소/저장 목적지를 만든다(returnUrl 없음).
test('templates/admin/program/form.html returns to the validated list state on cancel and save (new → page 0)', () => {
    const source = t9dSource();
    const helperIndex = source.indexOf('<script src="/js/admin/admin-list-state.js"></script>');
    assert.ok(helperIndex !== -1 && helperIndex < source.indexOf('<script>\n'), 'the helper loads before the inline script');
    assert.match(source, /var listState = AdminListState\.readState\(AdminListState\.PROGRAM_LIST, window\.location\.search\);\s*if \(!editingProgramId\) \{\s*listState\.page = 0;\s*\}\s*var listHref = AdminListState\.listHref\(AdminListState\.PROGRAM_LIST, listState\);\s*document\.querySelector\('#admin-form-cancel-link'\)\.href = listHref;/);
    assert.match(source, /window\.location\.href = listHref;/);
    assert.equal((source.match(/window\.location\.search/g) || []).length, 1, 'the raw query is only parsed, never copied');
    assert.doesNotMatch(source, /returnUrl|redirectUrl|nextUrl|replaceState|pushState/);
});

test('templates/admin/program/form.html groups the long form into fieldset sections without reordering fields', () => {
    const source = t9dSource();
    const legends = [...source.matchAll(/<legend class="h5">([^<]+)<\/legend>/g)].map((match) => match[1]);
    assert.deepEqual(legends, ['기본 정보', '본문', '파일', '신청·모집', '공개 설정']);
    let previous = -1;
    for (const id of ['programType', 'title', 'content', 'thumbnailInput', 'attachmentInput', 'googleFormUrl', 'recruitStatus', 'isPublic']) {
        const index = source.indexOf(`id="${id}"`);
        assert.ok(index > previous, `field ${id} keeps its original order`);
        previous = index;
    }
});

test('templates/admin/program/form.html explains the Google Form URL contract (http(s), empty hides the apply button)', () => {
    const source = t9dSource();
    assert.match(source, /<div class="form-text">http:\/\/ 또는 https:\/\/로 시작하는 주소입니다\. 비워두면 공개 화면에 신청 버튼이 표시되지 않습니다\.<\/div>/);
});
