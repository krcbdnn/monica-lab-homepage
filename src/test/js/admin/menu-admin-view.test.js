// package.json 없이 Node.js 내장 테스트 러너로 실행한다:
//   node --test src/test/js/admin/menu-admin-view.test.js
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

function readTemplate(relativePath) {
    return fs.readFileSync(path.join(__dirname, '../../../main/resources/templates', relativePath), 'utf8');
}

test('templates/admin/menu/list.html inherits the common admin layout', () => {
    const html = readTemplate('admin/menu/list.html');
    assert.match(html, /th:replace="~\{admin\/layout\/default :: layout\(/);
});

test('templates/admin/menu/list.html loads menus from the admin menu API via common fetch as a plain array (no pagination)', () => {
    const html = readTemplate('admin/menu/list.html');
    assert.match(html, /AdminFetch\.adminFetch\('\/api\/admin\/menus'\)/);
    assert.match(html, /var menus = body\.data;/);
    assert.doesNotMatch(html, /buildQuery/);
    assert.doesNotMatch(html, /data\.content/);
});

test('templates/admin/menu/list.html links to the new-menu screen', () => {
    const html = readTemplate('admin/menu/list.html');
    assert.match(html, /href="\/admin\/menus\/new"/);
});

test('templates/admin/menu/list.html links each row to its edit screen', () => {
    const html = readTemplate('admin/menu/list.html');
    assert.match(html, /editLink\.href = '\/admin\/menus\/' \+ menu\.id \+ '\/edit'/);
});

test('templates/admin/menu/list.html wires the delete action to DELETE /api/admin/menus/{id}', () => {
    const html = readTemplate('admin/menu/list.html');
    assert.match(html, /AdminFetch\.adminFetch\('\/api\/admin\/menus\/' \+ menu\.id, \{method: 'DELETE'\}\)/);
});

// P13-T30A: 자식이 있는 메뉴 삭제는 서버가 409(MENU_HAS_CHILDREN)를 반환한다 - 다른 도메인의
// 삭제 버튼과 달리, 실패를 조용히 넘기지 않고 에러 메시지를 화면에 보여줘야 한다.
test('templates/admin/menu/list.html surfaces an error message when delete fails (e.g. menu has children)', () => {
    const html = readTemplate('admin/menu/list.html');
    const deleteHandlerIndex = html.indexOf("deleteButton.addEventListener('click'");
    const responseOkCheckIndex = html.indexOf('response.ok', deleteHandlerIndex);
    const showErrorCallIndex = html.indexOf('showError(', deleteHandlerIndex);

    assert.notEqual(deleteHandlerIndex, -1);
    assert.notEqual(responseOkCheckIndex, -1, 'delete handler must check response.ok before reloading');
    assert.ok(showErrorCallIndex > deleteHandlerIndex,
        'delete handler must call showError(...) when the request fails');
});

test('templates/admin/menu/list.html wires the visibility toggle to PATCH .../visibility', () => {
    const html = readTemplate('admin/menu/list.html');
    assert.match(html, /'\/api\/admin\/menus\/' \+ menu\.id \+ '\/visibility'/);
    assert.match(html, /visible: !menu\.visible/);
});

test('templates/admin/menu/list.html provides a sortOrder number input with min="0" per row', () => {
    const html = readTemplate('admin/menu/list.html');
    assert.match(html, /sortOrderInput\.type = 'number'/);
    assert.match(html, /sortOrderInput\.min = '0'/);
    assert.match(html, /sortOrderInput\.value = menu\.sortOrder/);
});

test('templates/admin/menu/list.html sends the user-entered sortOrder value as-is to PATCH /order', () => {
    const html = readTemplate('admin/menu/list.html');
    assert.match(html, /'\/api\/admin\/menus\/' \+ menu\.id \+ '\/order'/);
    assert.match(html, /method: 'PATCH'/);
    assert.match(html, /sortOrder: Number\(sortOrderInput\.value\)/);
});

test('templates/admin/menu/list.html does not implement +/-1 nudge buttons or drag-and-drop reordering', () => {
    const html = readTemplate('admin/menu/list.html');
    assert.doesNotMatch(html, /sortOrder\s*[+-]\s*1/);
    assert.doesNotMatch(html, /sortOrder\s*[+-]=\s*1/);
    assert.doesNotMatch(html, /draggable/i);
    assert.doesNotMatch(html, /dragstart/i);
    assert.doesNotMatch(html, /위로|아래로/);
});

// P13-T30A: /api/admin/menus는 이미 "부모 바로 뒤에 자식"이 오는 순서로 응답하므로, 화면은 그
// 순서를 그대로 렌더링하되 자식 행만 라벨 앞에 들여쓰기 접두사를 붙인다(별도 트리 조립 없음).
test('templates/admin/menu/list.html indents child rows (parentId present) without rebuilding the tree client-side', () => {
    const html = readTemplate('admin/menu/list.html');
    assert.match(html, /menu\.parentId \? '— ' : ''/);
});

test('templates/admin/menu/form.html inherits the common admin layout', () => {
    const html = readTemplate('admin/menu/form.html');
    assert.match(html, /th:replace="~\{admin\/layout\/default :: layout\(/);
});

test('templates/admin/menu/form.html parses the editing menu id from the URL path, not from a model attribute', () => {
    const html = readTemplate('admin/menu/form.html');
    assert.match(html, /window\.location\.pathname\.match\(/);
    assert.match(html, /function extractMenuIdFromPath/);
});

test('templates/admin/menu/form.html offers all 7 MenuTargetType options', () => {
    const html = readTemplate('admin/menu/form.html');
    for (const type of ['GROUP', 'HOME', 'PAGE', 'PROGRAM_LIST', 'BOARD_LIST', 'INTERNAL_URL', 'EXTERNAL_URL']) {
        assert.match(html, new RegExp(`<option value="${type}"`));
    }
});

// P13-T30A: 상위 메뉴로 선택 가능한 것은 유형이 GROUP인 최상위 메뉴뿐이고, 자기 자신은 제외한다.
test('templates/admin/menu/form.html restricts the parent select to top-level GROUP menus, excluding itself', () => {
    const html = readTemplate('admin/menu/form.html');
    assert.match(html, /menu\.targetType === 'GROUP' && !menu\.parentId/);
    assert.match(html, /String\(menu\.id\) !== String\(editingMenuId\)/);
});

test('templates/admin/menu/form.html branches between POST and PUT based on the presence of a menu id', () => {
    const html = readTemplate('admin/menu/form.html');
    assert.match(html, /var method = menuId \? 'PUT' : 'POST';/);
});

test('templates/admin/menu/form.html sends parentId as a Number or null, never an empty string', () => {
    const html = readTemplate('admin/menu/form.html');
    assert.match(html, /parentId: parentIdValue \? Number\(parentIdValue\) : null/);
});

test('templates/admin/menu/form.html redirects to the list screen after a successful save', () => {
    const html = readTemplate('admin/menu/form.html');
    assert.match(html, /window\.location\.href = '\/admin\/menus'/);
});

test('templates/admin/menu/form.html displays an error message when the save request fails', () => {
    const html = readTemplate('admin/menu/form.html');
    assert.match(html, /function showError/);
    assert.match(html, /errorMessage\.style\.display = 'block'/);
});

test('templates/admin/menu/form.html prefills visible/openInNewTab checkboxes and waits for parent options before prefilling', () => {
    const html = readTemplate('admin/menu/form.html');
    assert.match(html, /document\.querySelector\('#visible'\)\.checked = menu\.visible;/);
    assert.match(html, /document\.querySelector\('#openInNewTab'\)\.checked = menu\.openInNewTab;/);
    assert.match(html, /Promise\.all\(\[[\s\S]*?parentOptionsReady[\s\S]*?\]\)/);
});
