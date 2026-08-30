// package.json 없이 Node.js 내장 테스트 러너로 실행한다:
//   node --test src/test/js/admin/ckeditor-config.test.js
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { EDITOR_CONFIG } = require('../../../main/resources/static/js/admin/ckeditor-config.js');

function readTemplate(relativePath) {
    return fs.readFileSync(path.join(__dirname, '../../../main/resources/templates', relativePath), 'utf8');
}

test('EDITOR_CONFIG.image.toolbar keeps the existing inline/block/side buttons', () => {
    assert.ok(EDITOR_CONFIG.image.toolbar.includes('imageStyle:inline'));
    assert.ok(EDITOR_CONFIG.image.toolbar.includes('imageStyle:block'));
    assert.ok(EDITOR_CONFIG.image.toolbar.includes('imageStyle:side'));
});

test('EDITOR_CONFIG.image.toolbar adds alignLeft/alignCenter/alignRight buttons', () => {
    assert.ok(EDITOR_CONFIG.image.toolbar.includes('imageStyle:alignLeft'));
    assert.ok(EDITOR_CONFIG.image.toolbar.includes('imageStyle:alignCenter'));
    assert.ok(EDITOR_CONFIG.image.toolbar.includes('imageStyle:alignRight'));
});

test('EDITOR_CONFIG.image.toolbar still keeps caption/alt-text buttons', () => {
    assert.ok(EDITOR_CONFIG.image.toolbar.includes('toggleImageCaption'));
    assert.ok(EDITOR_CONFIG.image.toolbar.includes('imageTextAlternative'));
});

test('EDITOR_CONFIG does not declare any Font/Alignment(paragraph)/ImageResize plugin config', () => {
    const configString = JSON.stringify(EDITOR_CONFIG);
    assert.doesNotMatch(configString, /font/i);
    assert.doesNotMatch(configString, /"alignment"/i);
    assert.doesNotMatch(configString, /resize/i);
});

['board', 'program', 'page', 'popup'].forEach((domain) => {
    test(`templates/admin/${domain}/form.html loads ckeditor-config.js and passes AdminCkeditorConfig.EDITOR_CONFIG to ClassicEditor.create`, () => {
        const html = readTemplate(`admin/${domain}/form.html`);
        assert.match(html, /\/js\/admin\/ckeditor-config\.js/);
        assert.match(html, /ClassicEditor\.create\(/);
        assert.match(html, /,\s*AdminCkeditorConfig\.EDITOR_CONFIG\)/);
    });
});

['board', 'program', 'page', 'popup'].forEach((domain) => {
    test(`templates/admin/${domain}/form.html loads ckeditor-config.js before ckeditor-upload-adapter.js (no ordering dependency, but both must load before ClassicEditor.create is called)`, () => {
        const html = readTemplate(`admin/${domain}/form.html`);
        const configScriptIndex = html.indexOf('/js/admin/ckeditor-config.js');
        const createCallIndex = html.indexOf('ClassicEditor.create(');

        assert.notEqual(configScriptIndex, -1);
        assert.notEqual(createCallIndex, -1);
        assert.ok(configScriptIndex < createCallIndex,
            'ckeditor-config.js script tag must appear before the ClassicEditor.create call');
    });
});
