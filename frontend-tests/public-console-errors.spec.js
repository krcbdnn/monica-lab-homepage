// @ts-check
const { test, expect } = require('@playwright/test');

// P13-T35: 공개(public) 페이지에 대한 console pageerror 회귀 검증. admin-console-errors.spec.js와
// 동일한 page.on('pageerror') 패턴을 그대로 재사용한다 - 새로운 검증 infrastructure는 만들지 않는다
// (console.error 수집까지는 확장하지 않는다 - 이 프로젝트에 의도적인 console.error 사용처가 없어
// uncaught exception만 잡는 pageerror로 충분하고, console.error까지 잡으면 서드파티 스크립트/브라우저
// 자체 경고 등으로 false-positive가 늘어날 위험이 있다).
// 앱은 테스트 실행 전에 별도로 기동되어 있어야 한다(server 자동 기동 없음, 다른 spec과 동일 원칙).
// admin-console-errors.spec.js와 마찬가지로 CI(.github/workflows/ci.yml)에는 포함되지 않고 수동
// 실행 대상이다.

const PUBLIC_PAGES = ['/', '/boards', '/programs', '/pages/GREETING'];

test.describe('공개 페이지 최초 진입 시 console pageerror 없음', () => {
  for (const path of PUBLIC_PAGES) {
    test(`${path} 최초 진입 시 콘솔에 pageerror가 없다`, async ({ page }) => {
      const pageErrors = [];
      page.on('pageerror', (error) => pageErrors.push(error.message));

      await page.goto(path);
      await page.waitForLoadState('networkidle');

      expect(pageErrors, `${path}에서 발생한 pageerror: ${pageErrors.join(', ')}`).toEqual([]);
    });
  }

  // 단순 진입만으로는 nav-toggle.js/nav-submenu.js의 실행 경로(Header GROUP/mega menu 상호작용)를
  // 전혀 타지 않으므로, 실제 production GROUP("연구소 소개")과 mega menu("전체메뉴")를 열고 닫는
  // 상호작용까지 최소 1회 포함해 그 경로에서도 pageerror가 없는지 확인한다.
  test('/ 에서 Header GROUP dropdown과 mega menu를 열고 닫아도 pageerror가 없다', async ({ page }) => {
    const pageErrors = [];
    page.on('pageerror', (error) => pageErrors.push(error.message));

    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    const groupTrigger = page.locator('#quick-menu > li.has-submenu:not([data-menu-id="all"])', {
      hasText: '연구소 소개',
    }).locator('.site-nav__trigger');
    await groupTrigger.click();
    await expect(groupTrigger).toHaveAttribute('aria-expanded', 'true');
    await page.keyboard.press('Escape');
    await expect(groupTrigger).toHaveAttribute('aria-expanded', 'false');

    const megaTrigger = page.locator('[data-menu-id="all"] > .site-nav__trigger');
    await megaTrigger.click();
    await expect(megaTrigger).toHaveAttribute('aria-expanded', 'true');
    await page.locator('.site-header__brand').click();
    await expect(megaTrigger).toHaveAttribute('aria-expanded', 'false');

    expect(pageErrors, `Header 상호작용 중 발생한 pageerror: ${pageErrors.join(', ')}`).toEqual([]);
  });

  // 모바일 hamburger accordion 경로도 별도로 확인한다(데스크톱 hover/click 경로와 이벤트 바인딩이
  // 다르므로 - matchMedia(hover:hover) 분기).
  test('/ 에서 375px 모바일 hamburger accordion을 열고 닫아도 pageerror가 없다', async ({ page }) => {
    const pageErrors = [];
    page.on('pageerror', (error) => pageErrors.push(error.message));

    await page.setViewportSize({ width: 375, height: 812 });
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    await page.locator('#nav-toggle').click();
    await expect(page.locator('#site-nav')).toBeVisible();

    const groupTrigger = page.locator('#quick-menu > li.has-submenu:not([data-menu-id="all"])', {
      hasText: '연구소 소개',
    }).locator('.site-nav__trigger');
    await groupTrigger.click();
    await expect(groupTrigger).toHaveAttribute('aria-expanded', 'true');

    await page.locator('#nav-toggle').click();
    await expect(page.locator('#site-nav')).toBeHidden();

    expect(pageErrors, `모바일 hamburger 상호작용 중 발생한 pageerror: ${pageErrors.join(', ')}`).toEqual([]);
  });
});
