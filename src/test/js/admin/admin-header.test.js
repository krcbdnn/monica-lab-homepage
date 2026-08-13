// package.json 없이 Node.js 내장 테스트 러너로 실행한다:
//   node --test src/test/js/admin/admin-header.test.js
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const {
    ME_URL,
    NAME_ELEMENT_ID,
    applyAdminName,
    bootstrap
} = require('../../../main/resources/static/js/admin/admin-header.js');

test('ME_URL targets the admin me endpoint', () => {
    assert.equal(ME_URL, '/api/admin/me');
});

test('NAME_ELEMENT_ID targets the admin-name DOM element', () => {
    assert.equal(NAME_ELEMENT_ID, 'admin-name');
});

test('applyAdminName sets textContent to the admin name from the response body', () => {
    const nameElement = { textContent: '' };
    applyAdminName(nameElement, {
        success: true,
        data: { id: 1, loginId: 'admin', name: '관리자', role: 'ROLE_ADMIN' },
        error: null
    });
    assert.equal(nameElement.textContent, '관리자');
});

test('applyAdminName does nothing when the name element is missing', () => {
    assert.doesNotThrow(() => applyAdminName(null, { data: { name: '관리자' } }));
});

test('applyAdminName does nothing when the response body has no data', () => {
    const nameElement = { textContent: 'placeholder' };
    applyAdminName(nameElement, { success: false, data: null, error: {} });
    assert.equal(nameElement.textContent, 'placeholder');
});

test('bootstrap fetches ME_URL and applies the admin name to the #admin-name element', async () => {
    const nameElement = { textContent: '' };
    const doc = { getElementById: (id) => (id === NAME_ELEMENT_ID ? nameElement : null) };
    let calledWith = null;
    const adminFetch = (url) => {
        calledWith = url;
        return Promise.resolve({ json: () => Promise.resolve({ data: { name: '관리자' } }) });
    };

    await bootstrap(adminFetch, doc);

    assert.equal(calledWith, ME_URL);
    assert.equal(nameElement.textContent, '관리자');
});
