// package.json 없이 Node.js 내장 테스트 러너로 실행한다:
//   node --test src/test/js/admin/page-admin-view.test.js
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

function readTemplate(relativePath) {
    return fs.readFileSync(path.join(__dirname, '../../../main/resources/templates', relativePath), 'utf8');
}

test('templates/admin/page/list.html inherits the common admin layout', () => {
    const html = readTemplate('admin/page/list.html');
    assert.match(html, /th:replace="~\{admin\/layout\/default :: layout\(/);
});

test('templates/admin/page/list.html links to all 4 fixed page type edit screens without calling any list API', () => {
    const html = readTemplate('admin/page/list.html');
    assert.match(html, /href="\/admin\/pages\/GREETING\/edit"/);
    assert.match(html, /href="\/admin\/pages\/INTRODUCTION\/edit"/);
    assert.match(html, /href="\/admin\/pages\/HISTORY\/edit"/);
    assert.match(html, /href="\/admin\/pages\/LOCATION\/edit"/);
    assert.doesNotMatch(html, /AdminFetch\.adminFetch/);
});

test('templates/admin/page/list.html does not link to a new-page screen', () => {
    const html = readTemplate('admin/page/list.html');
    assert.doesNotMatch(html, /\/admin\/pages\/new/);
});

test('templates/admin/page/form.html inherits the common admin layout', () => {
    const html = readTemplate('admin/page/form.html');
    assert.match(html, /th:replace="~\{admin\/layout\/default :: layout\(/);
});

test('templates/admin/page/form.html parses the editing page type from the URL path', () => {
    const html = readTemplate('admin/page/form.html');
    assert.match(html, /window\.location\.pathname\.match\(/);
    assert.match(html, /function extractPageTypeFromPath/);
});

test('templates/admin/page/form.html shows an error and does not call the API when the page type cannot be parsed', () => {
    const html = readTemplate('admin/page/form.html');
    const extractIndex = html.indexOf('var editingPageType = extractPageTypeFromPath()');
    const ifIndex = html.indexOf('if (editingPageType) {', extractIndex);
    const elseIndex = html.indexOf('} else {', ifIndex);
    const showErrorIndex = html.indexOf('showError(', elseIndex);

    assert.notEqual(extractIndex, -1);
    assert.notEqual(ifIndex, -1);
    assert.notEqual(elseIndex, -1);
    assert.notEqual(showErrorIndex, -1);
    assert.ok(elseIndex < showErrorIndex, 'the else branch (no page type) must call showError');

    const submitHandlerIndex = html.indexOf("addEventListener('submit'");
    const guardIndex = html.indexOf('if (!editingPageType) {', submitHandlerIndex);
    assert.notEqual(guardIndex, -1, 'submit handler must guard against a missing page type before calling the API');
});

test('templates/admin/page/form.html waits for both the CKEditor instance and the fetched page before prefilling', () => {
    const html = readTemplate('admin/page/form.html');
    const editorReadyIndex = html.indexOf('var editorReady =');
    const promiseAllIndex = html.indexOf('Promise.all([');
    const setDataIndex = html.indexOf('editor.setData(page.content');

    assert.notEqual(editorReadyIndex, -1);
    assert.notEqual(promiseAllIndex, -1);
    assert.notEqual(setDataIndex, -1);
    assert.ok(editorReadyIndex < promiseAllIndex, 'editorReady must be defined before Promise.all waits on it');
    assert.ok(promiseAllIndex < setDataIndex, 'editor.setData must run only after Promise.all resolves');
    assert.match(html, /Promise\.all\(\[[\s\S]*?editorReady[\s\S]*?\]\)/);
});

test('templates/admin/page/form.html always saves via PUT /api/admin/pages/{pageType}, never POST', () => {
    const html = readTemplate('admin/page/form.html');
    assert.match(html, /method: 'PUT'/);
    assert.doesNotMatch(html, /method: 'POST'/);
    assert.match(html, /'\/api\/admin\/pages\/' \+ editingPageType/);
});

test('templates/admin/page/form.html redirects to the page list screen after a successful save', () => {
    const html = readTemplate('admin/page/form.html');
    assert.match(html, /window\.location\.href = '\/admin\/pages'/);
});

test('templates/admin/page/form.html displays an error message when the save request fails', () => {
    const html = readTemplate('admin/page/form.html');
    assert.match(html, /function showError/);
    assert.match(html, /errorMessage\.style\.display = 'block'/);
});

test('templates/admin/page/form.html has no thumbnail/attachment/isPublic fields (Page has no such API fields)', () => {
    const html = readTemplate('admin/page/form.html');
    assert.doesNotMatch(html, /thumbnail/i);
    assert.doesNotMatch(html, /attachment/i);
    assert.doesNotMatch(html, /isPublic/);
});
