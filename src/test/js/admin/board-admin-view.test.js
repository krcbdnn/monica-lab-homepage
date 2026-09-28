// package.json 없이 Node.js 내장 테스트 러너로 실행한다:
//   node --test src/test/js/admin/board-admin-view.test.js
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

function readTemplate(relativePath) {
    return fs.readFileSync(path.join(__dirname, '../../../main/resources/templates', relativePath), 'utf8');
}

test('templates/admin/board/list.html inherits the common admin layout', () => {
    const html = readTemplate('admin/board/list.html');
    assert.match(html, /th:replace="~\{admin\/layout\/default :: layout\(/);
});

test('templates/admin/board/list.html loads boards from the admin board API via common fetch', () => {
    const html = readTemplate('admin/board/list.html');
    assert.match(html, /AdminFetch\.adminFetch\('\/api\/admin\/boards\?' \+ buildQuery\(\)\)/);
});

test('templates/admin/board/list.html links to the new-board screen', () => {
    const html = readTemplate('admin/board/list.html');
    assert.match(html, /href="\/admin\/boards\/new"/);
});

test('templates/admin/board/list.html links each row to its edit screen', () => {
    const html = readTemplate('admin/board/list.html');
    assert.match(html, /editLink\.href = '\/admin\/boards\/' \+ board\.id \+ '\/edit'/);
});

test('templates/admin/board/list.html wires the delete action to DELETE /api/admin/boards/{id}', () => {
    const html = readTemplate('admin/board/list.html');
    assert.match(html, /AdminFetch\.adminFetch\('\/api\/admin\/boards\/' \+ board\.id, \{method: 'DELETE'\}\)/);
});

test('templates/admin/board/list.html wires the visibility toggle to PATCH .../visibility', () => {
    const html = readTemplate('admin/board/list.html');
    assert.match(html, /'\/api\/admin\/boards\/' \+ board\.id \+ '\/visibility'/);
    assert.match(html, /isPublic: !board\.isPublic/);
});

test('templates/admin/board/list.html does not include a recruit-status style toggle (Board has no status API)', () => {
    const html = readTemplate('admin/board/list.html');
    assert.doesNotMatch(html, /\/status/);
    assert.doesNotMatch(html, /recruitStatus/);
});

// P13-T19: 조회수 기능 완전 제거. 관리자 목록의 조회수 컬럼(헤더/셀 생성 로직)과
// viewCount 참조가 더 이상 존재하지 않는지 확인한다.
test('templates/admin/board/list.html no longer displays a view count column or references viewCount', () => {
    const html = readTemplate('admin/board/list.html');
    assert.doesNotMatch(html, /<th>조회수<\/th>/);
    assert.doesNotMatch(html, /viewCountTd/);
    assert.doesNotMatch(html, /viewCount/);
});

// P13-T31: boardType select는 검색 버튼 없이 변경 즉시 재조회한다(page를 0으로 초기화). keyword는
// 이 change 핸들러에서 다시 읽지 않는다 - 검색어 실행은 여전히 기존 submit 핸들러(검색 버튼)를 통해서만
// 커밋되는 계약을 그대로 유지한다.
test('templates/admin/board/list.html #searchBoardType change listener resets page and re-fetches with the new boardType, without re-reading keyword', () => {
    const html = readTemplate('admin/board/list.html');
    const changeHandlerIndex = html.indexOf("document.getElementById('searchBoardType').addEventListener('change'");
    assert.notEqual(changeHandlerIndex, -1, 'searchBoardType must have a change listener');

    const handlerEnd = html.indexOf('});', changeHandlerIndex);
    const handlerBody = html.slice(changeHandlerIndex, handlerEnd);

    assert.match(handlerBody, /state\.page = 0;/);
    assert.match(handlerBody, /state\.boardType = document\.getElementById\('searchBoardType'\)\.value;/);
    assert.match(handlerBody, /loadBoards\(\);/);
    // keyword는 change 핸들러 안에서 다시 읽지 않는다(§keyword 정책) - state.keyword 대입이 없어야 한다.
    assert.doesNotMatch(handlerBody, /state\.keyword/);
});

// 기존 submit 핸들러(검색 버튼/Enter)는 그대로 boardType과 keyword를 함께 커밋한다 - P13-T31이
// 이 계약을 바꾸지 않았는지 확인한다.
test('templates/admin/board/list.html existing search form submit still commits both boardType and keyword together', () => {
    const html = readTemplate('admin/board/list.html');
    const submitHandlerIndex = html.indexOf("document.getElementById('searchForm').addEventListener('submit'");
    const changeHandlerIndex = html.indexOf("document.getElementById('searchBoardType').addEventListener('change'");
    assert.notEqual(submitHandlerIndex, -1);
    assert.ok(submitHandlerIndex < changeHandlerIndex, 'submit handler must remain defined before the new change listener');

    const submitHandlerBody = html.slice(submitHandlerIndex, changeHandlerIndex);
    assert.match(submitHandlerBody, /state\.boardType = document\.getElementById\('searchBoardType'\)\.value;/);
    assert.match(submitHandlerBody, /state\.keyword = document\.getElementById\('searchKeyword'\)\.value;/);
});

test('templates/admin/board/form.html inherits the common admin layout', () => {
    const html = readTemplate('admin/board/form.html');
    assert.match(html, /th:replace="~\{admin\/layout\/default :: layout\(/);
});

test('templates/admin/board/form.html parses the editing board id from the URL path, not from a model attribute', () => {
    const html = readTemplate('admin/board/form.html');
    assert.match(html, /window\.location\.pathname\.match\(/);
    assert.match(html, /function extractBoardIdFromPath/);
});

test('templates/admin/board/form.html waits for both the CKEditor instance and the fetched board before prefilling', () => {
    const html = readTemplate('admin/board/form.html');
    const editorReadyIndex = html.indexOf('var editorReady =');
    const promiseAllIndex = html.indexOf('Promise.all([');
    const setDataIndex = html.indexOf('editor.setData(board.content');

    assert.notEqual(editorReadyIndex, -1);
    assert.notEqual(promiseAllIndex, -1);
    assert.notEqual(setDataIndex, -1);
    assert.ok(editorReadyIndex < promiseAllIndex, 'editorReady must be defined before Promise.all waits on it');
    assert.ok(promiseAllIndex < setDataIndex, 'editor.setData must run only after Promise.all resolves');
    assert.match(html, /Promise\.all\(\[[\s\S]*?editorReady[\s\S]*?\]\)/);
});

test('templates/admin/board/form.html redirects to the list screen after a successful save', () => {
    const html = readTemplate('admin/board/form.html');
    assert.match(html, /window\.location\.href = '\/admin\/boards'/);
});

test('templates/admin/board/form.html displays an error message when the save request fails', () => {
    const html = readTemplate('admin/board/form.html');
    assert.match(html, /errorMessage\.textContent = /);
    assert.match(html, /errorMessage\.style\.display = 'block'/);
});

test('templates/admin/board/form.html still wires the existing thumbnail/attachment upload handlers', () => {
    const html = readTemplate('admin/board/form.html');
    assert.match(html, /AdminBoardFileUpload\.uploadFile\(file, AdminBoardFileUpload\.THUMBNAIL_FILE_TYPE, AdminFetch\.adminFetch\)/);
    assert.match(html, /AdminBoardFileUpload\.uploadFile\(file, AdminBoardFileUpload\.ATTACHMENT_FILE_TYPE, AdminFetch\.adminFetch\)/);
});

test('templates/admin/board/form.html still wires the CKEditor upload adapter', () => {
    const html = readTemplate('admin/board/form.html');
    assert.match(html, /AdminCkeditorUploadAdapter\.installUploadAdapterPlugin\(editor, AdminFetch\.adminFetch\)/);
});

// P13-T18: 기존 thumbnail/attachment가 있으면 미리보기가 보이고, 신규 등록 화면(값 없음)에서는
// hidden 속성으로 기본 숨김 상태다.
test('templates/admin/board/form.html loads the shared admin-file-preview.js helper', () => {
    const html = readTemplate('admin/board/form.html');
    assert.match(html, /\/js\/admin\/admin-file-preview\.js/);
});

test('templates/admin/board/form.html preview containers are hidden by default (new-board screen has no existing files)', () => {
    const html = readTemplate('admin/board/form.html');
    assert.match(html, /<div id="thumbnailPreview" class="mb-2" hidden>/);
    assert.match(html, /<div id="attachmentPreview" class="mb-2" hidden>/);
});

// P13-T41: 대표 이미지는 목록/홈/HomePinnedContent 카드 전용 메타데이터이며 상세 본문에는 더 이상
// 자동으로 표시되지 않는다 - 관리자가 이 역할 차이를 오해하지 않도록 라벨 아래에 짧은 안내를 둔다.
test('templates/admin/board/form.html labels the thumbnail field "대표 이미지" and explains it is not auto-inserted into the detail body', () => {
    const html = readTemplate('admin/board/form.html');
    assert.match(html, /<label for="thumbnailInput" class="form-label">대표 이미지<\/label>/);
    assert.match(html, /<div class="form-text">목록·메인 카드 등에 표시되는 이미지입니다\. 상세 본문에는 자동으로 표시되지 않습니다\.<\/div>/);
});

test('templates/admin/board/form.html renders the existing thumbnail/attachment preview when prefilling an edited board', () => {
    const html = readTemplate('admin/board/form.html');
    assert.match(html, /AdminFilePreview\.renderImagePreview\(\s*document\.querySelector\('#thumbnailPreview'\),\s*document\.querySelector\('#thumbnailPreviewImage'\),\s*document\.querySelector\('#thumbnailPreviewLink'\),\s*board\.thumbnail\);/);
    assert.match(html, /AdminFilePreview\.renderLinkPreview\(\s*document\.querySelector\('#attachmentPreview'\),\s*document\.querySelector\('#attachmentPreviewLink'\),\s*board\.attachment\);/);
});

test('templates/admin/board/form.html refreshes the preview immediately after a new thumbnail/attachment upload succeeds', () => {
    const html = readTemplate('admin/board/form.html');
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

test('templates/admin/board/form.html attachment link has no target="_blank" (server already responds with Content-Disposition: attachment)', () => {
    const html = readTemplate('admin/board/form.html');
    assert.match(html, /<a id="attachmentPreviewLink" href="#">열기\/다운로드<\/a>/);
});

// P13-T22: 기존 attachment의 원본 파일명을 GET /api/admin/files/{id}로 조회해 표시한다.
// nameWrap은 조회 성공 전까지/실패 시 hidden으로 남아 "현재 첨부파일: (열기/다운로드)"처럼
// 이름이 빈 채로 보이지 않는다.
test('templates/admin/board/form.html has a hidden-by-default attachment name wrap next to the download link', () => {
    const html = readTemplate('admin/board/form.html');
    assert.match(html, /<span id="attachmentPreviewNameWrap" hidden>현재 첨부파일: <span id="attachmentPreviewName"><\/span> <\/span>/);
});

test('templates/admin/board/form.html loads the attachment original name when prefilling an edited board', () => {
    const html = readTemplate('admin/board/form.html');
    assert.match(html, /AdminFilePreview\.loadAttachmentName\(\s*document\.querySelector\('#attachmentPreviewNameWrap'\),\s*document\.querySelector\('#attachmentPreviewName'\),\s*board\.attachment,\s*AdminFetch\.adminFetch\);/);
});

test('templates/admin/board/form.html reloads the attachment original name after a new attachment upload succeeds', () => {
    const html = readTemplate('admin/board/form.html');
    const attachmentHandlerIndex = html.indexOf("#attachmentInput').addEventListener('change'");
    const loadNameCallIndex = html.indexOf('AdminFilePreview.loadAttachmentName', attachmentHandlerIndex);

    assert.notEqual(attachmentHandlerIndex, -1);
    assert.ok(loadNameCallIndex > attachmentHandlerIndex,
        'attachment change handler must call loadAttachmentName after a successful upload');
});

test('templates/admin/board/form.html thumbnail "open in new tab" link uses target="_blank" with rel="noopener noreferrer"', () => {
    const html = readTemplate('admin/board/form.html');
    assert.match(html, /<a id="thumbnailPreviewLink" href="#" target="_blank" rel="noopener noreferrer">/);
});

// P13-T26: thumbnail/attachment는 Board가 가진 URL 참조만 비우는 "제거"(detach) 버튼을 갖는다.
// File 레코드/실제 파일 삭제(DELETE /api/admin/files/{id})와는 무관하며, 버튼은 각 preview
// container 내부에 위치해 container가 hidden될 때 함께 사라진다(별도 가시성 로직 불필요).
test('templates/admin/board/form.html has a "제거" button inside the thumbnail preview container (so it hides together with the preview)', () => {
    const html = readTemplate('admin/board/form.html');
    const previewStart = html.indexOf('<div id="thumbnailPreview"');
    const previewEnd = html.indexOf('<input type="file" class="form-control" id="thumbnailInput"');
    const buttonIndex = html.indexOf('<button type="button" id="thumbnailRemoveButton" class="btn btn-outline-secondary btn-sm">제거</button>');

    assert.notEqual(buttonIndex, -1);
    assert.ok(buttonIndex > previewStart && buttonIndex < previewEnd,
        'remove button must be nested inside the thumbnail preview container');
});

test('templates/admin/board/form.html has a "제거" button inside the attachment preview container (so it hides together with the preview)', () => {
    const html = readTemplate('admin/board/form.html');
    const previewStart = html.indexOf('<div id="attachmentPreview"');
    const previewEnd = html.indexOf('<input type="file" class="form-control" id="attachmentInput">');
    const buttonIndex = html.indexOf('<button type="button" id="attachmentRemoveButton" class="btn btn-outline-secondary btn-sm">제거</button>');

    assert.notEqual(buttonIndex, -1);
    assert.ok(buttonIndex > previewStart && buttonIndex < previewEnd,
        'remove button must be nested inside the attachment preview container');
});

test('templates/admin/board/form.html thumbnail remove button clears the hidden input and hides the preview via the existing renderImagePreview(\'\') path', () => {
    const html = readTemplate('admin/board/form.html');
    assert.match(html, /document\.querySelector\('#thumbnailRemoveButton'\)\.addEventListener\('click', function \(\) \{\s*document\.querySelector\('#thumbnail'\)\.value = '';\s*AdminFilePreview\.renderImagePreview\(\s*document\.querySelector\('#thumbnailPreview'\),\s*document\.querySelector\('#thumbnailPreviewImage'\),\s*document\.querySelector\('#thumbnailPreviewLink'\),\s*''\);\s*\}\);/);
});

test('templates/admin/board/form.html attachment remove button clears the hidden input and hides the preview via the existing renderLinkPreview(\'\')/loadAttachmentName(\'\') path', () => {
    const html = readTemplate('admin/board/form.html');
    assert.match(html, /document\.querySelector\('#attachmentRemoveButton'\)\.addEventListener\('click', function \(\) \{\s*document\.querySelector\('#attachment'\)\.value = '';\s*AdminFilePreview\.renderLinkPreview\(\s*document\.querySelector\('#attachmentPreview'\),\s*document\.querySelector\('#attachmentPreviewLink'\),\s*''\);\s*AdminFilePreview\.loadAttachmentName\(\s*document\.querySelector\('#attachmentPreviewNameWrap'\),\s*document\.querySelector\('#attachmentPreviewName'\),\s*'',\s*AdminFetch\.adminFetch\);\s*\}\);/);
});

test('templates/admin/board/form.html never references the physical file DELETE endpoint (제거 is a detach-only action)', () => {
    const html = readTemplate('admin/board/form.html');
    assert.doesNotMatch(html, /DELETE/);
    assert.doesNotMatch(html, /\/api\/admin\/files/);
});

// P13-T30D(Task C): REVIEW subtype(programType) UI.
test('templates/admin/board/form.html programType field is hidden by default and offers an empty placeholder first (no implicit COURSE default)', () => {
    const html = readTemplate('admin/board/form.html');
    assert.match(html, /<div class="mb-3" id="programTypeField" hidden>/);
    const fieldIndex = html.indexOf('id="programTypeField"');
    const selectIndex = html.indexOf('<select class="form-select" id="programType" name="programType">', fieldIndex);
    const placeholderIndex = html.indexOf('<option value="">', selectIndex);
    const courseOptionIndex = html.indexOf('<option value="COURSE">', selectIndex);
    const specialOptionIndex = html.indexOf('<option value="SPECIAL">', selectIndex);

    assert.notEqual(selectIndex, -1);
    assert.ok(placeholderIndex !== -1 && placeholderIndex < courseOptionIndex,
        'a value="" placeholder option must come before the COURSE option so the browser never implicitly defaults to COURSE');
    assert.ok(courseOptionIndex < specialOptionIndex);
});

test('templates/admin/board/form.html toggles programType field visibility on boardType change and clears the value when hidden', () => {
    const html = readTemplate('admin/board/form.html');
    assert.match(html, /function updateProgramTypeVisibility\(\)/);
    assert.match(html, /document\.querySelector\('#boardType'\)\.value === 'REVIEW'/);
    assert.match(html, /document\.querySelector\('#programTypeField'\)\.hidden = !isReview/);
    assert.match(html, /document\.querySelector\('#programType'\)\.value = '';/);
    assert.match(html, /document\.querySelector\('#boardType'\)\.addEventListener\('change', updateProgramTypeVisibility\)/);
});

test('templates/admin/board/form.html prefills programType from the fetched board without defaulting legacy NULL to COURSE', () => {
    const html = readTemplate('admin/board/form.html');
    assert.match(html, /document\.querySelector\('#programType'\)\.value = board\.programType \|\| '';/);
    // updateProgramTypeVisibility() must run AFTER the value is set, and must not itself write 'COURSE'.
    const prefillIndex = html.indexOf("document.querySelector('#programType').value = board.programType");
    const visibilityCallAfterPrefillIndex = html.indexOf('updateProgramTypeVisibility();', prefillIndex);
    assert.notEqual(visibilityCallAfterPrefillIndex, -1);
    assert.doesNotMatch(html, /#programType'\)\.value = 'COURSE'/);
});

test('templates/admin/board/form.html blocks submission when boardType is REVIEW and programType is still the empty placeholder', () => {
    const html = readTemplate('admin/board/form.html');
    const submitHandlerIndex = html.indexOf("#boardForm').addEventListener('submit'");
    const guardIndex = html.indexOf("boardTypeValue === 'REVIEW' && !programTypeValue", submitHandlerIndex);
    const returnAfterGuardIndex = html.indexOf('return;', guardIndex);
    const payloadIndex = html.indexOf('var payload = {', submitHandlerIndex);

    assert.notEqual(submitHandlerIndex, -1);
    assert.notEqual(guardIndex, -1, 'submit handler must check boardType===REVIEW && empty programType');
    assert.ok(returnAfterGuardIndex !== -1 && returnAfterGuardIndex < payloadIndex,
        'the guard must return before building/sending the payload');
});

test('templates/admin/board/form.html payload normalizes programType to null for every boardType except REVIEW', () => {
    const html = readTemplate('admin/board/form.html');
    assert.match(html, /programType: boardTypeValue === 'REVIEW' \? programTypeValue : null/);
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
    "template": "admin/board/list.html",
    "loadFn": "loadBoards",
    "columnCountVar": "COLUMN_COUNT",
    "columnCount": 4,
    "firstTheadColumns": 4,
    "tableCount": 1,
    "emptyMessageExpr": "emptyMessage()",
    "errorMessage": "게시글 목록을 불러오지 못했습니다.",
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

// P14-T9C-1: 구분은 관리자 공통 표시명 neutral badge(REVIEW는 강의 후기(정규 강좌)/강의 후기(특강)), 공개 여부는 state badge.
test('templates/admin/board/list.html renders boardType (with REVIEW subtype) as a neutral badge and visibility as a state badge', () => {
    const html = readTemplate('admin/board/list.html');
    assert.match(html, /typeTd\.appendChild\(AdminDisplay\.createBadge\(document,\s*AdminDisplay\.boardTypeLabel\(board\.boardType, board\.programType\), 'neutral'\)\);/);
    assert.match(html, /publicTd\.appendChild\(AdminDisplay\.createStateBadge\(document, board\.isPublic, '공개', '비공개'\)\);/);
    assert.doesNotMatch(html, /reviewSubtypeLabel/);
    assert.doesNotMatch(html, /수강 후기|특강 후기|미지정/);
    assert.doesNotMatch(html, /typeTd\.textContent = board\.boardType/);
});

test('templates/admin/board/list.html keeps the title as a textContent link to the admin detail without a return query', () => {
    const html = readTemplate('admin/board/list.html');
    assert.match(html, /titleLink\.href = '\/admin\/boards\/' \+ board\.id;/);
    assert.match(html, /titleLink\.textContent = board\.title;/);
    // P14-T9C-1은 URL/list state를 다루지 않는다(T9C-2).
    assert.doesNotMatch(html, /location\.search|replaceState|pushState|popstate/);
});

test('templates/admin/board/list.html distinguishes a filtered empty result from an empty board list', () => {
    const html = readTemplate('admin/board/list.html');
    assert.match(html, /return state\.boardType \|\| state\.keyword \? '조건에 맞는 게시글이 없습니다\.' : '등록된 게시글이 없습니다\.';/);
    assert.match(html, /<nav id="pagination" class="d-flex gap-2" aria-label="페이지 이동">/);
});
