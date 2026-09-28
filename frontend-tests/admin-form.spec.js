// @ts-check
// P14-T9D: Admin Form Composition behavior contract(Board/Program 목록 state 복귀 - 수정은 filter + keyword + page, 등록은
// filter + keyword만(page 0), 취소, 잘못된 query 정리, 저장 실패 시 role=alert + focus + 버튼 재활성화, 중복 submit 방지,
// Banner 이미지 사전 확인, 등록/수정 heading, 375px overflow). 앱은 테스트 실행 전에 별도로 기동되어 있어야 한다(CI 밖 수동
// 실행 대상, admin-layout.spec.js와 동일). 실제 데이터는 runId 제목으로 만들고 tracker로 정리한다.
const { test, expect, observeNavigatingCreate } = require('./support/e2e-fixtures');

const ADMIN_LOGIN_ID = process.env.ADMIN_LOGIN_ID;
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD;

// route mock이 일부러 만든 실패 응답에 대해 브라우저가 스스로 남기는 네트워크 로그만 허용한다(JS 오류는 허용하지 않음).
const EXPECTED_MOCK_NETWORK_ERROR = /^Failed to load resource: the server responded with a status of 500 \(Internal Server Error\)$/;

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

function listUrl(path, entries) {
  const search = new URLSearchParams(entries).toString();
  return search ? `${path}?${search}` : path;
}

