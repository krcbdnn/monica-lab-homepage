// package.json 없이 Node.js 내장 테스트 러너로 실행한다:
//   node --test src/test/js/admin/banner-admin-view.test.js
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

function readTemplate(relativePath) {
    return fs.readFileSync(path.join(__dirname, '../../../main/resources/templates', relativePath), 'utf8');
}

test('templates/admin/banner/list.html inherits the common admin layout', () => {
    const html = readTemplate('admin/banner/list.html');
    assert.match(html, /th:replace="~\{admin\/layout\/default :: layout\(/);
});

test('templates/admin/banner/list.html loads banners from the admin banner API via common fetch as a plain array (no pagination)', () => {
    const html = readTemplate('admin/banner/list.html');
    assert.match(html, /AdminFetch\.adminFetch\('\/api\/admin\/banners'\)/);
    assert.match(html, /var banners = body\.data;/);
    assert.doesNotMatch(html, /buildQuery/);
    assert.doesNotMatch(html, /data\.content/);
    assert.doesNotMatch(html, /data\.last/);
});

test('templates/admin/banner/list.html links to the new-banner screen', () => {
    const html = readTemplate('admin/banner/list.html');
    assert.match(html, /href="\/admin\/banners\/new"/);
});

test('templates/admin/banner/list.html links each row to its edit screen', () => {
    const html = readTemplate('admin/banner/list.html');
    assert.match(html, /editLink\.href = '\/admin\/banners\/' \+ banner\.id \+ '\/edit'/);
});

test('templates/admin/banner/list.html wires the delete action to DELETE /api/admin/banners/{id}', () => {
    const html = readTemplate('admin/banner/list.html');
    assert.match(html, /AdminFetch\.adminFetch\('\/api\/admin\/banners\/' \+ banner\.id, \{method: 'DELETE'\}\)/);
});

test('templates/admin/banner/list.html wires the visibility toggle to PATCH .../visibility', () => {
    const html = readTemplate('admin/banner/list.html');
    assert.match(html, /'\/api\/admin\/banners\/' \+ banner\.id \+ '\/visibility'/);
    assert.match(html, /isVisible: !banner\.isVisible/);
});

test('templates/admin/banner/list.html displays an image thumbnail per row', () => {
    const html = readTemplate('admin/banner/list.html');
    assert.match(html, /img\.src = banner\.image/);
});

test('templates/admin/banner/list.html provides a sortOrder number input with min="0" per row', () => {
    const html = readTemplate('admin/banner/list.html');
    assert.match(html, /sortOrderInput\.type = 'number'/);
    assert.match(html, /sortOrderInput\.min = '0'/);
    assert.match(html, /sortOrderInput\.value = banner\.sortOrder/);
});

test('templates/admin/banner/list.html sends the user-entered sortOrder value as-is to PATCH /order', () => {
    const html = readTemplate('admin/banner/list.html');
    assert.match(html, /'\/api\/admin\/banners\/' \+ banner\.id \+ '\/order'/);
    assert.match(html, /method: 'PATCH'/);
    assert.match(html, /sortOrder: Number\(sortOrderInput\.value\)/);
});

test('templates/admin/banner/list.html reloads the list after a successful order change', () => {
    const html = readTemplate('admin/banner/list.html');
    const orderButtonIndex = html.indexOf("orderButton.addEventListener('click'");
    const loadBannersCallIndex = html.indexOf('loadBanners();', orderButtonIndex);

    assert.notEqual(orderButtonIndex, -1);
    assert.notEqual(loadBannersCallIndex, -1, 'the order-change handler must call loadBanners() again on success');
});

test('templates/admin/banner/list.html does not implement +/-1 nudge buttons or drag-and-drop reordering', () => {
    const html = readTemplate('admin/banner/list.html');
    assert.doesNotMatch(html, /sortOrder\s*[+-]\s*1/);
    assert.doesNotMatch(html, /sortOrder\s*[+-]=\s*1/);
    assert.doesNotMatch(html, /draggable/i);
    assert.doesNotMatch(html, /dragstart/i);
    assert.doesNotMatch(html, /위로|아래로/);
});

test('templates/admin/banner/form.html inherits the common admin layout', () => {
    const html = readTemplate('admin/banner/form.html');
    assert.match(html, /th:replace="~\{admin\/layout\/default :: layout\(/);
});

test('templates/admin/banner/form.html parses the editing banner id from the URL path, not from a model attribute', () => {
    const html = readTemplate('admin/banner/form.html');
    assert.match(html, /window\.location\.pathname\.match\(/);
    assert.match(html, /function extractBannerIdFromPath/);
});

test('templates/admin/banner/form.html prefills fields directly from the fetched banner without waiting on a CKEditor instance', () => {
    const html = readTemplate('admin/banner/form.html');
    assert.doesNotMatch(html, /ClassicEditor/);
    assert.doesNotMatch(html, /editorReady/);
    assert.doesNotMatch(html, /ckeditor/i);
    assert.match(html, /document\.querySelector\('#title'\)\.value = banner\.title/);
});

test('templates/admin/banner/form.html branches between POST and PUT based on the presence of a banner id', () => {
    const html = readTemplate('admin/banner/form.html');
    assert.match(html, /var method = bannerId \? 'PUT' : 'POST';/);
});

test('templates/admin/banner/form.html redirects to the list screen after a successful save', () => {
    const html = readTemplate('admin/banner/form.html');
    assert.match(html, /window\.location\.href = '\/admin\/banners'/);
});

test('templates/admin/banner/form.html displays an error message when the save request fails', () => {
    const html = readTemplate('admin/banner/form.html');
    assert.match(html, /function showError/);
    assert.match(html, /errorMessage\.style\.display = 'block'/);
});

test('templates/admin/banner/form.html still wires the existing image upload handler', () => {
    const html = readTemplate('admin/banner/form.html');
    assert.match(html, /\/js\/admin\/common-fetch\.js/);
    assert.match(html, /\/js\/admin\/banner-file-upload\.js/);
    assert.match(html, /AdminBannerFileUpload\.uploadFile\(file, AdminBannerFileUpload\.IMAGE_FILE_TYPE, AdminFetch\.adminFetch\)/);
});

// P13-T24: P13-T18에서 Board/Program에 도입한 AdminFilePreview.renderImagePreview 패턴을
// Banner에도 동일하게 적용한다(신규 등록 화면에서는 hidden, 기존 image가 있으면 미리보기 표시).
test('templates/admin/banner/form.html loads the shared admin-file-preview.js helper', () => {
    const html = readTemplate('admin/banner/form.html');
    assert.match(html, /\/js\/admin\/admin-file-preview\.js/);
});

test('templates/admin/banner/form.html preview container is hidden by default (new-banner screen has no existing image)', () => {
    const html = readTemplate('admin/banner/form.html');
    assert.match(html, /<div id="imagePreview" class="mb-2" hidden>/);
});

test('templates/admin/banner/form.html renders the existing image preview when prefilling an edited banner', () => {
    const html = readTemplate('admin/banner/form.html');
    assert.match(html, /AdminFilePreview\.renderImagePreview\(\s*document\.querySelector\('#imagePreview'\),\s*document\.querySelector\('#imagePreviewImage'\),\s*document\.querySelector\('#imagePreviewLink'\),\s*banner\.image\);/);
});

test('templates/admin/banner/form.html refreshes the preview immediately after a new image upload succeeds', () => {
    const html = readTemplate('admin/banner/form.html');
    const uploadHandlerIndex = html.indexOf("#imageInput').addEventListener('change'");
    const previewCallIndex = html.indexOf('AdminFilePreview.renderImagePreview', uploadHandlerIndex);

    assert.notEqual(uploadHandlerIndex, -1);
    assert.ok(previewCallIndex > uploadHandlerIndex,
        'image change handler must call renderImagePreview after a successful upload');
});

test('templates/admin/banner/form.html "open in new tab" link uses target="_blank" with rel="noopener noreferrer"', () => {
    const html = readTemplate('admin/banner/form.html');
    assert.match(html, /<a id="imagePreviewLink" href="#" target="_blank" rel="noopener noreferrer">/);
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
    "template": "admin/banner/list.html",
    "loadFn": "loadBanners",
    "columnCountVar": "COLUMN_COUNT",
    "columnCount": 5,
    "firstTheadColumns": 5,
    "tableCount": 1,
    "emptyMessageExpr": "'등록된 배너가 없습니다.'",
    "errorMessage": "배너 목록을 불러오지 못했습니다.",
    "deleteSuccess": "삭제되었습니다.",
    "deleteErrorVar": "DELETE_ERROR",
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

// P14-T9C-1: 노출 여부는 state badge(노출 positive / 비노출 muted).
test('templates/admin/banner/list.html renders visibility as a positive/muted state badge', () => {
    const html = readTemplate('admin/banner/list.html');
    assert.match(html, /visibleTd\.appendChild\(AdminDisplay\.createStateBadge\(document, banner\.isVisible, '노출', '비노출'\)\);/);
    assert.doesNotMatch(html, /visibleTd\.textContent/);
});

// ---------------------------------------------------------------------------------------------------------------
// P14-T9D: Admin Form Composition 공통 계약(.admin-form 800px, 필수 표시, role=alert 오류 + focus, [취소][저장],
// 저장 중 중복 submit 방지). form마다 같은 형태로 검증한다.
// ---------------------------------------------------------------------------------------------------------------
const T9D = {
    "template": "admin/banner/form.html",
    "contentId": "banner-form-content",
    "cancelHref": "/admin/banners",
    "requiredLabels": [
        "제목",
        "배너 이미지",
        "정렬 순서"
    ],
    "createHeading": "배너 등록",
    "editHeading": "배너 수정",
    "editingVar": "editingBannerId"
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

// P14-T9D: 배너 이미지는 서버에서 필수(@NotBlank)라 저장 전에 확인하고 요청을 보내지 않는다.
test('templates/admin/banner/form.html blocks saving without an image before any request', () => {
    const source = t9dSource();
    const handler = source.slice(source.indexOf("addEventListener('submit'"));
    const guardIndex = handler.indexOf("if (!document.querySelector('#image').value) {");
    assert.notEqual(guardIndex, -1);
    assert.match(handler, /if \(!document\.querySelector\('#image'\)\.value\) \{\s*showError\('배너 이미지를 등록해야 합니다\.'\);\s*return;\s*\}/);
    assert.ok(guardIndex < handler.indexOf('saveButton.disabled = true;'), 'the guard runs before the button is disabled');
    assert.ok(guardIndex < handler.indexOf('AdminFetch.adminFetch('), 'the guard runs before any request');
});
