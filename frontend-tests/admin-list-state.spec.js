// @ts-check
// P14-T9C-2: Admin List State & Navigation(URL = 목록 state, replaceState, 범위 밖 page/삭제 후 fallback, 상세 ↔ 목록 복귀,
// stale response guard)의 behavior contract. 앱은 테스트 실행 전에 별도로 기동되어 있어야 한다(server 자동 기동 없음,
// admin-layout.spec.js와 동일하게 CI 밖 수동 실행 대상). 실제 pagination 통합은 Board 21건 dataset 1개(beforeAll 1회 생성,
// tracker로 정리)로만 검증하고, File/race처럼 client state machine이 핵심인 경우는 page.route mock을 쓴다.
const { test, expect, createTracker } = require('./support/e2e-fixtures');

const ADMIN_LOGIN_ID = process.env.ADMIN_LOGIN_ID;
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD;

async function loginAsAdmin(context, baseURL) {
  const response = await context.request.post(`${baseURL}/api/admin/login`, {
    data: { loginId: ADMIN_LOGIN_ID, password: ADMIN_PASSWORD },
  });
  expect(response.ok(), '관리자 로그인 실패 - ADMIN_LOGIN_ID/ADMIN_PASSWORD 환경변수를 확인하세요').toBeTruthy();
}

async function getXsrfToken(context) {
  const cookies = await context.cookies();
  const xsrfCookie = cookies.find((cookie) => cookie.name === 'XSRF-TOKEN');
  expect(xsrfCookie, 'XSRF-TOKEN 쿠키가 발급되어 있어야 한다').toBeTruthy();
  return xsrfCookie.value;
}

function pathAndQuery(page) {
  const url = new URL(page.url());
  return url.pathname + url.search;
}

function listUrl(path, entries) {
  const params = new URLSearchParams(entries);
  const search = params.toString();
  return search ? `${path}?${search}` : path;
}

// JS 오류와 예상하지 못한 console error를 모은다(이 spec의 mock은 전부 200 응답이라 허용할 네트워크 오류가 없다).
function trackErrors(page) {
  const errors = [];
  page.on('console', (message) => {
    if (message.type() === 'error') {
      errors.push(`console: ${message.text()}`);
    }
  });
  page.on('pageerror', (error) => errors.push(`pageerror: ${error.message}`));
  return {
    assertClean() {
      expect(errors, errors.join(' | ')).toEqual([]);
    },
  };
}

// 브라우저 안의 이미 도착한 응답 처리(then 체인)가 끝날 때까지 한 tick 이상 기다린다.
async function settle(page) {
  await page.evaluate(() => new Promise((resolve) => setTimeout(resolve, 150)));
}

function pageResponse(content, { page, size = 20, totalElements }) {
  const totalPages = Math.ceil(totalElements / size);
  return {
    success: true,
    error: null,
    data: { content, page, size, totalElements, totalPages, last: page + 1 >= totalPages },
  };
}

