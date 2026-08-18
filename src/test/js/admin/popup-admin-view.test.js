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
