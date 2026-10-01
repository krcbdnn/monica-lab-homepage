// @ts-check
// P14-T9E: Admin Dashboard behavior/layout contract(요약 카드 3개, 최근 게시글/프로그램 + 상세 링크, 등록 버튼 2개, empty/error,
// 1440/1024/375 layout, console/pageerror 0). 앱은 테스트 실행 전에 별도로 기동되어 있어야 한다(CI 밖 수동 실행 대상,
// admin-layout.spec.js와 동일). 표시명 검증은 /api/admin/dashboard 응답을 mock해 결정적으로 보고, 실제 API 흐름은
// runId 데이터(tracker 정리)로 1건 확인한다.
const { test, expect } = require('./support/e2e-fixtures');
const { postAdminLogin } = require('./support/admin-login');

const ADMIN_LOGIN_ID = process.env.ADMIN_LOGIN_ID;
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD;

// route mock이 일부러 만든 실패 응답에 대해 브라우저가 스스로 남기는 네트워크 로그만 허용한다(JS 오류는 허용하지 않음).
const EXPECTED_MOCK_NETWORK_ERROR = /^Failed to load resource: the server responded with a status of 500 \(Internal Server Error\)$/;

async function loginAsAdmin(context, baseURL) {
  const response = await postAdminLogin(context.request, { loginId: ADMIN_LOGIN_ID, password: ADMIN_PASSWORD }, {
    url: `${baseURL}/api/admin/login`,
  });
  expect(response.ok(), '관리자 로그인 실패 - ADMIN_LOGIN_ID/ADMIN_PASSWORD 환경변수를 확인하세요').toBeTruthy();
}

async function getXsrfToken(context) {
  const cookies = await context.cookies();
  const xsrfCookie = cookies.find((cookie) => cookie.name === 'XSRF-TOKEN');
  expect(xsrfCookie, 'XSRF-TOKEN 쿠키가 발급되어 있어야 한다').toBeTruthy();
  return xsrfCookie.value;
}

function trackErrors(page) {
  const errors = [];
  page.on('console', (message) => {
    if (message.type() === 'error') {
      errors.push({ text: message.text() });
    }
  });
  page.on('pageerror', (error) => errors.push({ text: `pageerror: ${error.message}`, pageerror: true }));
  return {
    assertClean({ allowMockNetworkError = false } = {}) {
      const unexpected = errors
        .filter((error) => !(allowMockNetworkError && !error.pageerror && EXPECTED_MOCK_NETWORK_ERROR.test(error.text)))
        .map((error) => error.text);
      expect(unexpected, unexpected.join(' | ')).toEqual([]);
    },
  };
}

function dashboardData(overrides = {}) {
  return {
    recentBoards: [
      { id: 9101, boardType: 'REVIEW', title: '후기 미지정', isPublic: true, createdAt: '2026-09-20T10:00:00', programType: null },
      { id: 9102, boardType: 'REVIEW', title: '정규 후기', isPublic: true, createdAt: '2026-09-19T10:00:00', programType: 'COURSE' },
      { id: 9103, boardType: 'REVIEW', title: '특강 후기', isPublic: false, createdAt: '2026-09-18T10:00:00', programType: 'SPECIAL' },
      { id: 9104, boardType: 'NOTICE', title: '공지 <b>태그</b>', isPublic: true, createdAt: '2026-09-17T10:00:00', programType: null },
      { id: 9105, boardType: 'GALLERY', title: '갤러리', isPublic: true, createdAt: '2026-09-16T10:00:00.123456', programType: null },
    ],
    programStatus: { OPEN: 4, CLOSED: 2 },
    quickMenus: [{ label: '기관소개 관리', url: '/admin/pages' }],
    recentPrograms: [
      { id: 9201, programType: 'COURSE', title: '정규 프로그램', recruitStatus: 'OPEN', isPublic: true, createdAt: '2026-09-21T09:00:00' },
      { id: 9202, programType: 'SPECIAL', title: '특강 프로그램', recruitStatus: 'CLOSED', isPublic: false, createdAt: '2026-09-15T09:00:00' },
    ],
    visiblePopupCount: 3,
    visibleBannerCount: 1,
    ...overrides,
  };
}

