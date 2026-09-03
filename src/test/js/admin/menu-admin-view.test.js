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

// P13-T30D(Task C): targetSubvalue(BOARD_LIST+REVIEW 전용) conditional UI.
test('templates/admin/menu/form.html targetSubvalue field is hidden by default and offers COURSE/SPECIAL plus a NULL-compatible option', () => {
    const html = readTemplate('admin/menu/form.html');
    assert.match(html, /<div class="mb-3" id="targetSubvalueField" hidden>/);
    const fieldIndex = html.indexOf('id="targetSubvalueField"');
    const selectIndex = html.indexOf('<select class="form-select" id="targetSubvalue" name="targetSubvalue">', fieldIndex);
    const emptyOptionIndex = html.indexOf('<option value="">', selectIndex);
    const courseOptionIndex = html.indexOf('<option value="COURSE">', selectIndex);
    const specialOptionIndex = html.indexOf('<option value="SPECIAL">', selectIndex);

    assert.notEqual(selectIndex, -1);
    // 기존 generic REVIEW 메뉴 호환을 위해 미지정(빈 값) 옵션도 정상적으로 선택 가능해야 한다
    // (Board의 programType select와 달리 여기서는 빈 값 선택을 막지 않는다).
    assert.ok(emptyOptionIndex !== -1 && courseOptionIndex !== -1 && specialOptionIndex !== -1);
});

test('templates/admin/menu/form.html toggles targetSubvalue visibility only for BOARD_LIST+REVIEW and clears it otherwise', () => {
    const html = readTemplate('admin/menu/form.html');
    assert.match(html, /function updateTargetSubvalueVisibility\(\)/);
    assert.match(html, /document\.querySelector\('#targetType'\)\.value === 'BOARD_LIST'/);
    assert.match(html, /document\.querySelector\('#targetValue'\)\.value === 'REVIEW'/);
    assert.match(html, /document\.querySelector\('#targetSubvalueField'\)\.hidden = !isBoardReview/);
    assert.match(html, /document\.querySelector\('#targetSubvalue'\)\.value = '';/);
    // targetType select change AND targetValue free-text input both must re-evaluate visibility -
    // targetValue is a plain <input>, not a <select>, so only 'change' would miss same-tick edits.
    assert.match(html, /document\.querySelector\('#targetType'\)\.addEventListener\('change', updateTargetSubvalueVisibility\)/);
    assert.match(html, /document\.querySelector\('#targetValue'\)\.addEventListener\('input', updateTargetSubvalueVisibility\)/);
});

test('templates/admin/menu/form.html prefills targetSubvalue from the fetched menu and re-runs visibility after both target fields are set', () => {
    const html = readTemplate('admin/menu/form.html');
    assert.match(html, /document\.querySelector\('#targetSubvalue'\)\.value = menu\.targetSubvalue \|\| '';/);
    const targetValuePrefillIndex = html.indexOf("document.querySelector('#targetValue').value = menu.targetValue");
    const subvaluePrefillIndex = html.indexOf("document.querySelector('#targetSubvalue').value = menu.targetSubvalue");
    const visibilityCallIndex = html.indexOf('updateTargetSubvalueVisibility();', subvaluePrefillIndex);

    assert.ok(targetValuePrefillIndex !== -1 && targetValuePrefillIndex < subvaluePrefillIndex,
        'targetValue must be prefilled before targetSubvalue so the BOARD_LIST+REVIEW check is accurate');
    assert.notEqual(visibilityCallIndex, -1);
});

test('templates/admin/menu/form.html payload normalizes targetSubvalue to null unless targetType=BOARD_LIST and targetValue=REVIEW', () => {
    const html = readTemplate('admin/menu/form.html');
    assert.match(html, /var isBoardReview = targetTypeValue === 'BOARD_LIST' && targetValueValue === 'REVIEW';/);
    assert.match(html, /targetSubvalue: isBoardReview \? \(document\.querySelector\('#targetSubvalue'\)\.value \|\| null\) : null/);
});
