// package.json 없이 Node.js 내장 테스트 러너로 실행한다:
//   node --test src/test/js/admin/admin-display.test.js
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const AdminDisplay = require('../../../main/resources/static/js/admin/admin-display.js');

// jsdom 없이 쓰는 최소 fake DOM. innerHTML/outerHTML/insertAdjacentHTML은 접근 자체를 금지해 helper가
// HTML 문자열 경로를 절대 쓰지 않음을 검증한다.
function createFakeElement(tagName) {
    const element = {
        tagName: tagName.toUpperCase(),
        className: '',
        textContent: '',
        hidden: false,
        attributes: {},
        children: [],
        get firstChild() {
            return this.children[0] || null;
        },
        appendChild(child) {
            this.children.push(child);
            return child;
        },
        removeChild(child) {
            const index = this.children.indexOf(child);
            assert.notEqual(index, -1, 'removeChild target must be a child');
            this.children.splice(index, 1);
            return child;
        },
        setAttribute(name, value) {
            this.attributes[name] = String(value);
        },
        getAttribute(name) {
            return Object.prototype.hasOwnProperty.call(this.attributes, name) ? this.attributes[name] : null;
        },
        removeAttribute(name) {
            delete this.attributes[name];
        }
    };
    for (const forbidden of ['innerHTML', 'outerHTML', 'insertAdjacentHTML']) {
        Object.defineProperty(element, forbidden, {
            get() {
                throw new Error(forbidden + ' must not be used');
            },
            set() {
                throw new Error(forbidden + ' must not be used');
            }
        });
    }
    return element;
}

const fakeDoc = {createElement: (tagName) => createFakeElement(tagName)};

test('BoardType labels map all 4 values', () => {
    assert.equal(AdminDisplay.label(AdminDisplay.BOARD_TYPE_LABELS, 'NOTICE'), '공지사항');
    assert.equal(AdminDisplay.label(AdminDisplay.BOARD_TYPE_LABELS, 'GALLERY'), '갤러리');
    assert.equal(AdminDisplay.label(AdminDisplay.BOARD_TYPE_LABELS, 'ARCHIVE'), '자료실');
    assert.equal(AdminDisplay.label(AdminDisplay.BOARD_TYPE_LABELS, 'REVIEW'), '강의 후기');
});

test('ProgramType labels map COURSE/SPECIAL', () => {
    assert.equal(AdminDisplay.label(AdminDisplay.PROGRAM_TYPE_LABELS, 'COURSE'), '정규 강좌');
    assert.equal(AdminDisplay.label(AdminDisplay.PROGRAM_TYPE_LABELS, 'SPECIAL'), '특강');
});

test('RecruitStatus labels and tones map OPEN/CLOSED', () => {
    assert.equal(AdminDisplay.label(AdminDisplay.RECRUIT_STATUS_LABELS, 'OPEN'), '모집중');
    assert.equal(AdminDisplay.label(AdminDisplay.RECRUIT_STATUS_LABELS, 'CLOSED'), '마감');
    assert.equal(AdminDisplay.recruitStatusTone('OPEN'), 'positive');
    assert.equal(AdminDisplay.recruitStatusTone('CLOSED'), 'muted');
    assert.equal(AdminDisplay.recruitStatusTone('PAUSED'), 'neutral');
});

test('FileType labels map IMAGE/ATTACHMENT', () => {
    assert.equal(AdminDisplay.label(AdminDisplay.FILE_TYPE_LABELS, 'IMAGE'), '이미지');
    assert.equal(AdminDisplay.label(AdminDisplay.FILE_TYPE_LABELS, 'ATTACHMENT'), '첨부파일');
});

test('label falls back to the raw value for unknown values and "-" for null/undefined/empty', () => {
    assert.equal(AdminDisplay.label(AdminDisplay.BOARD_TYPE_LABELS, 'EVENT'), 'EVENT');
    assert.equal(AdminDisplay.label(AdminDisplay.BOARD_TYPE_LABELS, null), '-');
    assert.equal(AdminDisplay.label(AdminDisplay.BOARD_TYPE_LABELS, undefined), '-');
    assert.equal(AdminDisplay.label(AdminDisplay.BOARD_TYPE_LABELS, ''), '-');
    // prototype 속성 이름이 label로 새지 않는다.
    assert.equal(AdminDisplay.label(AdminDisplay.BOARD_TYPE_LABELS, 'toString'), 'toString');
});

