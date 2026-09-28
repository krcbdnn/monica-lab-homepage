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

// ---------------------------------------------------------------------------------------------------------------
// P14-T9D: Admin Form Composition 공통 계약(.admin-form 800px, 필수 표시, role=alert 오류 + focus, [취소][저장],
// 저장 중 중복 submit 방지). form마다 같은 형태로 검증한다.
// ---------------------------------------------------------------------------------------------------------------
const T9D = {
    "template": "admin/page/form.html",
    "contentId": "page-form-content",
    "cancelHref": "/admin/pages",
    "requiredLabels": [
        "제목"
    ]
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

test('templates/admin/page/form.html keeps its edit-only heading', () => {
    const source = t9dSource();
    assert.match(source, /<h3 class="mb-2">기관소개 페이지 편집<\/h3>/);
    assert.doesNotMatch(source, /admin-form-heading/);
});
