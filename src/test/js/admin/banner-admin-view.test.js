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
