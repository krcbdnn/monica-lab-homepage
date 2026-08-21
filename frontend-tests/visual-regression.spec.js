// @ts-check
const { test, expect } = require('@playwright/test');

// P11-T1: 반응형 적용 검증.
// 대상은 빈 DB에서도 안정적으로 검증 가능한 공개 주요 화면으로 한정한다.
// Program/Board 상세 화면은 기존 Java/View 테스트가 이미 커버하므로 이번 범위에서 제외한다.

const VIEWPORTS = [
  { name: '375px (mobile)', width: 375, height: 812 },
  { name: '768px (tablet)', width: 768, height: 1024 },
  { name: '1440px (desktop)', width: 1440, height: 900 },
];

const PAGES = [
  {
    path: '/',
    label: '메인',
    nav: '#quick-menu',
    main: '#greeting',
    button: '#program-shortcut a.btn',
  },
  {
    path: '/pages/GREETING',
    label: '기관소개(인사말)',
    nav: null,
    main: 'h2',
    button: null,
  },
  {
    path: '/programs',
    label: '프로그램 목록',
    nav: '#program-type-filter',
    main: '#program-list',
    button: 'form button[type="submit"]',
  },
  {
    path: '/boards',
    label: '게시판 목록',
    nav: '#board-type-filter',
    main: '#board-list',
    button: 'form button[type="submit"]',
  },
];

// P13-T1: 공개 공통 Header가 도입되면서 메인 페이지의 주요 내비게이션(#quick-menu)은
// 모바일(768px 미만)에서 햄버거 토글(#nav-toggle) 뒤로 접힌다.
// 768px 이상(태블릿/데스크톱)에서는 기존과 동일하게 내비게이션이 바로 visible해야 한다.
const HEADER_NAV_SELECTOR = '#quick-menu';
const MOBILE_BREAKPOINT = 768;

for (const viewport of VIEWPORTS) {
  test.describe(`반응형 뷰포트 ${viewport.name}`, () => {
    test.use({ viewport: { width: viewport.width, height: viewport.height } });

    for (const target of PAGES) {
      test(`${target.label} (${target.path}) - 수평 overflow 없음 및 주요 요소 visible`, async ({ page }) => {
        await page.goto(target.path);

        const overflow = await page.evaluate(() => {
          const doc = document.documentElement;
          return doc.scrollWidth - doc.clientWidth;
        });
        expect(overflow, '문서 scrollWidth가 clientWidth를 초과하면 수평 overflow가 발생한 것이다').toBeLessThanOrEqual(0);

        if (target.nav === HEADER_NAV_SELECTOR && viewport.width < MOBILE_BREAKPOINT) {
          await expect(page.locator('#nav-toggle')).toBeVisible();
          await expect(page.locator('#site-nav')).toBeHidden();
        } else if (target.nav) {
          await expect(page.locator(target.nav).first()).toBeVisible();
        }
        await expect(page.locator(target.main).first()).toBeVisible();
        if (target.button) {
          await expect(page.locator(target.button).first()).toBeVisible();
        }
      });
    }
  });
}

// P13-T1: 모바일 햄버거 메뉴 동작/접근성 검증.
test.describe('모바일 햄버거 메뉴', () => {
  test.use({ viewport: { width: 375, height: 812 } });

  test('기본 상태에서는 토글 버튼만 보이고 메뉴는 닫혀 있다', async ({ page }) => {
    await page.goto('/');

    const toggle = page.locator('#nav-toggle');
    await expect(toggle).toBeVisible();
    await expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await expect(page.locator('#site-nav')).toBeHidden();
  });

  test('토글 클릭 시 메뉴가 열리고 다시 클릭하면 닫힌다', async ({ page }) => {
    await page.goto('/');
    const toggle = page.locator('#nav-toggle');

    await toggle.click();
    await expect(toggle).toHaveAttribute('aria-expanded', 'true');
    await expect(page.locator('#site-nav')).toBeVisible();
    await expect(page.locator('#quick-menu a')).toHaveCount(3);
    for (const href of ['/pages/GREETING', '/programs', '/boards']) {
      await expect(page.locator(`#quick-menu a[href="${href}"]`)).toBeVisible();
    }

    await toggle.click();
    await expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await expect(page.locator('#site-nav')).toBeHidden();
  });

  test('키보드로 토글에 포커스 후 Enter로 열고 Escape로 닫으면 포커스가 토글로 복귀한다', async ({ page }) => {
    await page.goto('/');
    const toggle = page.locator('#nav-toggle');

    await toggle.focus();
    await page.keyboard.press('Enter');
    await expect(toggle).toHaveAttribute('aria-expanded', 'true');
    await expect(page.locator('#site-nav')).toBeVisible();

    await page.keyboard.press('Escape');
    await expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await expect(page.locator('#site-nav')).toBeHidden();
    await expect(toggle).toBeFocused();
  });

  test('모바일에서 메뉴를 연 채로 데스크톱 크기로 리사이즈해도 내비게이션이 계속 보인다(CSS 우선 처리)', async ({ page }) => {
    await page.goto('/');
    const toggle = page.locator('#nav-toggle');

    await toggle.click();
    await expect(page.locator('#site-nav')).toBeVisible();

    await page.setViewportSize({ width: 1440, height: 900 });
    await expect(page.locator('#site-nav')).toBeVisible();
    await expect(page.locator('#quick-menu a')).toHaveCount(3);
  });
});