async function mockDashboard(page, data, status = 200) {
  await page.route((url) => url.pathname === '/api/admin/dashboard', (route) => route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(status === 200
      ? { success: true, data, error: null }
      : { success: false, data: null, error: { code: 'INTERNAL_SERVER_ERROR', message: '서버 오류' } }),
  }));
}

test.describe('P14-T9E: Admin Dashboard', () => {
  test.skip(!ADMIN_LOGIN_ID || !ADMIN_PASSWORD, 'ADMIN_LOGIN_ID/ADMIN_PASSWORD 환경변수가 설정되지 않아 건너뜀');

  test.beforeEach(async ({ context, baseURL }) => {
    await loginAsAdmin(context, baseURL);
  });

  test('표시명/badge는 AdminDisplay 기준(REVIEW 하위유형 포함)이고 raw enum이 보이지 않으며, 요약 카드 값이 채워진다', async ({ page }) => {
    const errors = trackErrors(page);
    await mockDashboard(page, dashboardData());
    await page.goto('/admin/dashboard');

    const boardRows = page.locator('#recent-boards-body tr');
    await expect(boardRows).toHaveCount(5);
    await expect(boardRows.nth(0).locator('td').nth(0)).toHaveText('강의 후기');
    await expect(boardRows.nth(1).locator('td').nth(0)).toHaveText('강의 후기(정규 강좌)');
    await expect(boardRows.nth(2).locator('td').nth(0)).toHaveText('강의 후기(특강)');
    await expect(boardRows.nth(3).locator('td').nth(0)).toHaveText('공지사항');
    await expect(boardRows.nth(4).locator('td').nth(0)).toHaveText('갤러리');
    await expect(boardRows.nth(0).locator('.admin-badge--neutral')).toHaveCount(1);
    await expect(boardRows.nth(2).locator('td').nth(2).locator('.admin-badge--muted')).toHaveText('비공개');
    await expect(boardRows.nth(0).locator('td').nth(2).locator('.admin-badge--positive')).toHaveText('공개');
    await expect(boardRows.nth(0).locator('td').nth(3)).toHaveText('2026.09.20');
    await expect(boardRows.nth(4).locator('td').nth(3)).toHaveText('2026.09.16');
    // 제목은 textContent로만 들어간다(태그처럼 보이는 문자열도 텍스트).
    await expect(boardRows.nth(3).locator('a')).toHaveText('공지 <b>태그</b>');
    await expect(boardRows.nth(3).locator('b')).toHaveCount(0);

    const programRows = page.locator('#recent-programs-body tr');
    await expect(programRows).toHaveCount(2);
    await expect(programRows.nth(0).locator('td').nth(0)).toHaveText('정규 강좌');
    await expect(programRows.nth(0).locator('td').nth(2).locator('.admin-badge--positive')).toHaveText('모집중');
    await expect(programRows.nth(1).locator('td').nth(0)).toHaveText('특강');
    await expect(programRows.nth(1).locator('td').nth(2).locator('.admin-badge--muted')).toHaveText('마감');
    await expect(programRows.nth(1).locator('td').nth(3).locator('.admin-badge--muted')).toHaveText('비공개');
    await expect(programRows.nth(1).locator('td').nth(4)).toHaveText('2026.09.15');

    await expect(page.locator('#program-status-open')).toHaveText('4');
    await expect(page.locator('#program-status-closed')).toHaveText('2');
    await expect(page.locator('#dashboard-popup-count')).toHaveText('3');
    await expect(page.locator('#dashboard-banner-count')).toHaveText('1');

    const tablesText = await page.locator('.admin-dashboard-recent').innerText();
    expect(tablesText).not.toMatch(/\b(NOTICE|GALLERY|ARCHIVE|REVIEW|COURSE|SPECIAL|OPEN|CLOSED)\b/);
    // sidebar와 중복되던 quick menu는 화면에서 사라졌다(API 필드는 mock에도 남아 있음).
    await expect(page.locator('#quick-menus')).toHaveCount(0);
    await expect(page.locator('#dashboard-content')).not.toContainText('기관소개 관리');
    errors.assertClean();
  });

  test('실제 API: 최근 게시글/프로그램 제목이 관리자 상세로 이동하고, 요약 카드 값이 API와 같다(비공개·마감 포함)', async ({ page, context, baseURL, tracker }) => {
    const errors = trackErrors(page);
    const xsrfToken = await getXsrfToken(context);
    const runId = `T9E-${Date.now()}`;
    const longTitle = `${runId} ${'아주긴대시보드제목'.repeat(8)}`;
    const boardRes = await context.request.post(`${baseURL}/api/admin/boards`, {
      headers: { 'X-XSRF-TOKEN': xsrfToken },
      data: { boardType: 'REVIEW', programType: 'SPECIAL', title: longTitle, content: '<p>T9E</p>', isPublic: false },
    });
    expect(boardRes.ok()).toBeTruthy();
    const board = (await boardRes.json()).data;
    tracker.track('board', board.id);
    const programRes = await context.request.post(`${baseURL}/api/admin/programs`, {
      headers: { 'X-XSRF-TOKEN': xsrfToken },
      data: { programType: 'COURSE', title: `${runId} 프로그램`, content: '<p>T9E</p>', recruitStatus: 'CLOSED', isPublic: false },
    });
    expect(programRes.ok()).toBeTruthy();
    const program = (await programRes.json()).data;
    tracker.track('program', program.id);

    // 요약 값은 전역 count라 병렬 테스트가 데이터를 만들면 바뀐다 - 별도 snapshot이 아니라 이 페이지가 실제로 받은 dashboard
    // 응답과 화면을 비교한다.
    const dashboardResponse = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/admin/dashboard');
    await page.goto('/admin/dashboard');
    const api = (await (await dashboardResponse).json()).data;

    const boardLink = page.locator(`#recent-boards-body a[href="/admin/boards/${board.id}"]`);
    await expect(boardLink).toHaveText(longTitle);
    const boardRow = page.locator('#recent-boards-body tr').filter({ has: page.locator(`a[href="/admin/boards/${board.id}"]`) });
    await expect(boardRow.locator('td').nth(0)).toHaveText('강의 후기(특강)');
    await expect(boardRow.locator('td').nth(2)).toHaveText('비공개');
    await expect(boardRow.locator('td').nth(3)).toHaveText(/^\d{4}\.\d{2}\.\d{2}$/);

    const programLink = page.locator(`#recent-programs-body a[href="/admin/programs/${program.id}"]`);
    const programRow = page.locator('#recent-programs-body tr').filter({ has: page.locator(`a[href="/admin/programs/${program.id}"]`) });
    await expect(programRow.locator('td').nth(2)).toHaveText('마감');
    await expect(programRow.locator('td').nth(3)).toHaveText('비공개');

    await expect(page.locator('#program-status-open')).toHaveText(String(api.programStatus.OPEN));
    await expect(page.locator('#program-status-closed')).toHaveText(String(api.programStatus.CLOSED));
    await expect(page.locator('#dashboard-popup-count')).toHaveText(String(api.visiblePopupCount));
    await expect(page.locator('#dashboard-banner-count')).toHaveText(String(api.visibleBannerCount));

    await boardLink.click();
    await expect(page).toHaveURL(new RegExp(`/admin/boards/${board.id}$`));
    await expect(page.locator('#admin-board-detail-content h2').first()).toHaveText(longTitle);
    await page.goBack();
    await programLink.click();
    await expect(page).toHaveURL(new RegExp(`/admin/programs/${program.id}$`));
    errors.assertClean();
  });

  test('등록 버튼 2개와 요약 카드 링크가 실제 관리 화면으로 이동한다(없는 filter query 없음)', async ({ page }) => {
    const errors = trackErrors(page);
    await page.goto('/admin/dashboard');
    await expect(page.locator('#dashboard-content .btn')).toHaveCount(2);
    await expect(page.locator('#dashboard-card-programs')).toHaveAttribute('href', '/admin/programs');
    await expect(page.locator('#dashboard-card-popups')).toHaveAttribute('href', '/admin/popups');
    await expect(page.locator('#dashboard-card-banners')).toHaveAttribute('href', '/admin/banners');

    await page.locator('#dashboard-new-board-link').click();
    await expect(page).toHaveURL(/\/admin\/boards\/new$/);
    await expect(page.locator('#admin-form-heading')).toHaveText('게시글 등록');
    await page.goto('/admin/dashboard');
    await page.locator('#dashboard-new-program-link').click();
    await expect(page).toHaveURL(/\/admin\/programs\/new$/);
    await expect(page.locator('#admin-form-heading')).toHaveText('프로그램 등록');
    await page.goto('/admin/dashboard');
    await page.locator('#dashboard-card-popups').click();
    await expect(page).toHaveURL(/\/admin\/popups$/);
    errors.assertClean();
  });

  test('데이터 0건(mock)이면 요약 0과 최근 목록 empty 행을 보인다', async ({ page }) => {
    const errors = trackErrors(page);
    await mockDashboard(page, dashboardData({
      recentBoards: [], recentPrograms: [], programStatus: { OPEN: 0, CLOSED: 0 }, visiblePopupCount: 0, visibleBannerCount: 0,
    }));
    await page.goto('/admin/dashboard');
    await expect(page.locator('#recent-boards-body td.admin-list-empty')).toHaveText('등록된 게시글이 없습니다.');
    await expect(page.locator('#recent-programs-body td.admin-list-empty')).toHaveText('등록된 프로그램이 없습니다.');
    await expect(page.locator('#program-status-open')).toHaveText('0');
    await expect(page.locator('#dashboard-popup-count')).toHaveText('0');
    await expect(page.locator('#dashboard-banner-count')).toHaveText('0');
    errors.assertClean();
  });

  test('API 오류(mock 500)면 요약은 "-", 최근 목록은 오류 행이고 pageerror가 없다', async ({ page }) => {
    const errors = trackErrors(page);
    await mockDashboard(page, null, 500);
    await page.goto('/admin/dashboard');
    await expect(page.locator('#recent-boards-body td.admin-list-empty--error')).toHaveText('최근 게시글을 불러오지 못했습니다.');
    await expect(page.locator('#recent-programs-body td.admin-list-empty--error')).toHaveText('최근 프로그램을 불러오지 못했습니다.');
    for (const id of ['#program-status-open', '#program-status-closed', '#dashboard-popup-count', '#dashboard-banner-count']) {
      await expect(page.locator(id)).toHaveText('-');
    }
    errors.assertClean({ allowMockNetworkError: true });
  });

  test('layout: 1440은 요약 3열 + 최근 목록 2열, 1024는 최근 목록 1열, 375는 전부 1열이고 가로 overflow가 없다', async ({ page }) => {
    const errors = trackErrors(page);
    await mockDashboard(page, dashboardData());

    async function measure(width) {
      await page.setViewportSize({ width, height: 900 });
      await page.goto('/admin/dashboard');
      await expect(page.locator('#recent-boards-body tr')).toHaveCount(5);
      return page.evaluate(() => {
        const rect = (el) => el.getBoundingClientRect();
        const cards = [...document.querySelectorAll('.admin-dashboard-card')].map((el) => ({ x: Math.round(rect(el).left), y: Math.round(rect(el).top) }));
        const boards = rect(document.getElementById('recent-boards'));
        const programs = rect(document.getElementById('recent-programs'));
        return {
          overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
          cardRows: new Set(cards.map((c) => c.y)).size,
          cardColumns: new Set(cards.map((c) => c.x)).size,
          recentSideBySide: Math.round(boards.top) === Math.round(programs.top) && programs.left > boards.left,
        };
      });
    }

    const desktop = await measure(1440);
    expect(desktop).toMatchObject({ cardRows: 1, cardColumns: 3, recentSideBySide: true });
    expect(desktop.overflow).toBeLessThanOrEqual(0);

    const tablet = await measure(1024);
    expect(tablet).toMatchObject({ cardRows: 1, cardColumns: 3, recentSideBySide: false });
    expect(tablet.overflow).toBeLessThanOrEqual(0);

    const mobile = await measure(375);
    expect(mobile).toMatchObject({ cardRows: 3, cardColumns: 1, recentSideBySide: false });
    expect(mobile.overflow).toBeLessThanOrEqual(0);
    errors.assertClean();
  });
});
