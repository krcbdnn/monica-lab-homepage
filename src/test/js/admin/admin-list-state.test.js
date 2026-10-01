// package.json 없이 Node.js 내장 테스트 러너로 실행한다:
//   node --test src/test/js/admin/admin-list-state.test.js
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const AdminListState = require('../../../main/resources/static/js/admin/admin-list-state.js');
const AdminDisplay = require('../../../main/resources/static/js/admin/admin-display.js');

const {BOARD_LIST, PROGRAM_LIST, FILE_LIST} = AdminListState;

function javaEnumValues(relativePath) {
    const source = fs.readFileSync(path.join(__dirname, '../../../main/java/com/monicalab', relativePath), 'utf8');
    const body = source.slice(source.indexOf('{') + 1, source.lastIndexOf('}'));
    return body.split(',').map((value) => value.trim()).filter(Boolean);
}

function fakeWindow(pathname, search) {
    const calls = [];
    return {
        calls,
        location: {pathname, search},
        history: {
            state: {kept: true},
            replaceState(state, title, url) {
                calls.push({state, title, url});
            }
        }
    };
}

test('configs fix the list base paths and are frozen', () => {
    assert.equal(BOARD_LIST.path, '/admin/boards');
    assert.equal(PROGRAM_LIST.path, '/admin/programs');
    assert.equal(FILE_LIST.path, '/admin/files');
    for (const config of [BOARD_LIST, PROGRAM_LIST, FILE_LIST]) {
        assert.ok(Object.isFrozen(config));
        assert.ok(Object.isFrozen(config.filterValues));
    }
    assert.equal(FILE_LIST.filterName, null);
    assert.equal(FILE_LIST.hasKeyword, false);
});

test('Board/Program allowlists match the Java enums and the AdminDisplay labels', () => {
    assert.deepEqual([...BOARD_LIST.filterValues], javaEnumValues('board/entity/BoardType.java'));
    assert.deepEqual([...PROGRAM_LIST.filterValues], javaEnumValues('program/entity/ProgramType.java'));
    assert.deepEqual([...BOARD_LIST.filterValues], Object.keys(AdminDisplay.BOARD_TYPE_LABELS));
    assert.deepEqual([...PROGRAM_LIST.filterValues], Object.keys(AdminDisplay.PROGRAM_TYPE_LABELS));
});

test('readState returns the default state for an empty or missing query', () => {
    assert.deepEqual(AdminListState.readState(BOARD_LIST, ''), {boardType: '', keyword: '', page: 0});
    assert.deepEqual(AdminListState.readState(PROGRAM_LIST, undefined), {programType: '', keyword: '', page: 0});
    assert.deepEqual(AdminListState.readState(FILE_LIST, '?'), {page: 0});
});

test('readState accepts allowlisted BoardType/ProgramType values only', () => {
    for (const boardType of ['NOTICE', 'GALLERY', 'ARCHIVE', 'REVIEW']) {
        assert.equal(AdminListState.readState(BOARD_LIST, `?boardType=${boardType}`).boardType, boardType);
    }
    for (const invalid of ['INVALID', 'notice', 'COURSE', '<script>', '']) {
        assert.equal(AdminListState.readState(BOARD_LIST, `?boardType=${encodeURIComponent(invalid)}`).boardType, '');
    }
    assert.equal(AdminListState.readState(PROGRAM_LIST, '?programType=COURSE').programType, 'COURSE');
    assert.equal(AdminListState.readState(PROGRAM_LIST, '?programType=SPECIAL').programType, 'SPECIAL');
    assert.equal(AdminListState.readState(PROGRAM_LIST, '?programType=NOTICE').programType, '');
    // 다른 목록의 filter 이름은 읽지 않는다.
    assert.equal(AdminListState.readState(PROGRAM_LIST, '?boardType=NOTICE').programType, '');
});

