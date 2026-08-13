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