test('boardTypeLabel shows the REVIEW subtype in parentheses', () => {
    assert.equal(AdminDisplay.boardTypeLabel('REVIEW', null), '강의 후기');
    assert.equal(AdminDisplay.boardTypeLabel('REVIEW', ''), '강의 후기');
    assert.equal(AdminDisplay.boardTypeLabel('REVIEW', undefined), '강의 후기');
    assert.equal(AdminDisplay.boardTypeLabel('REVIEW', 'COURSE'), '강의 후기(정규 강좌)');
    assert.equal(AdminDisplay.boardTypeLabel('REVIEW', 'SPECIAL'), '강의 후기(특강)');
    assert.equal(AdminDisplay.boardTypeLabel('REVIEW', 'WORKSHOP'), '강의 후기(WORKSHOP)');
});

test('boardTypeLabel ignores programType for non-REVIEW board types', () => {
    assert.equal(AdminDisplay.boardTypeLabel('NOTICE', 'COURSE'), '공지사항');
    assert.equal(AdminDisplay.boardTypeLabel('GALLERY', 'SPECIAL'), '갤러리');
    assert.equal(AdminDisplay.boardTypeLabel('EVENT', 'COURSE'), 'EVENT');
    assert.equal(AdminDisplay.boardTypeLabel(null, 'COURSE'), '-');
});

test('badgeClassName supports exactly 4 tones and falls back to neutral', () => {
    assert.deepEqual([...AdminDisplay.TONES], ['positive', 'muted', 'neutral', 'attention']);
    assert.equal(AdminDisplay.badgeClassName('positive'), 'badge admin-badge admin-badge--positive');
    assert.equal(AdminDisplay.badgeClassName('muted'), 'badge admin-badge admin-badge--muted');
    assert.equal(AdminDisplay.badgeClassName('neutral'), 'badge admin-badge admin-badge--neutral');
    assert.equal(AdminDisplay.badgeClassName('attention'), 'badge admin-badge admin-badge--attention');
    assert.equal(AdminDisplay.badgeClassName('info'), 'badge admin-badge admin-badge--neutral');
    assert.equal(AdminDisplay.badgeClassName(undefined), 'badge admin-badge admin-badge--neutral');
});

test('label maps and TONES are frozen', () => {
    for (const constant of [AdminDisplay.BOARD_TYPE_LABELS, AdminDisplay.PROGRAM_TYPE_LABELS,
        AdminDisplay.RECRUIT_STATUS_LABELS, AdminDisplay.FILE_TYPE_LABELS, AdminDisplay.TONES]) {
        assert.ok(Object.isFrozen(constant));
    }
});

test('apiErrorMessage uses a server error message when present, otherwise the fallback', () => {
    assert.equal(AdminDisplay.apiErrorMessage({error: {message: '하위 메뉴가 있습니다.'}}, 'fallback'), '하위 메뉴가 있습니다.');
    assert.equal(AdminDisplay.apiErrorMessage({error: {message: ''}}, 'fallback'), 'fallback');
    assert.equal(AdminDisplay.apiErrorMessage({error: null}, 'fallback'), 'fallback');
    assert.equal(AdminDisplay.apiErrorMessage({error: {message: {nested: true}}}, 'fallback'), 'fallback');
    assert.equal(AdminDisplay.apiErrorMessage(null, 'fallback'), 'fallback');
});

test('createBadge builds a span with the tone class and textContent', () => {
    const badge = AdminDisplay.createBadge(fakeDoc, '공지사항', 'neutral');
    assert.equal(badge.tagName, 'SPAN');
    assert.equal(badge.className, 'badge admin-badge admin-badge--neutral');
    assert.equal(badge.textContent, '공지사항');
    assert.equal(AdminDisplay.createBadge(fakeDoc, null, 'neutral').textContent, '-');
});

test('createBadge keeps markup-like text as plain textContent (no HTML path)', () => {
    const payload = '<img src=x onerror="alert(1)">';
    const badge = AdminDisplay.createBadge(fakeDoc, payload, 'attention');
    assert.equal(badge.textContent, payload);
    assert.equal(badge.children.length, 0);
});

