// @ts-check
const { test, expect } = require('@playwright/test');

// fix/admin-common-fetch-load-order 회귀 검증.
// 앱은 테스트 실행 전에 별도로 기동되어 있어야 한다(server 자동 기동 없음, 다른 spec과 동일 원칙).
// 관리자 로그인 자격증명은 앱 자체가 쓰는 것과 동일한 환경변수(ADMIN_LOGIN_ID/ADMIN_PASSWORD)에서 읽는다.
// 이 spec은 CI(.github/workflows/ci.yml)에 포함되지 않으며, 기존 visual-regression.spec.js와 동일하게 수동 실행 대상이다.

const ADMIN_LOGIN_ID = process.env.ADMIN_LOGIN_ID;
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD;

const ADMIN_PAGES = [
  '/admin/dashboard',
  '/admin/boards',
  '/admin/programs',
  '/admin/banners',
  '/admin/popups',
  '/admin/files',
];

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

test.describe('관리자 화면 최초 진입 시 AdminFetch 로딩 순서 회귀 검증', () => {
  test.skip(!ADMIN_LOGIN_ID || !ADMIN_PASSWORD,
      'ADMIN_LOGIN_ID/ADMIN_PASSWORD 환경변수가 설정되지 않아 건너뜀');

  test.beforeEach(async ({ context, baseURL }) => {
    await loginAsAdmin(context, baseURL);
  });

  for (const path of ADMIN_PAGES) {
    test(`${path} 최초 진입 시 콘솔에 AdminFetch is not defined 등 pageerror가 없다`, async ({ page, baseURL }) => {
      const pageErrors = [];
      page.on('pageerror', (error) => pageErrors.push(error.message));

      await page.goto(path);
      await page.waitForLoadState('networkidle');

      expect(pageErrors, `${path}에서 발생한 pageerror: ${pageErrors.join(', ')}`).toEqual([]);
    });
  }

  test('/admin/boards 최초 진입 시 검색 버튼을 누르지 않아도 방금 생성한 게시글이 목록에 보인다', async ({ page, context, baseURL }) => {
    const xsrfToken = await getXsrfToken(context);
    const uniqueTitle = 'AdminFetch 회귀 확인용 공지 ' + Date.now();

    const createResponse = await context.request.post(`${baseURL}/api/admin/boards`, {
      headers: { 'X-XSRF-TOKEN': xsrfToken },
      data: { boardType: 'NOTICE', title: uniqueTitle, isPublic: true },
    });
    expect(createResponse.ok()).toBeTruthy();

    const pageErrors = [];
    page.on('pageerror', (error) => pageErrors.push(error.message));

    await page.goto('/admin/boards');

    // 검색 버튼을 클릭하지 않고, 최초 로딩만으로 방금 만든 게시글이 보이는지 확인한다.
    await expect(page.locator('#board-list-body')).toContainText(uniqueTitle, { timeout: 5000 });
    expect(pageErrors).toEqual([]);
  });
});
