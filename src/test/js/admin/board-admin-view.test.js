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

test('templates/admin/board/list.html displays the read-only view count column', () => {
    const html = readTemplate('admin/board/list.html');
    assert.match(html, /viewCountTd\.textContent = board\.viewCount/);
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
