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

test('templates/admin/program/list.html links each row to its edit screen', () => {
    const html = readTemplate('admin/program/list.html');
    assert.match(html, /editLink\.href = '\/admin\/programs\/' \+ program\.id \+ '\/edit'/);
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

test('templates/admin/program/form.html redirects to the list screen after a successful save', () => {
    const html = readTemplate('admin/program/form.html');
    assert.match(html, /window\.location\.href = '\/admin\/programs'/);
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
    assert.match(html, /<a id="attachmentPreviewLink" href="#">현재 등록된 첨부파일 다운로드<\/a>/);
});

test('templates/admin/program/form.html thumbnail "open in new tab" link uses target="_blank" with rel="noopener noreferrer"', () => {
    const html = readTemplate('admin/program/form.html');
    assert.match(html, /<a id="thumbnailPreviewLink" href="#" target="_blank" rel="noopener noreferrer">/);
});
