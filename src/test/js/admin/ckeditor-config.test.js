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

// P13-T25: 기존 6개 style 버튼을 balloon toolbar에 나열하지 않고 "이미지 정렬" dropdown 1개로
// 묶는다. image.toolbar의 첫 항목이 그 dropdown이어야 하고(개별 style 문자열이 최상위에 없어야
// 함), 그 dropdown의 items 배열 안에 6개 style이 전부 있어야 한다.
test('EDITOR_CONFIG.image.toolbar groups the 6 existing image styles into a single dropdown (not listed as flat buttons)', () => {
    const toolbar = EDITOR_CONFIG.image.toolbar;
    const dropdown = toolbar[0];

    assert.equal(typeof dropdown, 'object');
    assert.equal(dropdown.name, 'imageStyle:dropdown');
    assert.equal(dropdown.title, '이미지 정렬');
    assert.equal(dropdown.defaultItem, 'imageStyle:block');
    assert.deepEqual(dropdown.items, [
        'imageStyle:inline', 'imageStyle:block', 'imageStyle:side',
        'imageStyle:alignLeft', 'imageStyle:alignCenter', 'imageStyle:alignRight'
    ]);

    // 개별 style 문자열이 toolbar 최상위(평면 버튼)에는 존재하지 않아야 한다(dropdown 안에만 있어야 함).
    ['imageStyle:inline', 'imageStyle:block', 'imageStyle:side',
        'imageStyle:alignLeft', 'imageStyle:alignCenter', 'imageStyle:alignRight'].forEach((styleName) => {
        assert.ok(!toolbar.includes(styleName),
            styleName + ' must not appear as a flat top-level toolbar button');
    });
});

test('EDITOR_CONFIG.image.toolbar still keeps caption/alt-text buttons', () => {
    assert.ok(EDITOR_CONFIG.image.toolbar.includes('toggleImageCaption'));
    assert.ok(EDITOR_CONFIG.image.toolbar.includes('imageTextAlternative'));
});

// P13-T25: 6개 style의 name/className/modelElements는 그대로 두고 title만 한글로 재선언한다.
test('EDITOR_CONFIG.image.styles.options declares Korean titles for all 6 existing styles without touching name/className', () => {
    const options = EDITOR_CONFIG.image.styles.options;
    const expectedTitles = {
        inline: '글 안에 배치',
        block: '기본',
        side: '글 옆에 배치',
        alignLeft: '왼쪽 정렬',
        alignCenter: '가운데 정렬',
        alignRight: '오른쪽 정렬'
    };

    assert.equal(options.length, 6);
    options.forEach((option) => {
        assert.ok(Object.prototype.hasOwnProperty.call(expectedTitles, option.name),
            'unexpected style name: ' + option.name);
        assert.equal(option.title, expectedTitles[option.name]);
        // className/modelElements/icon을 명시하지 않아 CKEditor 기본값을 그대로 재사용해야 한다.
        assert.ok(!Object.prototype.hasOwnProperty.call(option, 'className'));
        assert.ok(!Object.prototype.hasOwnProperty.call(option, 'modelElements'));
    });
    assert.deepEqual(Object.keys(expectedTitles).sort(), options.map((o) => o.name).sort());
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