test('parsePage accepts only non-negative integers up to 9 digits', () => {
    assert.equal(AdminListState.parsePage('0'), 0);
    assert.equal(AdminListState.parsePage('3'), 3);
    assert.equal(AdminListState.parsePage('999999999'), 999999999);
    for (const invalid of ['-1', 'abc', '1.5', '1e3', '1000000000', '99999999999', ' 2', '+2', '', null, undefined, 2]) {
        assert.equal(AdminListState.parsePage(invalid), 0, `page ${String(invalid)}`);
    }
    assert.equal(AdminListState.readState(BOARD_LIST, '?page=-1').page, 0);
    assert.equal(AdminListState.readState(BOARD_LIST, '?page=2').page, 2);
});

test('keyword round-trips Unicode and URL-significant characters through URLSearchParams', () => {
    const keyword = '한글 공백 & a=b ? #x % 100% 😀 <script>alert(1)</script>';
    const search = AdminListState.toSearch(BOARD_LIST, {boardType: '', keyword, page: 0});
    assert.ok(!search.includes('#'), 'the fragment separator must be encoded');
    assert.ok(!search.includes('<'), 'markup-looking text must be encoded as data');
    assert.equal(AdminListState.readState(BOARD_LIST, search).keyword, keyword);
    assert.equal(new URL(AdminListState.listHref(BOARD_LIST, {keyword, page: 0}), 'http://x').searchParams.get('keyword'), keyword);
});

test('whitespace-only keyword is the default (omitted), while a real keyword keeps its surrounding whitespace', () => {
    assert.equal(AdminListState.readState(BOARD_LIST, '?keyword=%20%20').keyword, '');
    assert.equal(AdminListState.toSearch(BOARD_LIST, {boardType: '', keyword: '   ', page: 0}), '');
    assert.equal(AdminListState.readState(BOARD_LIST, '?keyword=%20abc%20').keyword, ' abc ');
    assert.equal(AdminListState.toSearch(BOARD_LIST, {boardType: '', keyword: ' abc ', page: 0}), '?keyword=+abc+');
});

test('toSearch omits defaults and serializes in a fixed order', () => {
    assert.equal(AdminListState.toSearch(BOARD_LIST, {boardType: '', keyword: '', page: 0}), '');
    assert.equal(AdminListState.toSearch(BOARD_LIST, {page: 1, keyword: 'test', boardType: 'NOTICE'}),
        '?boardType=NOTICE&keyword=test&page=1');
    assert.equal(AdminListState.toSearch(PROGRAM_LIST, {page: 2, keyword: 'a', programType: 'COURSE'}),
        '?programType=COURSE&keyword=a&page=2');
    assert.equal(AdminListState.toSearch(FILE_LIST, {page: 3}), '?page=3');
    assert.equal(AdminListState.toSearch(FILE_LIST, {page: 0}), '');
});

test('toSearch never serializes invalid values or fields the config does not own', () => {
    assert.equal(AdminListState.toSearch(BOARD_LIST, {boardType: 'INVALID', keyword: '', page: -1}), '');
    assert.equal(AdminListState.toSearch(BOARD_LIST, {boardType: '', keyword: '', page: 1.5}), '');
    assert.equal(AdminListState.toSearch(FILE_LIST, {boardType: 'NOTICE', keyword: 'x', page: 1}), '?page=1');
});

test('unknown query parameters are dropped by read → serialize canonicalization', () => {
    const state = AdminListState.readState(BOARD_LIST, '?evil=x&boardType=INVALID&page=-1&size=100&returnUrl=https%3A%2F%2Fevil.example');
    assert.equal(AdminListState.listHref(BOARD_LIST, state), '/admin/boards');
    const kept = AdminListState.readState(BOARD_LIST, '?page=1&x=1&keyword=k&boardType=GALLERY');
    assert.equal(AdminListState.listHref(BOARD_LIST, kept), '/admin/boards?boardType=GALLERY&keyword=k&page=1');
});

