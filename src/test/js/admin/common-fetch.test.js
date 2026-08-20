// package.json 없이 Node.js 내장 테스트 러너로 실행한다(P11-T0 이전이라 npm 프로젝트 없음):
//   node --test src/test/js/admin/common-fetch.test.js
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const {
    parseCookie,
    buildHeaders,
    CSRF_HEADER_NAME
} = require('../../../main/resources/static/js/admin/common-fetch.js');

test('parseCookie extracts the XSRF-TOKEN value from a cookie header string', () => {
    const cookieHeader = 'JSESSIONID=abc123; XSRF-TOKEN=my-csrf-token; other=value';
    assert.equal(parseCookie(cookieHeader, 'XSRF-TOKEN'), 'my-csrf-token');
});

test('parseCookie returns null when the cookie is absent', () => {
    assert.equal(parseCookie('JSESSIONID=abc123', 'XSRF-TOKEN'), null);
});

test('parseCookie returns null for an empty cookie header', () => {
    assert.equal(parseCookie('', 'XSRF-TOKEN'), null);
});

test('parseCookie url-decodes the cookie value', () => {
    const cookieHeader = 'XSRF-TOKEN=abc%2Fdef%3D';
    assert.equal(parseCookie(cookieHeader, 'XSRF-TOKEN'), 'abc/def=');
});

test('buildHeaders attaches X-XSRF-TOKEN for POST/PUT/PATCH/DELETE regardless of case', () => {
    for (const method of ['POST', 'PUT', 'PATCH', 'DELETE', 'post', 'put', 'patch', 'delete']) {
        const headers = buildHeaders(method, {}, 'token-value');
        assert.equal(headers[CSRF_HEADER_NAME], 'token-value');
    }
});

test('buildHeaders does not attach X-XSRF-TOKEN for GET/HEAD/OPTIONS', () => {
    for (const method of ['GET', 'HEAD', 'OPTIONS']) {
        const headers = buildHeaders(method, {}, 'token-value');
        assert.equal(headers[CSRF_HEADER_NAME], undefined);
    }
});

test('buildHeaders does not attach the header when no CSRF token is available', () => {
    const headers = buildHeaders('POST', {}, null);
    assert.equal(headers[CSRF_HEADER_NAME], undefined);
});

test('buildHeaders preserves existing headers', () => {
    const headers = buildHeaders('POST', { 'Content-Type': 'application/json' }, 'token-value');
    assert.equal(headers['Content-Type'], 'application/json');
    assert.equal(headers[CSRF_HEADER_NAME], 'token-value');
});