test.describe('P14-T9C-2: Admin List State & Navigation', () => {
  test.skip(!ADMIN_LOGIN_ID || !ADMIN_PASSWORD, 'ADMIN_LOGIN_ID/ADMIN_PASSWORD 환경변수가 설정되지 않아 건너뜀');

  test.beforeEach(async ({ context, baseURL }) => {
    await loginAsAdmin(context, baseURL);
  });

  // ---------------------------------------------------------------------------------------------------------
  // Board 실제 API: 같은 keyword의 NOTICE 21건(= 20 + 1, 두 번째 page에 1건) dataset을 describe 전체에서 1회만 만든다.
  // 마지막 테스트가 두 번째 page의 1건을 삭제하므로 serial로 순서를 고정한다.
  // ---------------------------------------------------------------------------------------------------------
  test.describe.serial('Board 실제 pagination dataset', () => {
    const runId = `${Date.now()}-${Math.floor(Math.random() * 1e6)}`;
    // 한글/공백/&를 포함해 URL encoding도 함께 검증한다(제목과 본문 모두 이 문자열을 포함).
    const keyword = `목록상태 ${runId} a&b`;
    let resourceCleanup;
    const datasetIds = [];

    test.beforeAll(async ({ browser, baseURL }) => {
      test.setTimeout(120000);
      resourceCleanup = createTracker({ baseURL });
      const context = await browser.newContext();
      try {
        await loginAsAdmin(context, baseURL);
        const xsrfToken = await getXsrfToken(context);
        for (let i = 0; i < 21; i++) {
          const res = await context.request.post(`${baseURL}/api/admin/boards`, {
            headers: { 'X-XSRF-TOKEN': xsrfToken },
            data: { boardType: 'NOTICE', title: `${keyword} #${i}`, content: '<p>T9C-2</p>', isPublic: true },
          });
          expect(res.ok()).toBeTruthy();
          datasetIds.push(resourceCleanup.tracker.track('board', (await res.json()).data.id));
        }
      } finally {
        await context.close();
      }
    });

    test.afterAll(async () => {
      if (!resourceCleanup) {
        return;
      }
      try {
        await resourceCleanup.tracker.cleanup();
      } finally {
        await resourceCleanup.dispose();
      }
    });

    const secondPageUrl = () => listUrl('/admin/boards', { boardType: 'NOTICE', keyword, page: '1' });

    test('filter/keyword/page가 URL에 반영되고 reload·상세 "목록으로"·Back/Forward에서 같은 state로 복원된다', async ({ page }) => {
      const errors = trackErrors(page);
      await page.goto('/admin/boards');
      await page.locator('#searchBoardType').selectOption('NOTICE');
      await expect.poll(() => pathAndQuery(page)).toBe('/admin/boards?boardType=NOTICE');

      await page.locator('#searchKeyword').fill(keyword);
      await page.locator('#searchForm button[type="submit"]').click();
      await expect.poll(() => pathAndQuery(page)).toBe(listUrl('/admin/boards', { boardType: 'NOTICE', keyword }));
      await expect(page.locator('#board-list-body tr')).toHaveCount(20);

      await page.locator('#next-page').click();
      await expect.poll(() => pathAndQuery(page)).toBe(secondPageUrl());
      await expect(page.locator('#board-list-body tr')).toHaveCount(1);
      // 21건이 거의 같은 시각에 만들어져 createdAt 동률 순서는 보장되지 않으므로, 두 번째 page의 1건을 실제 링크에서 읽는다.
      const secondPageHref = await page.locator('#board-list-body td:first-child a').getAttribute('href');
      const secondPageMatch = /^\/admin\/boards\/(\d+)\?/.exec(secondPageHref || '');
      expect(secondPageMatch, `unexpected detail href: ${secondPageHref}`).not.toBeNull();
      const secondPageBoardId = Number(secondPageMatch[1]);
      expect(datasetIds).toContain(secondPageBoardId);

      // reload: URL만으로 filter/keyword/page가 복원된다.
      await page.reload();
      await expect(page.locator('#searchBoardType')).toHaveValue('NOTICE');
      await expect(page.locator('#searchKeyword')).toHaveValue(keyword);
      await expect(page.locator('#board-list-body tr')).toHaveCount(1);
      await expect(page.locator('#prev-page')).toBeEnabled();
      await expect(page.locator('#next-page')).toBeDisabled();
      expect(pathAndQuery(page)).toBe(secondPageUrl());

      // 상세 URL은 allowlist된 목록 state를 싣고, "목록으로"만 그 상태로 돌아간다(수정 링크는 query 없음).
      const detailUrl = listUrl(`/admin/boards/${secondPageBoardId}`, { boardType: 'NOTICE', keyword, page: '1' });
      await page.locator(`#board-list-body a[href^="/admin/boards/${secondPageBoardId}?"]`).click();
      await expect.poll(() => pathAndQuery(page)).toBe(detailUrl);
      await expect(page.locator('#admin-detail-list-link')).toHaveAttribute('href', secondPageUrl());
      // P14-T9D: 수정 링크도 같은 목록 state(filter + keyword + page)를 싣는다(T9C-2의 "수정 링크 query 없음" 계약 대체).
      await expect(page.locator('#admin-detail-edit-link'))
        .toHaveAttribute('href', listUrl(`/admin/boards/${secondPageBoardId}/edit`, { boardType: 'NOTICE', keyword, page: '1' }));
      await expect(page.locator('#admin-detail-public-link')).toHaveAttribute('href', `/boards/${secondPageBoardId}`);
      await page.locator('#admin-detail-list-link').click();
      await expect.poll(() => pathAndQuery(page)).toBe(secondPageUrl());
      await expect(page.locator('#searchBoardType')).toHaveValue('NOTICE');
      await expect(page.locator('#searchKeyword')).toHaveValue(keyword);
      await expect(page.locator('#board-list-body tr')).toHaveCount(1);

      // browser Back/Forward: 목록 state 변경은 replaceState라 history에는 목록 1칸 + 상세만 남는다.
      await page.locator(`#board-list-body a[href^="/admin/boards/${secondPageBoardId}?"]`).click();
      await expect.poll(() => pathAndQuery(page)).toBe(detailUrl);
      await page.goBack();
      await expect.poll(() => pathAndQuery(page)).toBe(secondPageUrl());
      await expect(page.locator('#searchBoardType')).toHaveValue('NOTICE');
      await expect(page.locator('#board-list-body tr')).toHaveCount(1);
      await page.goForward();
      await expect.poll(() => pathAndQuery(page)).toBe(detailUrl);
      await expect(page.locator('#admin-detail-list-link')).toHaveAttribute('href', secondPageUrl());
      errors.assertClean();
    });

    test('범위 밖 page(데이터 있음)는 마지막 유효 page로 1회만 재조회되고, 전체 0건 + page>0은 재조회 없이 page 0이 된다', async ({ page }) => {
      const errors = trackErrors(page);
      const listRequests = [];
      page.on('request', (request) => {
        const url = new URL(request.url());
        if (url.pathname === '/api/admin/boards') {
          listRequests.push(url.searchParams.get('page'));
        }
      });

      // CASE A: 21건(2 page)인데 page=9999 → page=1로 정규화, 요청은 9999 → 1 두 번뿐.
      await page.goto(listUrl('/admin/boards', { boardType: 'NOTICE', keyword, page: '9999' }));
      await expect.poll(() => pathAndQuery(page)).toBe(secondPageUrl());
      await expect(page.locator('#board-list-body tr')).toHaveCount(1);
      await expect(page.locator('#board-list-body td.admin-list-empty')).toHaveCount(0);
      await settle(page);
      expect(listRequests).toEqual(['9999', '1']);

      // CASE B: 조건에 맞는 글이 0건인데 page=3 → 재조회 없이 page만 0으로 정규화, empty 행 표시.
      listRequests.length = 0;
      const nothing = `없는검색어-${runId}`;
      await page.goto(listUrl('/admin/boards', { keyword: nothing, page: '3' }));
      await expect(page.locator('#board-list-body td.admin-list-empty')).toHaveText('조건에 맞는 게시글이 없습니다.');
      await expect.poll(() => pathAndQuery(page)).toBe(listUrl('/admin/boards', { keyword: nothing }));
      await expect(page.locator('#prev-page')).toBeDisabled();
      await settle(page);
      expect(listRequests).toEqual(['3']);
      errors.assertClean();
    });

    // dataset을 바꾸는 테스트라 serial group의 마지막에 둔다.
    test('두 번째 page의 마지막 1건을 삭제하면 page 0으로 fallback하고 "삭제되었습니다." status가 유지된다', async ({ page }) => {
      const errors = trackErrors(page);
      await page.goto(secondPageUrl());
      await expect(page.locator('#board-list-body tr')).toHaveCount(1);

      page.once('dialog', (dialog) => dialog.accept());
      await page.locator('#board-list-body button', { hasText: '삭제' }).click();

      await expect.poll(() => pathAndQuery(page)).toBe(listUrl('/admin/boards', { boardType: 'NOTICE', keyword }));
      await expect(page.locator('#board-list-body tr')).toHaveCount(20);
      await expect(page.locator('#prev-page')).toBeDisabled();
      await expect(page.locator('#next-page')).toBeDisabled();
      const status = page.locator('#admin-list-status');
      await expect(status).toHaveAttribute('role', 'status');
      await expect(status).toHaveText('삭제되었습니다.');
      await expect(status).toBeVisible();
      errors.assertClean();
    });
  });

  test('잘못된/모르는 query는 400·예외 없이 canonical URL로 정리된다(Board/Program/File)', async ({ page }) => {
    const errors = trackErrors(page);
    const failedApiResponses = [];
    page.on('response', (response) => {
      if (new URL(response.url()).pathname.startsWith('/api/admin/') && response.status() >= 400) {
        failedApiResponses.push(`${response.status()} ${response.url()}`);
      }
    });

    await page.goto('/admin/boards?boardType=INVALID&page=-3&x=1&keyword=%20%20&returnUrl=https%3A%2F%2Fevil.example');
    await expect.poll(() => pathAndQuery(page)).toBe('/admin/boards');
    await expect(page.locator('#searchBoardType')).toHaveValue('');
    await expect(page.locator('#searchKeyword')).toHaveValue('');

    await page.goto('/admin/boards?boardType=%3Cscript%3Ealert(1)%3C%2Fscript%3E&page=1.5');
    await expect.poll(() => pathAndQuery(page)).toBe('/admin/boards');

    await page.goto('/admin/programs?programType=NOTICE&page=abc&keyword=%3Cimg%20src%3Dx%20onerror%3Dalert(1)%3E');
    await expect.poll(() => pathAndQuery(page))
      .toBe(listUrl('/admin/programs', { keyword: '<img src=x onerror=alert(1)>' }));
    await expect(page.locator('#searchProgramType')).toHaveValue('');
    await expect(page.locator('#searchKeyword')).toHaveValue('<img src=x onerror=alert(1)>');

    await page.goto('/admin/files?page=99999999999&keyword=x');
    await expect.poll(() => pathAndQuery(page)).toBe('/admin/files');
    await settle(page);

    expect(failedApiResponses, failedApiResponses.join(', ')).toEqual([]);
    errors.assertClean();
  });

  test('Program: programType/keyword state가 상세 → "목록으로"와 새 탭 상세에서도 복원된다', async ({ page, context, baseURL, tracker }) => {
    const errors = trackErrors(page);
    const xsrfToken = await getXsrfToken(context);
    const keyword = `프로그램상태 ${Date.now()} #1`;
    const res = await context.request.post(`${baseURL}/api/admin/programs`, {
      headers: { 'X-XSRF-TOKEN': xsrfToken },
      data: { programType: 'COURSE', title: keyword, content: '<p>T9C-2</p>', recruitStatus: 'OPEN', isPublic: true },
    });
    expect(res.ok()).toBeTruthy();
    const program = (await res.json()).data;
    tracker.track('program', program.id);

    const expectedList = listUrl('/admin/programs', { programType: 'COURSE', keyword });
    await page.goto('/admin/programs');
    await page.locator('#searchProgramType').selectOption('COURSE');
    await page.locator('#searchKeyword').fill(keyword);
    await page.locator('#searchForm button[type="submit"]').click();
    await expect.poll(() => pathAndQuery(page)).toBe(expectedList);
    await expect(page.locator('#program-list-body tr')).toHaveCount(1);

    const detailLink = page.locator(`#program-list-body a[href^="/admin/programs/${program.id}?"]`);
    const detailHref = await detailLink.getAttribute('href');
    expect(detailHref).toBe(listUrl(`/admin/programs/${program.id}`, { programType: 'COURSE', keyword }));
    await detailLink.click();
    await expect(page.locator('#admin-detail-list-link')).toHaveAttribute('href', expectedList);
    // P14-T9D: 수정 링크도 같은 목록 state를 싣고, 공개 페이지 링크에는 싣지 않는다.
    await expect(page.locator('#admin-detail-edit-link'))
      .toHaveAttribute('href', listUrl(`/admin/programs/${program.id}/edit`, { programType: 'COURSE', keyword }));
    await expect(page.locator('#admin-detail-public-link')).toHaveAttribute('href', `/programs/${program.id}`);
    await page.locator('#admin-detail-list-link').click();
    await expect.poll(() => pathAndQuery(page)).toBe(expectedList);
    await expect(page.locator('#searchProgramType')).toHaveValue('COURSE');
    await expect(page.locator('#searchKeyword')).toHaveValue(keyword);
    await expect(page.locator('#program-list-body tr')).toHaveCount(1);

    // 새 탭(같은 로그인 context의 새 page)에서 상세 URL을 직접 열어도 "목록으로"가 목록 state를 복원한다.
    const newTab = await context.newPage();
    const newTabErrors = trackErrors(newTab);
    await newTab.goto(detailHref);
    await expect(newTab.locator('#admin-detail-list-link')).toHaveAttribute('href', expectedList);
    await newTab.locator('#admin-detail-list-link').click();
    await expect.poll(() => pathAndQuery(newTab)).toBe(expectedList);
    await expect(newTab.locator('#searchProgramType')).toHaveValue('COURSE');
    await expect(newTab.locator('#program-list-body tr')).toHaveCount(1);
    await newTab.close();
    newTabErrors.assertClean();
    errors.assertClean();
  });

  test('File(mock): page state가 URL/reload로 복원되고 범위 밖 page는 마지막 유효 page로 1회 재조회된다', async ({ page }) => {
    const errors = trackErrors(page);
    const requestedPages = [];
    const totalElements = 25;
    await page.route((url) => url.pathname === '/api/admin/files', (route) => {
      const requested = Number(new URL(route.request().url()).searchParams.get('page'));
      requestedPages.push(requested);
      const start = requested * 20;
      const count = Math.max(0, Math.min(20, totalElements - start));
      const content = Array.from({ length: count }, (_, i) => ({
        id: 900000 + start + i, originalName: `mock-${start + i}.png`, url: '#', fileType: 'IMAGE',
        contentType: 'image/png', size: 10, createdAt: '2026-01-01T00:00:00',
      }));
      return route.fulfill({
        status: 200, contentType: 'application/json',
        body: JSON.stringify(pageResponse(content, { page: requested, totalElements })),
      });
    });

    await page.goto('/admin/files');
    await expect(page.locator('#file-list-body tr')).toHaveCount(20);
    await page.locator('#next-page').click();
    await expect.poll(() => pathAndQuery(page)).toBe('/admin/files?page=1');
    await expect(page.locator('#file-list-body tr')).toHaveCount(5);

    await page.reload();
    await expect(page.locator('#file-list-body tr')).toHaveCount(5);
    await expect(page.locator('#prev-page')).toBeEnabled();
    await expect(page.locator('#next-page')).toBeDisabled();

    requestedPages.length = 0;
    await page.goto('/admin/files?page=7');
    await expect.poll(() => pathAndQuery(page)).toBe('/admin/files?page=1');
    await expect(page.locator('#file-list-body tr')).toHaveCount(5);
    await settle(page);
    expect(requestedPages).toEqual([7, 1]);

    await page.locator('#prev-page').click();
    await expect.poll(() => pathAndQuery(page)).toBe('/admin/files');
    await expect(page.locator('#file-list-body tr')).toHaveCount(20);
    errors.assertClean();
  });

  test('Race(mock): 늦게 도착한 이전 filter 응답이 최신 filter의 URL/목록을 덮지 않는다', async ({ page }) => {
    const errors = trackErrors(page);
    let releaseNotice;
    const noticeGate = new Promise((resolve) => { releaseNotice = resolve; });
    await page.route((url) => url.pathname === '/api/admin/boards', async (route) => {
      const boardType = new URL(route.request().url()).searchParams.get('boardType') || 'ALL';
      if (boardType === 'NOTICE') {
        await noticeGate; // NOTICE 응답을 REVIEW 응답보다 늦게 보낸다.
      }
      const row = { id: 910001, title: `race-${boardType}`, boardType: boardType === 'ALL' ? 'NOTICE' : boardType, programType: null, isPublic: true };
      return route.fulfill({
        status: 200, contentType: 'application/json', body: JSON.stringify(pageResponse([row], { page: 0, totalElements: 1 })),
      });
    });

    await page.goto('/admin/boards');
    await expect(page.locator('#board-list-body')).toContainText('race-ALL');

    const noticeResponse = page.waitForResponse((response) => new URL(response.url()).searchParams.get('boardType') === 'NOTICE');
    await page.locator('#searchBoardType').selectOption('NOTICE');
    await page.locator('#searchBoardType').selectOption('REVIEW');
    await expect(page.locator('#board-list-body')).toContainText('race-REVIEW');
    releaseNotice();
    await noticeResponse;
    await settle(page);

    await expect(page.locator('#board-list-body')).toContainText('race-REVIEW');
    await expect(page.locator('#board-list-body')).not.toContainText('race-NOTICE');
    expect(pathAndQuery(page)).toBe('/admin/boards?boardType=REVIEW');
    errors.assertClean();
  });

  test('Race + fallback(mock): 범위 밖 fallback을 유발할 늦은 응답은 사용자가 바꾼 새 filter 화면을 덮거나 재조회를 만들지 않는다', async ({ page }) => {
    const errors = trackErrors(page);
    const requests = [];
    let releaseOutOfRange;
    const outOfRangeGate = new Promise((resolve) => { releaseOutOfRange = resolve; });
    await page.route((url) => url.pathname === '/api/admin/boards', async (route) => {
      const params = new URL(route.request().url()).searchParams;
      const requestedPage = Number(params.get('page'));
      const boardType = params.get('boardType') || 'ALL';
      requests.push(`${boardType}:${requestedPage}`);
      if (requestedPage === 9) {
        await outOfRangeGate;
        return route.fulfill({
          status: 200, contentType: 'application/json', body: JSON.stringify(pageResponse([], { page: 9, totalElements: 1 })),
        });
      }
      const row = { id: 920001, title: `fallback-${boardType}`, boardType: boardType === 'ALL' ? 'NOTICE' : boardType, programType: null, isPublic: true };
      return route.fulfill({
        status: 200, contentType: 'application/json', body: JSON.stringify(pageResponse([row], { page: requestedPage, totalElements: 1 })),
      });
    });

    await page.goto('/admin/boards?page=9');
    await page.locator('#searchBoardType').selectOption('GALLERY');
    await expect(page.locator('#board-list-body')).toContainText('fallback-GALLERY');
    const outOfRangeResponse = page.waitForResponse((response) => new URL(response.url()).searchParams.get('page') === '9');
    releaseOutOfRange();
    await outOfRangeResponse;
    await settle(page);

    expect(requests).toEqual(['ALL:9', 'GALLERY:0']);
    expect(pathAndQuery(page)).toBe('/admin/boards?boardType=GALLERY');
    await expect(page.locator('#board-list-body')).toContainText('fallback-GALLERY');
    errors.assertClean();
  });
});