test('listHref/detailHref keep the fixed base path and append only canonical state', () => {
    const state = {boardType: 'NOTICE', keyword: 'a&b', page: 1};
    assert.equal(AdminListState.listHref(BOARD_LIST, state), '/admin/boards?boardType=NOTICE&keyword=a%26b&page=1');
    assert.equal(AdminListState.detailHref(BOARD_LIST, 123, state), '/admin/boards/123?boardType=NOTICE&keyword=a%26b&page=1');
    assert.equal(AdminListState.detailHref(PROGRAM_LIST, 7, {programType: '', keyword: '', page: 0}), '/admin/programs/7');
    // id는 경로 segment로 encode되어 다른 경로/스킴을 만들 수 없다.
    assert.equal(AdminListState.detailHref(BOARD_LIST, '../x?y', {}), '/admin/boards/..%2Fx%3Fy');
    assert.ok(!AdminListState.listHref(BOARD_LIST, {keyword: 'javascript:alert(1)'}).startsWith('javascript:'));
});

// P14-T9D: 수정 링크는 filter + keyword + page를 모두, 등록 링크는 filter + keyword만(page 없음) 싣는다.
test('editHref keeps filter, keyword and page and omits the query for the default state', () => {
    assert.equal(AdminListState.editHref(BOARD_LIST, 123, {boardType: '', keyword: '', page: 0}), '/admin/boards/123/edit');
    assert.equal(AdminListState.editHref(BOARD_LIST, 123, {boardType: 'NOTICE', keyword: 'test', page: 1}),
        '/admin/boards/123/edit?boardType=NOTICE&keyword=test&page=1');
    assert.equal(AdminListState.editHref(PROGRAM_LIST, 7, {programType: 'COURSE', keyword: 'a&b #한글', page: 2}),
        '/admin/programs/7/edit?programType=COURSE&keyword=a%26b+%23%ED%95%9C%EA%B8%80&page=2');
    assert.equal(AdminListState.editHref(PROGRAM_LIST, 7, {programType: '', keyword: '', page: 0}), '/admin/programs/7/edit');
    assert.equal(AdminListState.editHref(BOARD_LIST, '../x', {}), '/admin/boards/..%2Fx/edit');
});

test('editHref never carries invalid or unknown values from a canonicalized state', () => {
    const state = AdminListState.readState(BOARD_LIST, '?boardType=INVALID&page=-1&returnUrl=https%3A%2F%2Fevil.example&keyword=k');
    assert.equal(AdminListState.editHref(BOARD_LIST, 5, state), '/admin/boards/5/edit?keyword=k');
});

test('newHref keeps filter and keyword but always drops page', () => {
    assert.equal(AdminListState.newHref(BOARD_LIST, {boardType: '', keyword: '', page: 0}), '/admin/boards/new');
    assert.equal(AdminListState.newHref(BOARD_LIST, {boardType: 'NOTICE', keyword: 'test', page: 3}),
        '/admin/boards/new?boardType=NOTICE&keyword=test');
    assert.equal(AdminListState.newHref(PROGRAM_LIST, {programType: 'SPECIAL', keyword: '공백 &', page: 9}),
        '/admin/programs/new?programType=SPECIAL&keyword=%EA%B3%B5%EB%B0%B1+%26');
    assert.equal(AdminListState.newHref(PROGRAM_LIST, {programType: '', keyword: '', page: 5}), '/admin/programs/new');
    for (const href of [
        AdminListState.newHref(BOARD_LIST, {boardType: 'REVIEW', keyword: 'x', page: 1}),
        AdminListState.newHref(PROGRAM_LIST, {programType: 'COURSE', keyword: 'y', page: 42}),
    ]) {
        assert.equal(new URL(href, 'http://x').searchParams.has('page'), false, `${href} must not carry page`);
    }
});

