// package.json 없이 Node.js 내장 테스트 러너로 실행한다:
//   node --test src/test/js/admin/ckeditor-resize-plugin.test.js
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const AdminCkeditorResizePlugin = require('../../../main/resources/static/js/admin/ckeditor-resize-plugin.js');

// P13-T40: 이 모듈은 실제 CKEditor editor 인스턴스 없이도 검증 가능한 "순수 계약"만 여기서
// 확인한다 - model schema 확장/downcast/upcast/undo-redo/버튼 DOM 연동처럼 진짜 CKEditor
// 인스턴스가 필요한 부분은 이 테스트로 "검증했다"고 과장하지 않고, 브라우저 기반 헤드리스
// 검증(개발 중 임시 스크립트로 수행, 결과는 구현 보고서에 기록) 및 Playwright/수동 검증 대상으로
// 명확히 분리한다.

test('module exports the expected public surface', () => {
    assert.equal(typeof AdminCkeditorResizePlugin.Plugin, 'function');
    assert.deepEqual(AdminCkeditorResizePlugin.ALLOWED_WIDTHS, ['25', '50', '75']);
    assert.equal(typeof AdminCkeditorResizePlugin.setResize, 'function');
    assert.equal(typeof AdminCkeditorResizePlugin.bindResizeControls, 'function');
});

test('Plugin does not extend any CKEditor base class (plain constructor function usable via extraPlugins without a bundler)', () => {
    const proto = Object.getPrototypeOf(AdminCkeditorResizePlugin.Plugin.prototype);
    assert.equal(proto, Object.prototype);
});

test('Plugin instance exposes an init() method', () => {
    const instance = new AdminCkeditorResizePlugin.Plugin({ model: {}, conversion: {} });
    assert.equal(typeof instance.init, 'function');
});

test('setResize rejects a value outside the 25/50/75/null allowlist without touching the model', () => {
    let modelChangeCalled = false;
    const fakeEditor = {
        model: {
            document: { selection: { getSelectedElement: () => ({ is: () => true, getAttribute: () => null }) } },
            change: () => { modelChangeCalled = true; }
        }
    };

    const result = AdminCkeditorResizePlugin.setResize(fakeEditor, '999');

    assert.equal(result, false);
    assert.equal(modelChangeCalled, false);
});

test('setResize rejects when nothing (or a non-imageBlock element) is selected', () => {
    let modelChangeCalled = false;
    const fakeEditor = {
        model: {
            document: { selection: { getSelectedElement: () => null } },
            change: () => { modelChangeCalled = true; }
        }
    };

    assert.equal(AdminCkeditorResizePlugin.setResize(fakeEditor, '50'), false);
    assert.equal(modelChangeCalled, false);
});

test('setResize accepts exactly "25"/"50"/"75"/null and calls editor.model.change when an imageBlock is selected', () => {
    AdminCkeditorResizePlugin.ALLOWED_WIDTHS.concat([null]).forEach((value) => {
        let calledWith = null;
        const fakeWriter = {
            setAttribute: (key, val) => { calledWith = ['set', key, val]; },
            removeAttribute: (key) => { calledWith = ['remove', key]; }
        };
        const fakeEditor = {
            model: {
                document: {
                    selection: {
                        getSelectedElement: () => ({ is: (type, name) => type === 'element' && name === 'imageBlock' })
                    }
                },
                change: (fn) => fn(fakeWriter)
            }
        };

        const result = AdminCkeditorResizePlugin.setResize(fakeEditor, value);

        assert.equal(result, true);
        if (value === null) {
            assert.deepEqual(calledWith, ['remove', 'resizedWidth']);
        } else {
            assert.deepEqual(calledWith, ['set', 'resizedWidth', value]);
        }
    });
});

test('bindResizeControls is a no-op when container is missing (does not throw)', () => {
    assert.doesNotThrow(() => AdminCkeditorResizePlugin.bindResizeControls({}, null));
});
