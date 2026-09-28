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
    // P14-T9C-1: 목록 전용 showError는 AdminDisplay 기반 showResponseError(서버 error.message 우선)로 대체됐다.
    const showErrorCallIndex = html.indexOf('return showResponseError(response, DELETE_ERROR);', deleteHandlerIndex);

    assert.notEqual(deleteHandlerIndex, -1);
    assert.notEqual(responseOkCheckIndex, -1, 'delete handler must check response.ok before reloading');
    assert.ok(showErrorCallIndex > responseOkCheckIndex,
        'delete handler must show the server error message via showResponseError(...) when the request fails');
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
// 순서를 그대로 렌더링하되 자식 행만 CSS로 들여쓰기한다(별도 트리 조립 없음).
// P13-T30E(Task B): 텍스트 접두사('— ')를 CSS class 기반 들여쓰기(admin.css의
// .admin-menu-row--child .admin-menu-row__label)로 교체했다.
test('templates/admin/menu/list.html indents child rows (parentId present) via a CSS class, without rebuilding the tree client-side', () => {
    const html = readTemplate('admin/menu/list.html');
    assert.match(html, /if \(menu\.parentId\) \{\s*tr\.classList\.add\('admin-menu-row--child'\);/);
    assert.match(html, /labelSpan\.className = 'admin-menu-row__label';/);
    assert.doesNotMatch(html, /'— '/);
});

// P13-T30E(Task B): 유형(targetType)을 raw enum 문자열이 아니라 badge + 한글 라벨로 표시한다.
// P14-T9C-1: 유형은 분류 정보라 유형별 장식색(text-bg-*) 없이 전부 AdminDisplay neutral badge다.
test('templates/admin/menu/list.html renders targetType as a neutral badge instead of the raw enum string', () => {
    const html = readTemplate('admin/menu/list.html');
    assert.match(html, /function typeBadge\(targetType\)/);
    assert.match(html, /return AdminDisplay\.createBadge\(document, TARGET_TYPE_LABELS\[targetType\] \|\| targetType, 'neutral'\);/);
    assert.doesNotMatch(html, /text-bg-/);
    assert.doesNotMatch(html, /targetTypeTd\.textContent = menu\.targetType;/);
});

// P14-T9C-1: 공개/숨김은 state badge(positive/muted)다.
test('templates/admin/menu/list.html renders visibility as a positive/muted state badge', () => {
    const html = readTemplate('admin/menu/list.html');
    assert.match(html, /return AdminDisplay\.createStateBadge\(document, visible, '공개', '숨김'\);/);
});

// P14-T9C-1: BOARD_LIST/PROGRAM_LIST 대상 라벨은 관리자 공통 매핑을 참조하고, REVIEW+subvalue는 공통
// boardTypeLabel(강의 후기(정규 강좌)/강의 후기(특강))로 표시한다 - Menu 전용 "수강" 축약 표현은 없다.
test('templates/admin/menu/list.html uses the shared AdminDisplay labels for BOARD_LIST/PROGRAM_LIST and the REVIEW subvalue', () => {
    const html = readTemplate('admin/menu/list.html');
    assert.match(html, /PROGRAM_LIST: AdminDisplay\.PROGRAM_TYPE_LABELS,/);
    assert.match(html, /BOARD_LIST: AdminDisplay\.BOARD_TYPE_LABELS/);
    assert.match(html, /return AdminDisplay\.boardTypeLabel\(menu\.targetValue, menu\.targetSubvalue\);/);
    assert.doesNotMatch(html, /REVIEW_SUBVALUE_LABELS/);
    assert.doesNotMatch(html, /'수강'/);
});

// P13-T30E(Task B): 알 수 없는 targetType/targetValue를 만나도 raw 값으로 fallback해야 한다(빈
// 문자열로 숨기지 않음) - typeBadge와 targetDisplayLabel 양쪽 모두.
test('templates/admin/menu/list.html falls back to the raw value for unmapped targetType/targetValue instead of hiding it', () => {
    const html = readTemplate('admin/menu/list.html');
    assert.match(html, /TARGET_TYPE_LABELS\[targetType\] \|\| targetType/);
    assert.match(html, /valueLabels\[menu\.targetValue\] \|\| menu\.targetValue/);
});

// P13-T30E(Task B): GROUP은 target_value 자체가 없는 구조적 요소라 '-', HOME은 target_value가 항상
// 비어 있어도 targetType 자체가 목적지(메인 화면)를 의미하므로 GROUP과 동일하게 '-' 처리하지 않고
// '홈'으로 명시한다.
test('templates/admin/menu/list.html displays GROUP target as "-" and HOME target as "홈" (not both as "-")', () => {
    const html = readTemplate('admin/menu/list.html');
    const fnBody = html.slice(html.indexOf('function targetDisplayLabel'), html.indexOf('function typeBadge'));
    assert.match(fnBody, /if \(menu\.targetType === 'GROUP'\) \{\s*return '-';/);
    assert.match(fnBody, /if \(menu\.targetType === 'HOME'\) \{\s*return '홈';/);
});

// P13-T30E(Task B): GROUP도 실제 is_visible 값을 갖고 getPublicMenuTree()에 영향을 주므로, GROUP
// 행이라고 해서 공개/숨김 badge를 다른 행과 다르게 생략하거나 조건 분기하지 않는다 - 모든 행이
// menu.visible 값을 그대로 배지로 보여준다.
test('templates/admin/menu/list.html shows a visible/hidden badge for every row including GROUP rows (no GROUP-specific branching)', () => {
    const html = readTemplate('admin/menu/list.html');
    assert.match(html, /function visibilityBadge\(visible\)/);
    assert.match(html, /visibleTd\.appendChild\(visibilityBadge\(menu\.visible\)\);/);
    // renderRow 안에서 targetType==='GROUP'을 조건으로 visibleTd/visibilityBadge를 분기하지 않는다.
    const renderRowBody = html.slice(html.indexOf('function renderRow'), html.indexOf('function loadMenus'));
    assert.doesNotMatch(renderRowBody, /targetType === 'GROUP'/);
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

// P13-T30E(Task B): targetValue는 여전히 자유 텍스트 input이고 payload 조립은 무변경이지만,
// targetType별 후보를 datalist로 제공해 오타를 줄인다. option.value는 항상 원본 enum 문자열이어야
// 하고(서버로 그대로 전송되는 값), 임의 값 입력 자체를 막는 기능(예: <select>로 완전 대체)이 아니어야
// 한다 - <input list="..."> 구조와 targetValue의 type="text"가 그대로 유지되는지로 이를 검증한다.
test('templates/admin/menu/form.html provides a datalist of targetValue candidates per targetType without restricting free text input', () => {
    const html = readTemplate('admin/menu/form.html');
    assert.match(html, /<input type="text" class="form-control" id="targetValue" name="targetValue" maxlength="255"\s*\n\s*list="targetValueCandidates">/);
    assert.match(html, /<datalist id="targetValueCandidates"><\/datalist>/);
    assert.match(html, /function updateTargetValueCandidates\(\)/);
    assert.match(html, /document\.querySelector\('#targetType'\)\.addEventListener\('change', updateTargetValueCandidates\)/);

    const candidatesBlock = html.slice(html.indexOf('var TARGET_VALUE_CANDIDATES'), html.indexOf('function updateTargetValueCandidates'));
    for (const value of ['GREETING', 'INTRODUCTION', 'HISTORY', 'LOCATION', 'COURSE', 'SPECIAL',
        'NOTICE', 'GALLERY', 'ARCHIVE', 'REVIEW']) {
        assert.match(candidatesBlock, new RegExp(`value: '${value}'`),
            `datalist candidates must include the raw enum value '${value}'`);
    }
});

// P13-T30E(Task B): datalist 후보는 편집 중인 메뉴를 불러온 뒤(targetType가 서버 값으로 갱신된 뒤)에도
// 다시 계산되어야 한다 - 그래야 수정 화면에서도 올바른 후보가 뜬다.
test('templates/admin/menu/form.html re-computes targetValue candidates after targetType is set from the fetched menu', () => {
    const html = readTemplate('admin/menu/form.html');
    const targetTypePrefillIndex = html.indexOf("document.querySelector('#targetType').value = menu.targetType;");
    const candidatesRecomputeIndex = html.indexOf('updateTargetValueCandidates();', targetTypePrefillIndex);
    const targetValuePrefillIndex = html.indexOf("document.querySelector('#targetValue').value = menu.targetValue", targetTypePrefillIndex);

    assert.notEqual(targetTypePrefillIndex, -1);
    assert.ok(candidatesRecomputeIndex !== -1 && candidatesRecomputeIndex < targetValuePrefillIndex,
        'updateTargetValueCandidates() must run after targetType is prefilled and before/around targetValue prefill');
});

// P13-T30E(Task B): 상위 메뉴 select의 표시 텍스트에만 "(그룹)"을 덧붙인다 - value/필터 조건은 무변경
// (기존 "restricts the parent select to top-level GROUP menus" 테스트가 이미 그 필터 로직 자체를
// 검증하므로 여기서는 표시 텍스트만 확인한다).
test('templates/admin/menu/form.html appends "(그룹)" to parent select option labels without changing option.value', () => {
    const html = readTemplate('admin/menu/form.html');
    assert.match(html, /option\.textContent = menu\.label \+ ' \(그룹\)';/);
    assert.match(html, /option\.value = menu\.id;/);
});

// ---------------------------------------------------------------------------------------------------------------
// P14-T9C-1: Admin List Presentation & Feedback 공통 계약(7개 목록이 같은 형태로 검증된다).
// ---------------------------------------------------------------------------------------------------------------
// 작업 트리(CRLF, Windows autocrlf)와 CI(LF) 어디서나 같은 marker로 자르도록 줄바꿈을 LF로 정규화한다.
function sliceBetween(rawSource, startMarker, endMarker) {
    const source = rawSource.replace(/\r\n/g, '\n');
    const start = source.indexOf(startMarker);
    assert.notEqual(start, -1, `marker not found: ${startMarker}`);
    const end = source.indexOf(endMarker, start + startMarker.length);
    assert.notEqual(end, -1, `end marker not found after ${startMarker}: ${endMarker}`);
    return source.slice(start, end);
}

function escapeRegExp(text) {
    return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

const T9C1 = {
    "template": "admin/menu/list.html",
    "loadFn": "loadMenus",
    "columnCountVar": "COLUMN_COUNT",
    "columnCount": 7,
    "firstTheadColumns": 7,
    "tableCount": 1,
    "emptyMessageExpr": "'등록된 메뉴가 없습니다.'",
    "errorMessage": "메뉴 목록을 불러오지 못했습니다.",
    "deleteSuccess": "삭제되었습니다.",
    "deleteErrorVar": "DELETE_ERROR",
    "patchActions": [
        {
            "marker": "orderButton.addEventListener('click'",
            "errorVar": "ORDER_CHANGE_ERROR"
        },
        {
            "marker": "visibilityButton.addEventListener('click'",
            "errorVar": "STATUS_CHANGE_ERROR"
        }
    ]
};

test(`${T9C1.template} uses the shared #admin-list-status instead of a list-local #errorMessage/showError`, () => {
    const html = readTemplate(T9C1.template);
    assert.match(html, /<div id="admin-list-status" hidden><\/div>/);
    assert.match(html, /var listStatus = document\.getElementById\('admin-list-status'\);/);
    assert.doesNotMatch(html, /id="errorMessage"/);
    assert.doesNotMatch(html, /function showError/);
    assert.doesNotMatch(html, /function clearError/);
    assert.doesNotMatch(html, /text-bg-/);
});

test(`${T9C1.template} marks every column header with scope="col" and the list table matches ${T9C1.columnCountVar}`, () => {
    const html = readTemplate(T9C1.template);
    assert.doesNotMatch(html, /<th>/);
    assert.match(html, new RegExp(`var ${T9C1.columnCountVar} = ${T9C1.columnCount};`));
    const listThead = sliceBetween(html, `<thead>`, '</thead>');
    assert.equal((listThead.match(/<th scope="col">/g) || []).length, T9C1.firstTheadColumns);
});

test(`${T9C1.template} wraps every table in .table-responsive`, () => {
    const html = readTemplate(T9C1.template);
    const tables = html.match(/<table class="table">/g) || [];
    const wrapped = html.match(/<div class="table-responsive">\s*<table class="table">/g) || [];
    assert.equal(tables.length, T9C1.tableCount);
    assert.equal(wrapped.length, T9C1.tableCount);
});

test(`${T9C1.template} ${T9C1.loadFn}() checks response.ok, renders an empty row, and replaces rows with an error row on failure`, () => {
    const html = readTemplate(T9C1.template);
    const body = sliceBetween(html, `function ${T9C1.loadFn}() {`, '\n        }\n');
    assert.match(body, /if \(!response\.ok\) \{\s*throw new Error\('HTTP ' \+ response\.status\);\s*\}\s*return response\.json\(\);/);
    assert.match(body, new RegExp(`AdminDisplay\\.renderEmptyRow\\(document, tbody, ${T9C1.columnCountVar}, ${escapeRegExp(T9C1.emptyMessageExpr)}\\);`));
    assert.match(body, new RegExp(`\\.catch\\(function \\(\\) \\{\\s*AdminDisplay\\.renderErrorRow\\(document, tbody, ${T9C1.columnCountVar}, '${escapeRegExp(T9C1.errorMessage)}'\\);`));
    // 목록 재조회는 inline status를 절대 건드리지 않는다(삭제 성공 메시지가 reload 성공/실패 후에도 남는 계약).
    assert.doesNotMatch(body, /clearStatus|showStatus/);
});

test(`${T9C1.template} reads failed-response messages safely (non-JSON body falls back) via AdminDisplay.apiErrorMessage`, () => {
    const html = readTemplate(T9C1.template);
    const body = sliceBetween(html, 'function showResponseError(response, fallback) {', '\n        }\n');
    assert.match(body, /return response\.json\(\)\s*\.catch\(function \(\) \{\s*return null;\s*\}\)/);
    assert.match(body, /AdminDisplay\.showStatus\(listStatus, AdminDisplay\.apiErrorMessage\(body, fallback\), 'error'\);/);
});

test(`${T9C1.template} delete clears status first, then on 2xx reloads and shows "${T9C1.deleteSuccess}"; failures show an error status`, () => {
    const html = readTemplate(T9C1.template);
    const handler = sliceBetween(html, "deleteButton.addEventListener('click'", '\n            });\n');
    const clearIndex = handler.indexOf('AdminDisplay.clearStatus(listStatus);');
    const deleteIndex = handler.indexOf("{method: 'DELETE'}");
    assert.ok(clearIndex !== -1 && clearIndex < deleteIndex, 'clearStatus must run before the DELETE request');
    assert.match(handler, new RegExp(`if \\(response\\.ok\\) \\{\\s*(?:\\/\\/[^\\n]*\\s*)?${T9C1.loadFn}\\(\\);\\s*(?:\\/\\/[^\\n]*\\s*)?AdminDisplay\\.showStatus\\(listStatus, '${escapeRegExp(T9C1.deleteSuccess)}', 'success'\\);`));
    assert.match(handler, new RegExp(`return showResponseError\\(response, ${T9C1.deleteErrorVar}\\);`));
    assert.match(handler, new RegExp(`\\.catch\\(function \\(\\) \\{\\s*AdminDisplay\\.showStatus\\(listStatus, ${T9C1.deleteErrorVar}, 'error'\\);`));
});

test(`${T9C1.template} PATCH actions check response.ok and surface failures instead of blindly reloading`, () => {
    const html = readTemplate(T9C1.template);
    assert.doesNotMatch(html, new RegExp(`\\.then\\(${T9C1.loadFn}\\)`));
    if (T9C1.patchActions.length === 0) {
        assert.doesNotMatch(html, /method: 'PATCH'/);
        return;
    }
    const reload = sliceBetween(html, 'function reloadOrShowError(response, fallback) {', '\n        }\n');
    assert.match(reload, new RegExp(`if \\(response\\.ok\\) \\{\\s*${T9C1.loadFn}\\(\\);\\s*return undefined;\\s*\\}\\s*return showResponseError\\(response, fallback\\);`));
    assert.equal((html.match(/method: 'PATCH'/g) || []).length, T9C1.patchActions.length);
    for (const action of T9C1.patchActions) {
        const handler = sliceBetween(html, action.marker, '\n            });\n');
        const clearIndex = handler.indexOf('AdminDisplay.clearStatus(listStatus);');
        const patchIndex = handler.indexOf("method: 'PATCH'");
        assert.ok(clearIndex !== -1 && patchIndex !== -1 && clearIndex < patchIndex, `${action.marker}: clearStatus must run before PATCH`);
        assert.match(handler, new RegExp(`return reloadOrShowError\\(response, ${action.errorVar}\\);`));
        assert.match(handler, new RegExp(`\\.catch\\(function \\(\\) \\{\\s*AdminDisplay\\.showStatus\\(listStatus, ${action.errorVar}, 'error'\\);`));
    }
});
