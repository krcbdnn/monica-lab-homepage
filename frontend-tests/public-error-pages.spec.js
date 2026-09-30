// @ts-check
const { test, expect } = require('@playwright/test');

// P15-T5: 공개 HTML 오류 페이지(templates/error/4xx.html) 실제 브라우저 검증. public-console-errors.spec.js와
// 같은 pageerror 패턴을 쓴다 - 404 main document에 대해 브라우저가 남기는 "Failed to load resource"
// console 메시지는 정상 동작이므로 console.error는 수집하지 않는다. 500은 production debug endpoint 없이
// Java 통합 테스트(PublicErrorPageServerErrorTest)로 검증하고 여기서는 다루지 않는다.
// 앱은 테스트 실행 전에 별도로 기동되어 있어야 하며, CI에는 포함되지 않는 수동 실행 대상이다.

const MISSING_PATH = '/no-such-path';
const VIEWPORTS = [
  { width: 375, height: 812 },
  { width: 1440, height: 900 },
];

test.describe('공개 404 오류 페이지', () => {
  for (const viewport of VIEWPORTS) {
    test(`${viewport.width}px에서 HTTP 404와 오류 페이지를 가로 overflow 없이 표시한다`, async ({ page }) => {
      const pageErrors = [];
      page.on('pageerror', (error) => pageErrors.push(error.message));
      await page.setViewportSize(viewport);

      const response = await page.goto(MISSING_PATH);

      expect(response?.status()).toBe(404);
      expect(response?.headers()['content-type']).toContain('text/html');
      await expect(page).toHaveTitle('페이지를 찾을 수 없습니다 - 모니카영어교육연구소');
      await expect(page.locator('h1')).toHaveCount(1);
      await expect(page.locator('main#error-main h1')).toHaveText('페이지를 찾을 수 없습니다');
      await expect(page.locator('.error-page__code')).toHaveText('404');

      const overflow = await page.evaluate(
        () => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(overflow, `${viewport.width}px 가로 overflow(px)`).toBeLessThanOrEqual(0);

      expect(pageErrors, `${MISSING_PATH}에서 발생한 pageerror: ${pageErrors.join(', ')}`).toEqual([]);
    });
  }

  test('홈으로 가기 링크는 keyboard로 도달 가능하고 누르면 홈으로 이동한다', async ({ page }) => {
    const pageErrors = [];
    page.on('pageerror', (error) => pageErrors.push(error.message));

    await page.goto(MISSING_PATH);
    const homeLink = page.getByRole('link', { name: '홈으로 가기' });
    await expect(homeLink).toHaveAttribute('href', '/');

    await page.keyboard.press('Tab');
    await expect(homeLink).toBeFocused();

    await Promise.all([page.waitForURL((url) => url.pathname === '/'), homeLink.click()]);
    expect(pageErrors, `오류 페이지/홈 이동 중 발생한 pageerror: ${pageErrors.join(', ')}`).toEqual([]);
  });
});
