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
    assert.match(html, /document\.getElementById\('prev-page'\)\.disabled = data\.page <= 0/);
    assert.match(html, /document\.getElementById\('next-page'\)\.disabled = data\.last/);
});

test('templates/admin/file/list.html links originalName to the existing download URL, without an image thumbnail preview', () => {
    const html = readTemplate('admin/file/list.html');
    assert.match(html, /nameLink\.href = file\.url/);
    assert.match(html, /nameLink\.textContent = file\.originalName/);
    assert.doesNotMatch(html, /<img/);
    assert.doesNotMatch(html, /\.src = file\./);
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

test('templates/admin/file/list.html shows #errorMessage when the delete request fails, following the Banner pattern', () => {
    const html = readTemplate('admin/file/list.html');
    assert.match(html, /id="errorMessage" class="alert alert-danger" style="display: none;"/);
    assert.match(html, /function showError\(message\)/);
    assert.match(html, /errorMessage\.style\.display = 'block'/);
    assert.match(html, /showError\(\(body\.error && body\.error\.message\)/);
});

test('templates/admin/file/list.html has no registration/edit/upload/search UI (not part of the P9-T2g scope)', () => {
    const html = readTemplate('admin/file/list.html');
    assert.doesNotMatch(html, /href="\/admin\/files\/new"/);
    assert.doesNotMatch(html, /\/edit/);
    assert.doesNotMatch(html, /<input[^>]*type="file"/);
    assert.doesNotMatch(html, /searchForm/);
    assert.doesNotMatch(html, /keyword/i);
});