test('newHref drops unknown values from a canonicalized state and does not mutate the input', () => {
    const state = Object.freeze(AdminListState.readState(BOARD_LIST, '?boardType=GALLERY&page=4&x=1&redirectUrl=%2F%2Fevil'));
    assert.equal(AdminListState.newHref(BOARD_LIST, state), '/admin/boards/new?boardType=GALLERY');
    assert.equal(state.page, 4);
});

test('readState/toSearch do not mutate their inputs', () => {
    const state = Object.freeze({boardType: 'NOTICE', keyword: 'k', page: 2, size: 20});
    assert.doesNotThrow(() => AdminListState.toSearch(BOARD_LIST, state));
    assert.doesNotThrow(() => AdminListState.listHref(BOARD_LIST, state));
    assert.doesNotThrow(() => AdminListState.detailHref(BOARD_LIST, 1, state));
});

test('replaceUrl calls history.replaceState only when the canonical URL differs', () => {
    const same = fakeWindow('/admin/boards', '?boardType=NOTICE');
    assert.equal(AdminListState.replaceUrl(same, BOARD_LIST, {boardType: 'NOTICE', keyword: '', page: 0}), false);
    assert.equal(same.calls.length, 0);

    const different = fakeWindow('/admin/boards', '?boardType=INVALID&page=-1');
    assert.equal(AdminListState.replaceUrl(different, BOARD_LIST, {boardType: '', keyword: '', page: 0}), true);
    assert.deepEqual(different.calls, [{state: {kept: true}, title: '', url: '/admin/boards'}]);

    const paged = fakeWindow('/admin/files', '');
    AdminListState.replaceUrl(paged, FILE_LIST, {page: 2});
    assert.equal(paged.calls[0].url, '/admin/files?page=2');
});

test('resolvePage CASE A: data exists but the requested page is out of range → last valid page, reload once', () => {
    const data = {content: [], page: 9999, totalElements: 21, totalPages: 2};
    assert.deepEqual(AdminListState.resolvePage(9999, data, true), {page: 1, reload: true});
    // 마지막 행 삭제 후: page 1이 비었고 전체 20건(1 page) 남음 → page 0 재조회.
    assert.deepEqual(AdminListState.resolvePage(1, {content: [], page: 1, totalElements: 20, totalPages: 1}, true),
        {page: 0, reload: true});
    // 이미 1회 fallback했다면 다시 reload하지 않는다(무한 재조회 방지).
    assert.deepEqual(AdminListState.resolvePage(1, {content: [], page: 1, totalElements: 20, totalPages: 1}, false),
        {page: 1, reload: false});
});

test('resolvePage CASE B: nothing exists at all while page > 0 → page 0 without another request', () => {
    assert.deepEqual(AdminListState.resolvePage(3, {content: [], page: 3, totalElements: 0, totalPages: 0}, true),
        {page: 0, reload: false});
    assert.deepEqual(AdminListState.resolvePage(3, {content: [], page: 3, totalElements: 0, totalPages: 0}, false),
        {page: 0, reload: false});
});

test('resolvePage keeps page 0 empty lists and valid pages as the server reported them', () => {
    assert.deepEqual(AdminListState.resolvePage(0, {content: [], page: 0, totalElements: 0, totalPages: 0}, true),
        {page: 0, reload: false});
    assert.deepEqual(AdminListState.resolvePage(1, {content: [{id: 1}], page: 1, totalElements: 21, totalPages: 2}, true),
        {page: 1, reload: false});
    // 서버가 실제로 사용한 page가 다르면(예: Spring 보정) 응답 page를 따른다.
    assert.deepEqual(AdminListState.resolvePage(5, {content: [{id: 1}], page: 0, totalElements: 1, totalPages: 1}, true),
        {page: 0, reload: false});
});

test('loading the module has no side effects on a global window/document', () => {
    assert.equal(typeof globalThis.AdminListState, 'undefined');
});
