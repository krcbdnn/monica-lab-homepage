// package.json 없이 Node.js 내장 테스트 러너로 실행한다:
//   node --test src/test/js/admin/admin-file-preview.test.js
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const {
    normalizeUrl,
    renderImagePreview,
    renderLinkPreview,
    extractFileIdFromUrl,
    loadAttachmentName
} = require('../../../main/resources/static/js/admin/admin-file-preview.js');

function createElementStub() {
    const attrs = {};
    return {
        hidden: false,
        attrs: attrs,
        setAttribute: function (name, value) {
            attrs[name] = value;
        },
        removeAttribute: function (name) {
            delete attrs[name];
        }
    };
}

function createTextElementStub() {
    return {hidden: false, textContent: ''};
}

function fakeAdminFetch(response) {
    return function () {
        return Promise.resolve(response);
    };
}

function rejectingAdminFetch(error) {
    return function () {
        return Promise.reject(error);
    };
}

test('normalizeUrl trims a string url', () => {
    assert.equal(normalizeUrl('  /api/files/1  '), '/api/files/1');
});

test('normalizeUrl treats null/undefined/non-string as empty', () => {
    assert.equal(normalizeUrl(null), '');
    assert.equal(normalizeUrl(undefined), '');
    assert.equal(normalizeUrl(42), '');
});

test('normalizeUrl treats an empty or whitespace-only string as empty', () => {
    assert.equal(normalizeUrl(''), '');
    assert.equal(normalizeUrl('   '), '');
    assert.equal(normalizeUrl('\t\n '), '');
});

test('renderImagePreview shows the container and sets src/href when a url exists', () => {
    const container = createElementStub();
    const img = createElementStub();
    const link = createElementStub();

    renderImagePreview(container, img, link, '/api/files/1');

    assert.equal(container.hidden, false);
    assert.equal(img.attrs.src, '/api/files/1');
    assert.equal(link.attrs.href, '/api/files/1');
});

test('renderImagePreview trims whitespace before using the url', () => {
    const container = createElementStub();
    const img = createElementStub();
    const link = createElementStub();

    renderImagePreview(container, img, link, '  /api/files/2  ');

    assert.equal(img.attrs.src, '/api/files/2');
    assert.equal(link.attrs.href, '/api/files/2');
});

test('renderImagePreview hides the container and removes src/href for null', () => {
    const container = createElementStub();
    const img = createElementStub();
    const link = createElementStub();
    img.setAttribute('src', '/api/files/old');
    link.setAttribute('href', '/api/files/old');

    renderImagePreview(container, img, link, null);

    assert.equal(container.hidden, true);
    assert.equal('src' in img.attrs, false);
    assert.equal('href' in link.attrs, false);
});

test('renderImagePreview hides the container for an empty string', () => {
    const container = createElementStub();
    const img = createElementStub();
    const link = createElementStub();

    renderImagePreview(container, img, link, '');

    assert.equal(container.hidden, true);
});

test('renderImagePreview hides the container for a whitespace-only string', () => {
    const container = createElementStub();
    const img = createElementStub();
    const link = createElementStub();

    renderImagePreview(container, img, link, '   ');

    assert.equal(container.hidden, true);
    assert.equal('src' in img.attrs, false);
    assert.equal('href' in link.attrs, false);
});

test('renderLinkPreview shows the container and sets href when a url exists', () => {
    const container = createElementStub();
    const link = createElementStub();

    renderLinkPreview(container, link, '/api/files/9');

    assert.equal(container.hidden, false);
    assert.equal(link.attrs.href, '/api/files/9');
});

test('renderLinkPreview hides the container and removes href for undefined', () => {
    const container = createElementStub();
    const link = createElementStub();
    link.setAttribute('href', '/api/files/old');

    renderLinkPreview(container, link, undefined);

    assert.equal(container.hidden, true);
    assert.equal('href' in link.attrs, false);
});

test('renderLinkPreview hides the container for a whitespace-only string', () => {
    const container = createElementStub();
    const link = createElementStub();

    renderLinkPreview(container, link, '  \t  ');

    assert.equal(container.hidden, true);
    assert.equal('href' in link.attrs, false);
});

test('extractFileIdFromUrl extracts the numeric id from a well-formed /api/files/{id} url', () => {
    assert.equal(extractFileIdFromUrl('/api/files/42'), '42');
});

test('extractFileIdFromUrl trims whitespace before matching', () => {
    assert.equal(extractFileIdFromUrl('  /api/files/7  '), '7');
});

test('extractFileIdFromUrl returns null for a non-matching path, empty, or null/undefined', () => {
    assert.equal(extractFileIdFromUrl('/other/path'), null);
    assert.equal(extractFileIdFromUrl('/api/files/abc'), null);
    assert.equal(extractFileIdFromUrl(''), null);
    assert.equal(extractFileIdFromUrl(null), null);
    assert.equal(extractFileIdFromUrl(undefined), null);
});

test('loadAttachmentName sets the original name and reveals the wrap on success', async () => {
    const nameWrap = createTextElementStub();
    const name = createTextElementStub();
    const adminFetch = fakeAdminFetch({
        ok: true,
        json: () => Promise.resolve({success: true, data: {originalName: '강의자료.pdf'}})
    });

    await loadAttachmentName(nameWrap, name, '/api/files/42', adminFetch);

    assert.equal(name.textContent, '강의자료.pdf');
    assert.equal(nameWrap.hidden, false);
});

test('loadAttachmentName keeps the wrap hidden without calling fetch when the url does not match /api/files/{id}', async () => {
    const nameWrap = createTextElementStub();
    const name = createTextElementStub();
    let called = false;
    const adminFetch = function () {
        called = true;
        return Promise.resolve({ok: true, json: () => Promise.resolve({})});
    };

    await loadAttachmentName(nameWrap, name, '', adminFetch);

    assert.equal(called, false);
    assert.equal(nameWrap.hidden, true);
    assert.equal(name.textContent, '');
});

test('loadAttachmentName keeps the wrap hidden (not an error text) when the lookup responds not-ok', async () => {
    const nameWrap = createTextElementStub();
    const name = createTextElementStub();
    const adminFetch = fakeAdminFetch({ok: false});

    await loadAttachmentName(nameWrap, name, '/api/files/999', adminFetch);

    assert.equal(nameWrap.hidden, true);
    assert.equal(name.textContent, '');
});

test('loadAttachmentName keeps the wrap hidden when the response has no originalName', async () => {
    const nameWrap = createTextElementStub();
    const name = createTextElementStub();
    const adminFetch = fakeAdminFetch({ok: true, json: () => Promise.resolve({success: true, data: {}})});

    await loadAttachmentName(nameWrap, name, '/api/files/5', adminFetch);

    assert.equal(nameWrap.hidden, true);
    assert.equal(name.textContent, '');
});

test('loadAttachmentName resolves (does not throw) and keeps the wrap hidden when adminFetch rejects', async () => {
    const nameWrap = createTextElementStub();
    const name = createTextElementStub();
    const adminFetch = rejectingAdminFetch(new Error('network error'));

    await assert.doesNotReject(loadAttachmentName(nameWrap, name, '/api/files/5', adminFetch));
    assert.equal(nameWrap.hidden, true);
    assert.equal(name.textContent, '');
});
