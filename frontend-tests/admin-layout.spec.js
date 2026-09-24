// @ts-check
const { test, expect } = require('@playwright/test');

// P14-T9A: Admin Critical UX Fix(logout UI / active navigation / mobile off-canvas sidebar /
// responsive table)의 interaction contract를 모은다. 앱은 테스트 실행 전에 별도로 기동되어
// 있어야 한다(server 자동 기동 없음, 다른 spec과 동일 원칙). CI(.github/workflows/ci.yml)에는
// 포함되지 않으며 admin-console-errors.spec.js와 동일하게 수동 실행 대상이다.

const ADMIN_LOGIN_ID = process.env.ADMIN_LOGIN_ID;
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD;

async function loginAsAdminApi(context, baseURL) {
  const response = await context.request.post(`${baseURL}/api/admin/login`, {
    data: { loginId: ADMIN_LOGIN_ID, password: ADMIN_PASSWORD },
  });
  expect(response.ok(), '관리자 로그인 실패 - ADMIN_LOGIN_ID/ADMIN_PASSWORD 환경변수를 확인하세요').toBeTruthy();
}

async function loginAsAdminUi(page) {
  await page.goto('/admin/login');
  await page.fill('input[name=loginId]', ADMIN_LOGIN_ID);
  await page.fill('input[name=password]', ADMIN_PASSWORD);
  await Promise.all([page.waitForNavigation(), page.click('button[type=submit]')]);
}