test('createStateBadge maps on/off to positive/muted with the matching text', () => {
    const on = AdminDisplay.createStateBadge(fakeDoc, true, '공개', '비공개');
    const off = AdminDisplay.createStateBadge(fakeDoc, false, '공개', '비공개');
    assert.equal(on.className, 'badge admin-badge admin-badge--positive');
    assert.equal(on.textContent, '공개');
    assert.equal(off.className, 'badge admin-badge admin-badge--muted');
    assert.equal(off.textContent, '비공개');
});

test('replaceContent removes existing children and appends the node; ignores a missing element', () => {
    const dd = createFakeElement('dd');
    dd.appendChild(createFakeElement('span'));
    dd.appendChild(createFakeElement('span'));
    const badge = AdminDisplay.createBadge(fakeDoc, '특강', 'neutral');
    AdminDisplay.replaceContent(dd, badge);
    assert.deepEqual(dd.children, [badge]);
    assert.doesNotThrow(() => AdminDisplay.replaceContent(null, badge));
});

test('showStatus success sets alert-success, role=status, unhides and sets textContent', () => {
    const el = createFakeElement('div');
    el.hidden = true;
    AdminDisplay.showStatus(el, '삭제되었습니다.', 'success');
    assert.equal(el.className, 'alert alert-success');
    assert.equal(el.getAttribute('role'), 'status');
    assert.equal(el.hidden, false);
    assert.equal(el.textContent, '삭제되었습니다.');
});

test('showStatus error sets alert-danger and role=alert, with server text kept as textContent', () => {
    const el = createFakeElement('div');
    el.hidden = true;
    const message = '<b>오류</b>';
    AdminDisplay.showStatus(el, message, 'error');
    assert.equal(el.className, 'alert alert-danger');
    assert.equal(el.getAttribute('role'), 'alert');
    assert.equal(el.hidden, false);
    assert.equal(el.textContent, message);
});

test('showStatus/clearStatus ignore a missing element', () => {
    assert.doesNotThrow(() => AdminDisplay.showStatus(null, 'x', 'error'));
    assert.doesNotThrow(() => AdminDisplay.clearStatus(null));
});

test('clearStatus hides the element and removes stale text/role/class', () => {
    const el = createFakeElement('div');
    AdminDisplay.showStatus(el, '삭제 중 오류가 발생했습니다.', 'error');
    AdminDisplay.clearStatus(el);
    assert.equal(el.hidden, true);
    assert.equal(el.textContent, '');
    assert.equal(el.getAttribute('role'), null);
    assert.equal(el.className, '');
});

test('renderEmptyRow replaces all existing rows with one colspan row', () => {
    const tbody = createFakeElement('tbody');
    tbody.appendChild(createFakeElement('tr'));
    tbody.appendChild(createFakeElement('tr'));
    AdminDisplay.renderEmptyRow(fakeDoc, tbody, 4, '등록된 게시글이 없습니다.');

    assert.equal(tbody.children.length, 1);
    const tr = tbody.children[0];
    assert.equal(tr.tagName, 'TR');
    assert.equal(tr.children.length, 1);
    const td = tr.children[0];
    assert.equal(td.tagName, 'TD');
    assert.equal(td.className, 'admin-list-empty');
    assert.equal(td.getAttribute('colspan'), '4');
    assert.equal(td.textContent, '등록된 게시글이 없습니다.');
});

test('renderErrorRow replaces previous rows with one error row', () => {
    const tbody = createFakeElement('tbody');
    tbody.appendChild(createFakeElement('tr'));
    AdminDisplay.renderErrorRow(fakeDoc, tbody, 6, '파일 목록을 불러오지 못했습니다.');

    assert.equal(tbody.children.length, 1);
    const td = tbody.children[0].children[0];
    assert.equal(td.className, 'admin-list-empty admin-list-empty--error');
    assert.equal(td.getAttribute('colspan'), '6');
    assert.equal(td.textContent, '파일 목록을 불러오지 못했습니다.');
});

test('loading the module has no side effects on a global document', () => {
    // module.exports 경로에서는 root.AdminDisplay를 만들지 않고, DOM 함수는 인자로 받은 요소만 다룬다.
    assert.equal(typeof globalThis.AdminDisplay, 'undefined');
});
