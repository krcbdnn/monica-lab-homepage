// package.json 없이 Node.js 내장 테스트 러너로 실행한다:
//   node --test src/test/js/home/nav-submenu.test.js
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { resolveOpenGroupId } = require('../../../main/resources/static/js/home/nav-submenu.js');

test('resolveOpenGroupId opens the target group when nothing is open yet', () => {
    assert.equal(resolveOpenGroupId(null, 'about'), 'about');
});

test('resolveOpenGroupId closes the group when the same open group is activated again', () => {
    assert.equal(resolveOpenGroupId('about', 'about'), null);
});

test('resolveOpenGroupId switches to the newly activated group, implicitly closing the previous one', () => {
    assert.equal(resolveOpenGroupId('about', 'programs'), 'programs');
});