test.describe('P14-T9A: Admin Critical UX Fix', () => {
  test.skip(!ADMIN_LOGIN_ID || !ADMIN_PASSWORD, 'ADMIN_LOGIN_ID/ADMIN_PASSWORD 환경변수가 설정되지 않아 건너뜀');

  test.describe('Active Navigation', () => {
    test.beforeEach(async ({ context, baseURL }) => {
      await loginAsAdminApi(context, baseURL);
    });

    const cases = [
      { path: '/admin/dashboard', href: '/admin/dashboard' },
      { path: '/admin/boards', href: '/admin/boards' },
      { path: '/admin/boards/new', href: '/admin/boards' },
      { path: '/admin/programs', href: '/admin/programs' },
      { path: '/admin/menus', href: '/admin/menus' },
      { path: '/admin/theme', href: '/admin/theme' },
    ];

    for (const { path, href } of cases) {
      test(`${path} 진입 시 sidebar에서 "${href}" 항목만 active(.is-active)이다`, async ({ page }) => {
        await page.goto(path);
        const activeLinks = page.locator('#admin-sidebar a.is-active');
        await expect(activeLinks).toHaveCount(1);
        await expect(activeLinks).toHaveAttribute('href', href);
      });
    }
  });

  test.describe('Desktop(1440px)', () => {
    test.beforeEach(async ({ context, baseURL, page }) => {
      await loginAsAdminApi(context, baseURL);
      await page.setViewportSize({ width: 1440, height: 900 });
    });

    test('sidebar가 항상 보이고 mobile toggle은 숨겨지며 logout 버튼이 보인다', async ({ page }) => {
      await page.goto('/admin/dashboard');
      await expect(page.locator('#admin-sidebar')).toBeVisible();
      await expect(page.locator('#admin-sidebar-toggle')).toBeHidden();
      await expect(page.locator('#admin-logout-button')).toBeVisible();

      const overflowX = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(overflowX).toBeLessThanOrEqual(0);
    });
  });

  test.describe('Mobile Sidebar(375px)', () => {
    test.beforeEach(async ({ context, baseURL, page }) => {
      await loginAsAdminApi(context, baseURL);
      await page.setViewportSize({ width: 375, height: 812 });
    });

    test('초기 상태는 닫혀 있고, toggle로 열고 닫을 수 있으며 Escape로도 닫힌다', async ({ page }) => {
      const pageErrors = [];
      page.on('pageerror', (error) => pageErrors.push(error.message));

      await page.goto('/admin/dashboard');
      await page.waitForLoadState('networkidle');

      const toggle = page.locator('#admin-sidebar-toggle');
      const sidebar = page.locator('#admin-sidebar');

      await expect(toggle).toBeVisible();
      await expect(toggle).toHaveAttribute('aria-expanded', 'false');
      await expect(sidebar).not.toHaveClass(/is-open/);

      await toggle.click();
      await expect(sidebar).toHaveClass(/is-open/);
      await expect(toggle).toHaveAttribute('aria-expanded', 'true');

      await page.keyboard.press('Escape');
      await expect(sidebar).not.toHaveClass(/is-open/);
      await expect(toggle).toHaveAttribute('aria-expanded', 'false');

      // 다시 열고 toggle 재클릭으로도 닫히는지 확인한다.
      await toggle.click();
      await expect(sidebar).toHaveClass(/is-open/);
      await toggle.click();
      await expect(sidebar).not.toHaveClass(/is-open/);

      const overflowX = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(overflowX).toBeLessThanOrEqual(0);
      expect(pageErrors, `pageerror: ${pageErrors.join(', ')}`).toEqual([]);
    });

    test('toggle은 키보드(Tab+Enter)로도 작동한다', async ({ page }) => {
      await page.goto('/admin/dashboard');
      const toggle = page.locator('#admin-sidebar-toggle');
      await toggle.focus();
      await expect(toggle).toBeFocused();
      await page.keyboard.press('Enter');
      await expect(page.locator('#admin-sidebar')).toHaveClass(/is-open/);
    });
  });

  test.describe('Responsive Table(375px)', () => {
    test.beforeEach(async ({ context, baseURL, page }) => {
      await loginAsAdminApi(context, baseURL);
      await page.setViewportSize({ width: 375, height: 812 });
    });

    for (const path of ['/admin/boards', '/admin/programs', '/admin/menus']) {
      test(`${path}: 페이지 전체는 가로 overflow가 없고 table-responsive wrapper가 존재한다`, async ({ page }) => {
        const pageErrors = [];
        page.on('pageerror', (error) => pageErrors.push(error.message));

        await page.goto(path);
        await page.waitForLoadState('networkidle');

        await expect(page.locator('.table-responsive table')).toHaveCount(1);

        const overflowX = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
        expect(overflowX).toBeLessThanOrEqual(0);
        expect(pageErrors, `pageerror: ${pageErrors.join(', ')}`).toEqual([]);
      });
    }
  });

  test.describe('Logout', () => {
    test('로그인 → 로그아웃 → /admin/login 이동 → 이후 protected URL 재접근 시 차단', async ({ page }) => {
      await loginAsAdminUi(page);
      await page.goto('/admin/dashboard');

      const logoutButton = page.locator('#admin-logout-button');
      await expect(logoutButton).toBeVisible();
      await logoutButton.click();

      await page.waitForURL('**/admin/login');
      expect(page.url()).toContain('/admin/login');

      // 세션이 실제로 무효화됐는지 - 다시 보호된 화면에 직접 접근하면 기존 인증 정책(로그인 화면으로
      // redirect)이 그대로 적용되어야 한다.
      await page.goto('/admin/dashboard');
      await page.waitForURL('**/admin/login');
      expect(page.url()).toContain('/admin/login');
    });

    test('로그아웃 API가 실패하면 로그인 화면으로 이동하지 않고 버튼이 다시 활성화되며 실패를 알린다', async ({ page }) => {
      await loginAsAdminUi(page);
      await page.goto('/admin/dashboard');

      await page.route('**/api/admin/logout', (route) => route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ success: false, data: null, error: { code: 'INTERNAL_SERVER_ERROR', message: '서버 오류가 발생했습니다.' } }),
      }));

      const pageErrors = [];
      page.on('pageerror', (error) => pageErrors.push(error.message));

      let dialogMessage = null;
      page.once('dialog', async (dialog) => {
        dialogMessage = dialog.message();
        await dialog.accept();
      });

      const logoutButton = page.locator('#admin-logout-button');
      await logoutButton.click();
      await page.waitForTimeout(500);

      expect(page.url()).not.toContain('/admin/login');
      await expect(logoutButton).toBeEnabled();
      expect(dialogMessage).toBeTruthy();
      expect(pageErrors, `pageerror: ${pageErrors.join(', ')}`).toEqual([]);

      // 이 테스트가 세션을 실제로 종료시키지 않았으므로(logout API를 가로챘을 뿐) 다음 테스트를 위한
      // 별도 정리는 불필요하다 - 각 테스트가 독립적으로 로그인한다.
    });
  });
});
