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

        if (target.nav) {
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
