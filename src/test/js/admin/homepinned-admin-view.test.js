// package.json 없이 Node.js 내장 테스트 러너로 실행한다:
//   node --test src/test/js/admin/homepinned-admin-view.test.js
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

function readTemplate(relativePath) {
    return fs.readFileSync(path.join(__dirname, '../../../main/resources/templates', relativePath), 'utf8');
}

function html() {
    return readTemplate('admin/homepinned/list.html');
}

test('templates/admin/homepinned/list.html inherits the common admin layout', () => {
    assert.match(html(), /th:replace="~\{admin\/layout\/default :: layout\(/);
});

test('templates/admin/homepinned/list.html has a #searchSubtype select next to #searchTargetType', () => {
    const targetTypeIndex = html().indexOf('id="searchTargetType"');
    const subtypeIndex = html().indexOf('id="searchSubtype"');
    assert.notEqual(targetTypeIndex, -1);
    assert.notEqual(subtypeIndex, -1);
    assert.ok(targetTypeIndex < subtypeIndex, '#searchSubtype must come after #searchTargetType in the search form');
});

// P13-T38C: searchState는 targetType/subtype/keyword를 committed 값으로 보관한다. keyword는
// searchForm의 submit(검색 버튼/Enter)에서만 갱신되고, targetType/subtype의 change 리스너는 keyword를
// 절대 다시 읽지 않는다.
test('templates/admin/homepinned/list.html defines searchState with BOARD/empty-subtype/empty-keyword defaults', () => {
    assert.match(html(), /var searchState = \{targetType: 'BOARD', subtype: '', keyword: ''\};/);
});

test('templates/admin/homepinned/list.html SUBTYPE_OPTIONS maps BOARD to NOTICE/GALLERY/ARCHIVE/REVIEW with an empty "전체" option first', () => {
    const source = html();
    const boardOptionsIndex = source.indexOf('BOARD: [');
    const programOptionsIndex = source.indexOf('PROGRAM: [');
    assert.notEqual(boardOptionsIndex, -1);
    assert.notEqual(programOptionsIndex, -1);
    assert.ok(boardOptionsIndex < programOptionsIndex);

    const boardOptionsBlock = source.slice(boardOptionsIndex, programOptionsIndex);
    assert.match(boardOptionsBlock, /\{value: '', label: '전체'\}/);
    assert.match(boardOptionsBlock, /\{value: 'NOTICE', label: '공지사항'\}/);
    assert.match(boardOptionsBlock, /\{value: 'GALLERY', label: '갤러리'\}/);
    assert.match(boardOptionsBlock, /\{value: 'ARCHIVE', label: '자료실'\}/);
    assert.match(boardOptionsBlock, /\{value: 'REVIEW', label: '강의 후기'\}/);
});

// COURSE 라벨은 공개 Menu/문서("수강 프로그램")가 아니라, 같은 관리자 CMS 화면인
// admin/program/list.html·admin/program/form.html과 동일한 "정규 강좌"를 쓴다(내부 일관성 우선).
test('templates/admin/homepinned/list.html SUBTYPE_OPTIONS maps PROGRAM to COURSE("정규 강좌")/SPECIAL with an empty "전체" option first', () => {
    const source = html();
    const programOptionsIndex = source.indexOf('PROGRAM: [');
    const programOptionsEnd = source.indexOf('};', programOptionsIndex);
    assert.notEqual(programOptionsIndex, -1);

    const programOptionsBlock = source.slice(programOptionsIndex, programOptionsEnd);
    assert.match(programOptionsBlock, /\{value: '', label: '전체'\}/);
    assert.match(programOptionsBlock, /\{value: 'COURSE', label: '정규 강좌'\}/);
    assert.match(programOptionsBlock, /\{value: 'SPECIAL', label: '특강'\}/);
});

test('templates/admin/homepinned/list.html has a populateSubtypeOptions() function that rebuilds #searchSubtype from SUBTYPE_OPTIONS[searchState.targetType]', () => {
    const source = html();
    const fnIndex = source.indexOf('function populateSubtypeOptions()');
    assert.notEqual(fnIndex, -1);
    const fnEnd = source.indexOf('\n        }', fnIndex);
    const fnBody = source.slice(fnIndex, fnEnd);

    assert.match(fnBody, /getElementById\('searchSubtype'\)/);
    assert.match(fnBody, /SUBTYPE_OPTIONS\[searchState\.targetType\]/);
});

// buildSearchParams()가 targetType에 따라 boardType 또는 programType 중 정확히 하나의 파라미터
// 이름으로만 subtype을 실어 보내는지 소스 레벨에서 확인한다(BOARD/PROGRAM 파라미터가 절대 섞이지
// 않는다는 구조적 보장).
test('templates/admin/homepinned/list.html buildSearchParams() sends boardType only for BOARD and programType only for PROGRAM, never a subtype parameter', () => {
    const source = html();
    const fnIndex = source.indexOf('function buildSearchParams()');
    const fnEnd = source.indexOf('\n        }', fnIndex);
    assert.notEqual(fnIndex, -1);
    const fnBody = source.slice(fnIndex, fnEnd);

    assert.match(fnBody, /params\.set\('size', 20\)/);
    assert.match(fnBody, /searchState\.targetType === 'BOARD'/);
    assert.match(fnBody, /params\.set\('boardType', searchState\.subtype\)/);
    assert.match(fnBody, /params\.set\('programType', searchState\.subtype\)/);
    assert.doesNotMatch(fnBody, /'subtype'/);
});

test('templates/admin/homepinned/list.html buildSearchParams() omits boardType/programType entirely when subtype is empty ("전체")', () => {
    const source = html();
    const fnIndex = source.indexOf('function buildSearchParams()');
    const fnEnd = source.indexOf('\n        }', fnIndex);
    const fnBody = source.slice(fnIndex, fnEnd);

    assert.match(fnBody, /if \(searchState\.subtype\) \{/);
});

test('templates/admin/homepinned/list.html runSearch() builds the request from searchState only, never reading the keyword input directly', () => {
    const source = html();
    const fnIndex = source.indexOf('function runSearch()');
    const fnEnd = source.indexOf('\n        }', fnIndex);
    assert.notEqual(fnIndex, -1);
    const fnBody = source.slice(fnIndex, fnEnd);

    assert.match(fnBody, /searchState\.targetType === 'BOARD'/);
    assert.match(fnBody, /buildSearchParams\(\)/);
    assert.doesNotMatch(fnBody, /getElementById\('searchKeyword'\)/);
    assert.doesNotMatch(fnBody, /getElementById\('searchTargetType'\)/);
});

// targetType change: targetType 갱신 + subtype 초기화 + subtype option 재구성 + 즉시 재조회.
// keyword는 이 핸들러 안에서 절대 다시 읽지 않는다(§keyword 정책, admin/board/list.html의 P13-T31과 동일).
test('templates/admin/homepinned/list.html #searchTargetType change listener resets subtype, rebuilds subtype options, and re-fetches without touching keyword', () => {
    const source = html();
    const changeHandlerIndex = source.indexOf("document.getElementById('searchTargetType').addEventListener('change'");
    assert.notEqual(changeHandlerIndex, -1, 'searchTargetType must have a change listener');

    const handlerEnd = source.indexOf('});', changeHandlerIndex);
    const handlerBody = source.slice(changeHandlerIndex, handlerEnd);

    assert.match(handlerBody, /searchState\.targetType = this\.value;/);
    assert.match(handlerBody, /searchState\.subtype = '';/);
    assert.match(handlerBody, /populateSubtypeOptions\(\);/);
    assert.match(handlerBody, /runSearch\(\);/);
    assert.doesNotMatch(handlerBody, /searchState\.keyword/);
});

// subtype change: subtype만 갱신 + 즉시 재조회. keyword/targetType은 건드리지 않는다.
test('templates/admin/homepinned/list.html #searchSubtype change listener updates subtype and re-fetches without touching keyword or targetType', () => {
    const source = html();
    const changeHandlerIndex = source.indexOf("document.getElementById('searchSubtype').addEventListener('change'");
    assert.notEqual(changeHandlerIndex, -1, 'searchSubtype must have a change listener');

    const handlerEnd = source.indexOf('});', changeHandlerIndex);
    const handlerBody = source.slice(changeHandlerIndex, handlerEnd);

    assert.match(handlerBody, /searchState\.subtype = this\.value;/);
    assert.match(handlerBody, /runSearch\(\);/);
    assert.doesNotMatch(handlerBody, /searchState\.keyword/);
    assert.doesNotMatch(handlerBody, /searchState\.targetType/);
});

// 기존 submit 핸들러(검색 버튼/Enter)는 targetType/subtype/keyword를 전부 함께 committed state로
// 반영한다 - 이 계약은 P13-T38C 이후에도 유지돼야 한다.
test('templates/admin/homepinned/list.html search form submit commits targetType, subtype, and keyword together', () => {
    const source = html();
    const submitHandlerIndex = source.indexOf("document.getElementById('searchForm').addEventListener('submit'");
    const changeHandlerIndex = source.indexOf("document.getElementById('searchTargetType').addEventListener('change'");
    assert.notEqual(submitHandlerIndex, -1);
    assert.ok(submitHandlerIndex < changeHandlerIndex, 'submit handler must remain defined before the new change listeners');

    const submitHandlerBody = source.slice(submitHandlerIndex, changeHandlerIndex);
    assert.match(submitHandlerBody, /searchState\.targetType = document\.getElementById\('searchTargetType'\)\.value;/);
    assert.match(submitHandlerBody, /searchState\.subtype = document\.getElementById\('searchSubtype'\)\.value;/);
    assert.match(submitHandlerBody, /searchState\.keyword = document\.getElementById\('searchKeyword'\)\.value;/);
});

test('templates/admin/homepinned/list.html initializes subtype options for the default BOARD targetType before the first render', () => {
    const source = html();
    const initCallIndex = source.lastIndexOf('populateSubtypeOptions();');
    const loadPinnedCallIndex = source.lastIndexOf('loadPinned();');
    assert.notEqual(initCallIndex, -1);
    assert.notEqual(loadPinnedCallIndex, -1);
    assert.ok(initCallIndex < loadPinnedCallIndex, 'populateSubtypeOptions() must run once at load time, before loadPinned()');
});
