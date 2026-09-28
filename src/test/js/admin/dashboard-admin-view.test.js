// package.json 없이 Node.js 내장 테스트 러너로 실행한다:
//   node --test src/test/js/admin/dashboard-admin-view.test.js
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

function readTemplate(relativePath) {
    return fs.readFileSync(path.join(__dirname, '../../../main/resources/templates', relativePath), 'utf8')
        .replace(/\r\n/g, '\n');
}

function dashboard() {
    return readTemplate('admin/dashboard.html');
}

function script() {
    const source = dashboard();
    return source.slice(source.indexOf('<script>'), source.indexOf('</script>'));
}

test('templates/admin/dashboard.html inherits the common admin layout', () => {
    assert.match(dashboard(), /th:replace="~\{admin\/layout\/default :: layout\(/);
});

// P14-T9E: 데이터는 기존 dashboard API를 inline JS가 받아 그린다(SSR/polling 없음).
test('templates/admin/dashboard.html loads data once from the existing dashboard API and checks response.ok', () => {
    const js = script();
    assert.equal((js.match(/AdminFetch\.adminFetch\('\/api\/admin\/dashboard'\)/g) || []).length, 1);
    assert.match(js, /if \(!response\.ok\) \{\s*throw new Error\('HTTP ' \+ response\.status\);\s*\}\s*return response\.json\(\);/);
    assert.doesNotMatch(js, /setInterval|setTimeout|EventSource|WebSocket/);
    assert.doesNotMatch(dashboard(), /th:text|th:each/, 'the dashboard stays API-driven, not server-rendered');
});

test('templates/admin/dashboard.html offers exactly two quick actions: new board (primary) and new program (outline)', () => {
    const source = dashboard();
    assert.match(source, /<a id="dashboard-new-board-link" href="\/admin\/boards\/new" class="btn btn-primary">게시글 등록<\/a>/);
    assert.match(source, /<a id="dashboard-new-program-link" href="\/admin\/programs\/new" class="btn btn-outline-primary">프로그램 등록<\/a>/);
    assert.equal((source.match(/class="btn /g) || []).length, 2);
});

// P14-T9E: sidebar와 중복되던 quick menu 버튼 6개는 화면에서 뺐다. API의 quickMenus 필드는 계약상 유지되지만 렌더링하지 않는다.
test('templates/admin/dashboard.html no longer renders the sidebar-duplicating quick menus', () => {
    const source = dashboard();
    assert.doesNotMatch(source, /id="quick-menus"/);
    assert.doesNotMatch(script(), /quickMenus/);
    assert.doesNotMatch(source, /list-group/);
});

test('templates/admin/dashboard.html shows three summary cards linking to real list screens without made-up filters', () => {
    const source = dashboard();
    assert.match(source, /<a id="dashboard-card-programs" class="admin-dashboard-card" href="\/admin\/programs">/);
    assert.match(source, /<a id="dashboard-card-popups" class="admin-dashboard-card" href="\/admin\/popups">/);
    assert.match(source, /<a id="dashboard-card-banners" class="admin-dashboard-card" href="\/admin\/banners">/);
    assert.equal((source.match(/class="admin-dashboard-card"/g) || []).length, 3);
    // 모집중 수는 관리자 기준(비공개 포함)이라 이를 명시하고, 팝업은 공개 화면과 같은 기간 조건임을 밝힌다.
    assert.match(source, /비공개 포함 · 마감 <span id="program-status-closed">-<\/span>건/);
    assert.match(source, /노출 설정 \+ 게시 기간 안/);
    assert.doesNotMatch(source, /href="\/admin\/(programs|popups|banners)\?/);
});

test('templates/admin/dashboard.html uses a logical h2 → h3 heading hierarchy (no h4)', () => {
    const source = dashboard();
    assert.match(source, /<h2>대시보드<\/h2>/);
    assert.match(source, /<h3 id="dashboard-recent-boards-heading" class="h5 mb-0">최근 게시글<\/h3>/);
    assert.match(source, /<h3 id="dashboard-recent-programs-heading" class="h5 mb-0">최근 프로그램<\/h3>/);
    assert.doesNotMatch(source, /<h4/);
});

// P14-T9E: 표시명은 새 매핑 없이 AdminDisplay를 그대로 쓴다(REVIEW는 강의 후기 / 강의 후기(정규 강좌) / 강의 후기(특강)).
test('templates/admin/dashboard.html renders labels and badges only through AdminDisplay (no dashboard-specific mapping)', () => {
    const js = script();
    assert.match(js, /AdminDisplay\.boardTypeLabel\(board\.boardType, board\.programType\)/);
    assert.match(js, /AdminDisplay\.label\(AdminDisplay\.PROGRAM_TYPE_LABELS, program\.programType\)/);
    assert.match(js, /AdminDisplay\.label\(AdminDisplay\.RECRUIT_STATUS_LABELS, program\.recruitStatus\),\s*AdminDisplay\.recruitStatusTone\(program\.recruitStatus\)/);
    assert.match(js, /AdminDisplay\.createStateBadge\(document, board\.isPublic, '공개', '비공개'\)/);
    assert.match(js, /AdminDisplay\.createStateBadge\(document, program\.isPublic, '공개', '비공개'\)/);
    assert.doesNotMatch(js, /NOTICE|GALLERY|ARCHIVE|'REVIEW'|COURSE|SPECIAL|'OPEN'|'CLOSED'/, 'no raw enum → label mapping in the dashboard');
    assert.doesNotMatch(js, /수강 후기|특강 후기/);
});

test('templates/admin/dashboard.html links recent titles to the admin read views and formats dates as yyyy.MM.dd', () => {
    const js = script();
    assert.match(js, /titleLink\('\/admin\/boards\/' \+ encodeURIComponent\(board\.id\), board\.title\)/);
    assert.match(js, /titleLink\('\/admin\/programs\/' \+ encodeURIComponent\(program\.id\), program\.title\)/);
    assert.match(js, /link\.textContent = title;/);
    assert.match(js, /value\.slice\(0, 10\)\.replace\(\/-\/g, '\.'\)/);
});

test('templates/admin/dashboard.html shows empty rows and, on failure, "-" summaries and error rows', () => {
    const js = script();
    assert.match(js, /AdminDisplay\.renderEmptyRow\(document, tbody, columnCount, emptyMessage\)/);
    assert.match(js, /'등록된 게시글이 없습니다\.'/);
    assert.match(js, /'등록된 프로그램이 없습니다\.'/);
    assert.match(js, /\.catch\(renderFailure\)/);
    const failure = js.slice(js.indexOf('function renderFailure()'), js.indexOf("AdminFetch.adminFetch('/api/admin/dashboard')"));
    assert.match(failure, /document\.getElementById\(id\)\.textContent = '-';/);
    assert.match(failure, /AdminDisplay\.renderErrorRow\(document, document\.getElementById\('recent-boards-body'\), BOARD_COLUMN_COUNT,\s*'최근 게시글을 불러오지 못했습니다\.'\)/);
    assert.match(failure, /AdminDisplay\.renderErrorRow\(document, document\.getElementById\('recent-programs-body'\),\s*PROGRAM_COLUMN_COUNT, '최근 프로그램을 불러오지 못했습니다\.'\)/);
});

test('templates/admin/dashboard.html builds DOM without innerHTML and wraps both tables in .table-responsive', () => {
    const source = dashboard();
    assert.doesNotMatch(source, /innerHTML|insertAdjacentHTML|outerHTML/);
    assert.equal((source.match(/<div class="table-responsive">\s*<table class="table">/g) || []).length, 2);
    assert.equal((source.match(/<th scope="col">/g) || []).length, 9);
});