function pathAndQuery(url) {
  const parsed = new URL(url);
  return parsed.pathname + parsed.search;
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

// 저장 성공 후 이동하는 목록 URL은 목록 화면의 T9C-2 범위 밖 page 보정(replaceState) 전에 확정되므로, 주소창이 아니라
// 실제 navigation 요청 URL로 검증한다.
function waitForListNavigation(page, path) {
  return page.waitForRequest((request) => request.isNavigationRequest() && new URL(request.url()).pathname === path);
}

test.describe('P14-T9D: Admin Form Composition', () => {
  test.skip(!ADMIN_LOGIN_ID || !ADMIN_PASSWORD, 'ADMIN_LOGIN_ID/ADMIN_PASSWORD 환경변수가 설정되지 않아 건너뜀');

  let xsrfToken;
  let runId;

  test.beforeEach(async ({ context, baseURL }) => {
    await loginAsAdmin(context, baseURL);
    xsrfToken = await getXsrfToken(context);
    runId = `T9D-${Date.now()}-${Math.floor(Math.random() * 1e6)}`;
  });

  async function createBoard(context, baseURL, title) {
    const res = await context.request.post(`${baseURL}/api/admin/boards`, {
      headers: { 'X-XSRF-TOKEN': xsrfToken },
      data: { boardType: 'NOTICE', title, content: '<p>T9D</p>', isPublic: true },
    });
    expect(res.ok()).toBeTruthy();
    return (await res.json()).data;
  }

  test('Board 수정: 상세 → 수정 → 저장이 filter + keyword + page를 유지한 목록으로 돌아가고, 취소도 같은 목록이다', async ({ page, context, baseURL, tracker }) => {
    const errors = trackErrors(page);
    const keyword = `${runId} 수정 & #`;
    const board = await createBoard(context, baseURL, `${keyword} 원본`);
    tracker.track('board', board.id);
    const state = { boardType: 'NOTICE', keyword, page: '1' };
    const expectedList = listUrl('/admin/boards', state);

    await page.goto(listUrl(`/admin/boards/${board.id}`, state));
    await expect(page.locator('#admin-detail-edit-link')).toHaveAttribute('href', listUrl(`/admin/boards/${board.id}/edit`, state));
    await expect(page.locator('#admin-detail-public-link')).toHaveAttribute('href', `/boards/${board.id}`);
    await page.locator('#admin-detail-edit-link').click();
    await expect.poll(() => pathAndQuery(page.url())).toBe(listUrl(`/admin/boards/${board.id}/edit`, state));

    // 등록/수정 heading과 문서 title, 취소 링크가 모두 수정 모드/같은 목록 state를 가리킨다.
    await expect(page.locator('#admin-form-heading')).toHaveText('게시글 수정');
    await expect(page).toHaveTitle('게시글 수정');
    await expect(page.locator('#admin-form-cancel-link')).toHaveAttribute('href', expectedList);
    await expect(page.locator('#title')).toHaveValue(`${keyword} 원본`);

    await page.locator('#title').fill(`${keyword} 수정됨`);
    const navigation = waitForListNavigation(page, '/admin/boards');
    await page.locator('#saveButton').click();
    expect(pathAndQuery((await navigation).url())).toBe(expectedList);
    await expect(page.locator('#board-list-body')).toContainText(`${keyword} 수정됨`);
    await expect(page.locator('#searchBoardType')).toHaveValue('NOTICE');
    await expect(page.locator('#searchKeyword')).toHaveValue(keyword);

    // 취소: 수정 form에서 같은 canonical 목록 state로 돌아간다.
    await page.goto(listUrl(`/admin/boards/${board.id}/edit`, state));
    const cancelNavigation = waitForListNavigation(page, '/admin/boards');
    await page.locator('#admin-form-cancel-link').click();
    expect(pathAndQuery((await cancelNavigation).url())).toBe(expectedList);
    errors.assertClean();
  });

  test('Board 등록: 목록의 등록 링크는 filter + keyword만 싣고(page 제외), 저장 후 page 0 목록으로 돌아간다', async ({ page, tracker }) => {
    const errors = trackErrors(page);
    const keyword = `${runId} 등록`;
    await page.goto(listUrl('/admin/boards', { boardType: 'NOTICE', keyword, page: '3' }));
    const newHref = listUrl('/admin/boards/new', { boardType: 'NOTICE', keyword });
    await expect(page.locator('#admin-list-new-link')).toHaveAttribute('href', newHref);

    await page.locator('#admin-list-new-link').click();
    await expect.poll(() => pathAndQuery(page.url())).toBe(newHref);
    await expect(page.locator('#admin-form-heading')).toHaveText('게시글 등록');
    await expect(page.locator('#admin-form-cancel-link')).toHaveAttribute('href', listUrl('/admin/boards', { boardType: 'NOTICE', keyword }));

    await page.locator('#boardType').selectOption('NOTICE');
    await page.locator('#title').fill(`${keyword} 새 글`);
    const created = await observeNavigatingCreate(page, tracker, 'board', { timeout: 15000 });
    const navigation = waitForListNavigation(page, '/admin/boards');
    await page.locator('#saveButton').click();
    await created.id;
    const target = new URL((await navigation).url());
    expect(target.pathname + target.search).toBe(listUrl('/admin/boards', { boardType: 'NOTICE', keyword }));
    expect(target.searchParams.has('page')).toBe(false);
    await expect(page.locator('#board-list-body')).toContainText(`${keyword} 새 글`);

    // 등록 form URL에 page가 직접 붙어 있어도 취소/저장 목적지는 page 0이다.
    await page.goto(listUrl('/admin/boards/new', { boardType: 'GALLERY', keyword, page: '5' }));
    await expect(page.locator('#admin-form-cancel-link')).toHaveAttribute('href', listUrl('/admin/boards', { boardType: 'GALLERY', keyword }));
    errors.assertClean();
  });

  test('잘못된/모르는 form query는 취소·저장 목적지에 들어가지 않는다(returnUrl 무시)', async ({ page, context, baseURL, tracker }) => {
    const errors = trackErrors(page);
    const board = await createBoard(context, baseURL, `${runId} query`);
    tracker.track('board', board.id);

    await page.goto(`/admin/boards/${board.id}/edit?boardType=INVALID&page=-1&x=1&keyword=k&returnUrl=https%3A%2F%2Fevil.example`);
    await expect(page.locator('#admin-form-cancel-link')).toHaveAttribute('href', '/admin/boards?keyword=k');
    await page.goto('/admin/programs/new?programType=NOTICE&page=abc&redirectUrl=%2F%2Fevil.example');
    await expect(page.locator('#admin-form-cancel-link')).toHaveAttribute('href', '/admin/programs');
    errors.assertClean();
  });

  test('Program 수정/등록: programType + keyword(+ 수정은 page) 목록 state로 돌아간다', async ({ page, context, baseURL, tracker }) => {
    const errors = trackErrors(page);
    const keyword = `${runId} 프로그램`;
    const res = await context.request.post(`${baseURL}/api/admin/programs`, {
      headers: { 'X-XSRF-TOKEN': xsrfToken },
      data: { programType: 'COURSE', title: `${keyword} 원본`, content: '<p>T9D</p>', recruitStatus: 'OPEN', isPublic: false },
    });
    expect(res.ok()).toBeTruthy();
    const program = (await res.json()).data;
    tracker.track('program', program.id);
    const state = { programType: 'COURSE', keyword, page: '2' };

    // 목록 행의 수정 링크 → 수정 → 저장
    await page.goto(listUrl('/admin/programs', { programType: 'COURSE', keyword }));
    const rowEditLink = page.locator('#program-list-body a', { hasText: '수정' });
    await expect(rowEditLink).toHaveAttribute('href', listUrl(`/admin/programs/${program.id}/edit`, { programType: 'COURSE', keyword }));
    await expect(page.locator('#admin-list-new-link')).toHaveAttribute('href', listUrl('/admin/programs/new', { programType: 'COURSE', keyword }));

    await page.goto(listUrl(`/admin/programs/${program.id}/edit`, state));
    await expect(page.locator('#admin-form-heading')).toHaveText('프로그램 수정');
    await expect(page.locator('#title')).toHaveValue(`${keyword} 원본`);
    await page.locator('#title').fill(`${keyword} 수정됨`);
    const navigation = waitForListNavigation(page, '/admin/programs');
    await page.locator('#saveButton').click();
    expect(pathAndQuery((await navigation).url())).toBe(listUrl('/admin/programs', state));
    await expect(page.locator('#program-list-body')).toContainText(`${keyword} 수정됨`);

    // 등록 form은 page를 싣지 않는다.
    await page.goto(listUrl('/admin/programs/new', state));
    await expect(page.locator('#admin-form-heading')).toHaveText('프로그램 등록');
    await expect(page.locator('#admin-form-cancel-link')).toHaveAttribute('href', listUrl('/admin/programs', { programType: 'COURSE', keyword }));
    errors.assertClean();
  });

  test('저장 전 검증 실패(REVIEW 후기 대상 미선택): 요청 없이 role=alert 오류에 focus, URL·입력값 유지, 버튼 활성', async ({ page }) => {
    const errors = trackErrors(page);
    const saveRequests = [];
    page.on('request', (request) => {
      if (new URL(request.url()).pathname === '/api/admin/boards' && request.method() === 'POST') {
        saveRequests.push(request.url());
      }
    });
    const formUrl = listUrl('/admin/boards/new', { boardType: 'REVIEW', keyword: runId });
    await page.goto(formUrl);
    await page.locator('#boardType').selectOption('REVIEW');
    await page.locator('#title').fill(`${runId} 후기`);
    await page.locator('#saveButton').click();

    const alert = page.locator('#errorMessage');
    await expect(alert).toBeVisible();
    await expect(alert).toHaveAttribute('role', 'alert');
    await expect(alert).toHaveText('후기 대상(정규 강좌/특강)을 선택해야 합니다.');
    await expect(alert).toBeFocused();
    await expect(page.locator('#saveButton')).toBeEnabled();
    await expect(page.locator('#title')).toHaveValue(`${runId} 후기`);
    expect(pathAndQuery(page.url())).toBe(formUrl);
    expect(saveRequests).toEqual([]);
    await expect(page.locator('#programType option')).toHaveText(['후기 대상을 선택하세요', '정규 강좌', '특강']);
    errors.assertClean();
  });

  test('저장 요청 중에는 중복 submit이 막히고, 실패하면 오류에 focus한 채 버튼이 다시 활성화된다', async ({ page, context, baseURL, tracker }) => {
    const errors = trackErrors(page);
    const board = await createBoard(context, baseURL, `${runId} 중복`);
    tracker.track('board', board.id);
    const putRequests = [];
    let releasePut;
    const putGate = new Promise((resolve) => { releasePut = resolve; });
    await page.route((url) => url.pathname === `/api/admin/boards/${board.id}`, async (route) => {
      if (route.request().method() !== 'PUT') {
        return route.continue();
      }
      putRequests.push(route.request().url());
      await putGate;
      return route.fulfill({
        status: 500, contentType: 'application/json',
        body: JSON.stringify({ success: false, data: null, error: { code: 'TEST', message: '저장할 수 없습니다(테스트).' } }),
      });
    });

    await page.goto(`/admin/boards/${board.id}/edit`);
    await expect(page.locator('#title')).toHaveValue(`${runId} 중복`);
    // 같은 tick에 두 번 클릭해도 첫 submit이 버튼을 비활성화하므로 요청은 1번뿐이다.
    await page.evaluate(() => {
      const button = document.querySelector('#saveButton');
      button.click();
      button.click();
    });
    await expect(page.locator('#saveButton')).toBeDisabled();
    releasePut();
    const alert = page.locator('#errorMessage');
    await expect(alert).toHaveText('저장할 수 없습니다(테스트).');
    await expect(alert).toBeFocused();
    await expect(page.locator('#saveButton')).toBeEnabled();
    expect(putRequests).toHaveLength(1);
    expect(pathAndQuery(page.url())).toBe(`/admin/boards/${board.id}/edit`);
    errors.assertClean({ allowMockNetworkError: true });
  });

  test('Banner: 이미지 없이 저장하면 요청 없이 "배너 이미지를 등록해야 합니다." 오류에 focus한다', async ({ page }) => {
    const errors = trackErrors(page);
    const saveRequests = [];
    page.on('request', (request) => {
      if (new URL(request.url()).pathname === '/api/admin/banners' && request.method() === 'POST') {
        saveRequests.push(request.url());
      }
    });
    await page.goto('/admin/banners/new');
    await expect(page.locator('#admin-form-heading')).toHaveText('배너 등록');
    await expect(page.locator('#admin-form-cancel-link')).toHaveAttribute('href', '/admin/banners');
    await page.locator('#title').fill(`${runId} 배너`);
    await page.locator('#sortOrder').fill('0');
    await page.locator('#saveButton').click();

    const alert = page.locator('#errorMessage');
    await expect(alert).toHaveText('배너 이미지를 등록해야 합니다.');
    await expect(alert).toBeFocused();
    await expect(page.locator('#saveButton')).toBeEnabled();
    expect(saveRequests).toEqual([]);
    errors.assertClean();
  });

  test('375px: 등록/편집 form 7개 모두 페이지 가로 overflow가 없고 [취소][저장]이 화면 안에 보인다', async ({ page }) => {
    const errors = trackErrors(page);
    await page.setViewportSize({ width: 375, height: 812 });
    for (const path of ['/admin/boards/new', '/admin/programs/new', '/admin/pages/GREETING/edit', '/admin/banners/new',
      '/admin/popups/new', '/admin/menus/new', '/admin/theme']) {
      await page.goto(path);
      await page.waitForLoadState('networkidle');
      const overflowX = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(overflowX, `${path} page overflow`).toBeLessThanOrEqual(0);
      const actions = await page.locator('.admin-form-actions .btn').evaluateAll((buttons) => buttons.map((button) => {
        const rect = button.getBoundingClientRect();
        return { text: button.textContent.trim(), left: rect.left, right: rect.right };
      }));
      expect(actions.length, `${path} action buttons`).toBeGreaterThan(0);
      for (const action of actions) {
        expect(action.left, `${path} ${action.text}`).toBeGreaterThanOrEqual(0);
        expect(action.right, `${path} ${action.text}`).toBeLessThanOrEqual(375);
      }
    }
    errors.assertClean();
  });
});
