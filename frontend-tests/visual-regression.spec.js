// @ts-check
const { test, expect } = require('@playwright/test');

// 여러 describe 블록(Hero 배너 캐러셀, 긴 제목 오버플로우, 메인 카드 폭)이 공통으로 쓰는
// 관리자 로그인/CSRF 헬퍼. 각 블록은 이 두 함수만 공유하고, 무엇을 생성/삭제할지는 각자 정의한다.
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

// P11-T1: 반응형 적용 검증.
// 대상은 빈 DB에서도 안정적으로 검증 가능한 공개 주요 화면으로 한정한다.
// Program/Board 상세 화면은 기존 Java/View 테스트가 이미 커버하므로 이번 범위에서 제외한다.

const VIEWPORTS = [
  { name: '375px (mobile)', width: 375, height: 812 },
  { name: '768px (tablet)', width: 768, height: 1024 },
  { name: '1024px (tablet landscape)', width: 1024, height: 768 },
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

// P13-T9: Hero 배너 캐러셀.
// 로컬/Docker DB에 이미 등록돼 있을 수 있는 배너 개수(0개일 수도, N개일 수도 있음)에 의존하지 않기 위해,
// 이 describe 블록은 매 테스트마다 관리자 API로 고유한 제목의 배너 3개를 만들고 끝나면 그 3개만 삭제한다.
// 그 외 기존에 등록돼 있던 배너는 절대 건드리지 않는다.
//
// "정확히 0개/1개"일 때의 계약(hero__empty 유지, 컨트롤 전부 숨김)은 여기서 검증하지 않는다.
// 실제 서비스 DB에서 배너를 전부 지우거나 정확히 1개만 남기는 건 파괴적인 작업이라 안전하게 재현할 수 없고,
// 해당 두 상태는 이미 격리된 DB에서 도는 HomeControllerTest(heroShowsEmptyStateWhenNoBannersExist,
// heroHidesControlsWhenOnlyOneBannerExists)가 결정적으로 검증하고 있으므로 이 파일의 범위에서 제외한다
// (Program/Board 상세를 이 파일에서 제외한 것과 동일한 원칙, 4-6행 주석 참고).
//
// 실제 5초 자동전환 타이머가 정말 동작하는지 확인하는 케이스는 아래 "기본 상태에서 5초 후 다음 배너로
// 자동 전환된다" 1건으로 최소화했다. 나머지 케이스(일시정지/hover/reduced-motion)는 상태(재생-일시정지 버튼의
// 표시 텍스트/aria-label, hidden 속성) 변화만 즉시 확인하거나 5초보다 훨씬 짧은 대기(1.5초)로 "전환되지
// 않았음"만 확인한다.
test.describe('Hero 배너 캐러셀', () => {
  test.skip(!ADMIN_LOGIN_ID || !ADMIN_PASSWORD, 'ADMIN_LOGIN_ID/ADMIN_PASSWORD 환경변수가 설정되지 않아 건너뜀');

  async function createBanner(context, baseURL, xsrfToken, title, sortOrder) {
    const response = await context.request.post(`${baseURL}/api/admin/banners`, {
      headers: { 'X-XSRF-TOKEN': xsrfToken },
      // sortOrder를 매우 큰 값으로 주어, 기존에 등록돼 있을 수 있는 실제 배너보다 항상 뒤쪽에 오도록 한다.
      // image도 sortOrder 기반으로 배너마다 다른 경로를 줘서, 네트워크 요청을 배너별로 구분할 수 있게 한다
      // (실제 파일 존재 여부는 이 테스트들의 관심사가 아니다 - 요청이 발생하는 시점만 본다).
      data: { title, image: `/api/files/${sortOrder}`, sortOrder, isVisible: true },
    });
    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    return body.data.id;
  }

  async function deleteBanner(context, baseURL, xsrfToken, id) {
    await context.request.delete(`${baseURL}/api/admin/banners/${id}`, {
      headers: { 'X-XSRF-TOKEN': xsrfToken },
    });
  }

  let xsrfToken;
  let bannerIds;
  let titles;

  test.beforeEach(async ({ context, baseURL }) => {
    await loginAsAdmin(context, baseURL);
    xsrfToken = await getXsrfToken(context);

    const runId = Date.now();
    titles = [`캐러셀 테스트 A ${runId}`, `캐러셀 테스트 B ${runId}`, `캐러셀 테스트 C ${runId}`];
    bannerIds = [];
    for (let i = 0; i < titles.length; i++) {
      const id = await createBanner(context, baseURL, xsrfToken, titles[i], 900000 + i);
      bannerIds.push(id);
    }
  });

  test.afterEach(async ({ context, baseURL }) => {
    for (const id of bannerIds) {
      await deleteBanner(context, baseURL, xsrfToken, id);
    }
  });

  function slideFor(page, title) {
    return page.locator('.hero__slide').filter({ has: page.locator(`img[alt="${title}"]`) });
  }

  function indicatorFor(page, title) {
    return page.getByRole('button', { name: `${title} 배너로 이동` });
  }

  test('배너 2개 이상이면 이전/다음/재생-일시정지/인디케이터 컨트롤이 모두 보인다', async ({ page }) => {
    await page.goto('/');

    await expect(page.locator('#hero-prev')).toBeVisible();
    await expect(page.locator('#hero-next')).toBeVisible();
    await expect(page.locator('#hero-play-pause')).toBeVisible();
    await expect(indicatorFor(page, titles[0])).toBeVisible();
    await expect(indicatorFor(page, titles[1])).toBeVisible();
    await expect(indicatorFor(page, titles[2])).toBeVisible();
  });

  test('인디케이터 클릭 시 해당 배너로 즉시 이동한다', async ({ page }) => {
    await page.goto('/');

    await indicatorFor(page, titles[1]).click();
    await expect(slideFor(page, titles[1])).not.toHaveAttribute('hidden', '');
    await expect(indicatorFor(page, titles[1])).toHaveAttribute('aria-current', 'true');
  });

  test('다음/이전 버튼으로 배너 사이를 이동할 수 있다', async ({ page }) => {
    await page.goto('/');

    await indicatorFor(page, titles[0]).click();
    await expect(slideFor(page, titles[0])).not.toHaveAttribute('hidden', '');

    await page.locator('#hero-next').click();
    await expect(slideFor(page, titles[1])).not.toHaveAttribute('hidden', '');

    await page.locator('#hero-prev').click();
    await expect(slideFor(page, titles[0])).not.toHaveAttribute('hidden', '');
  });

  test('키보드로 컨트롤에 포커스한 뒤 화살표 키로 배너를 전환할 수 있다', async ({ page }) => {
    await page.goto('/');

    await indicatorFor(page, titles[0]).click();
    await page.locator('#hero-next').focus();
    await page.keyboard.press('ArrowRight');
    await expect(slideFor(page, titles[1])).not.toHaveAttribute('hidden', '');

    await page.keyboard.press('ArrowLeft');
    await expect(slideFor(page, titles[0])).not.toHaveAttribute('hidden', '');
  });

  test('비활성 슬라이드 내부 링크/이미지는 hidden 처리되어 보이지도, 포커스되지도 않는다', async ({ page }) => {
    await page.goto('/');

    await indicatorFor(page, titles[0]).click();
    await expect(slideFor(page, titles[0])).not.toHaveAttribute('hidden', '');
    await expect(slideFor(page, titles[1])).toHaveAttribute('hidden', '');
    await expect(slideFor(page, titles[1]).locator('.hero__link')).toBeHidden();
  });

  test('재생/일시정지 버튼 클릭 시 표시 텍스트와 aria-label이 토글되고, 일시정지 후에는 마우스가 벗어나도 자동전환이 재개되지 않는다', async ({ page }) => {
    await page.goto('/');
    const playPause = page.locator('#hero-play-pause');

    // 이 버튼은 "다음 클릭에 일어날 동작"을 라벨로 보여주는 재생/일시정지 토글이므로 aria-pressed는 쓰지 않는다.
    // 기본 상태는 자동전환 중이므로 "지금 누르면 일시정지된다"는 뜻의 "일시정지"가 보여야 한다.
    await expect(playPause).toHaveText('일시정지');
    await expect(playPause).toHaveAttribute('aria-label', '배너 자동 전환 일시정지');

    await playPause.click();
    // 일시정지 상태가 되면 "지금 누르면 재생된다"는 뜻의 "재생"으로 바뀌어야 한다.
    await expect(playPause).toHaveText('재생');
    await expect(playPause).toHaveAttribute('aria-label', '배너 자동 전환 재생');

    await indicatorFor(page, titles[0]).click();
    await page.mouse.move(0, 0);
    await page.waitForTimeout(1500);
    await expect(slideFor(page, titles[0])).not.toHaveAttribute('hidden', '');
  });

  test('마우스 hover 동안에는 자동전환이 일시정지된다', async ({ page }) => {
    await page.goto('/');

    await indicatorFor(page, titles[0]).click();
    await page.locator('#hero-viewport').hover();
    await page.waitForTimeout(1500);
    await expect(slideFor(page, titles[0])).not.toHaveAttribute('hidden', '');
  });

  test('prefers-reduced-motion: reduce 환경에서는 자동전환이 시작되지 않는다', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.goto('/');

    await indicatorFor(page, titles[0]).click();
    // 초기 상태가 일시정지이므로 "지금 누르면 재생된다"는 뜻의 "재생"이 보여야 한다.
    await expect(page.locator('#hero-play-pause')).toHaveText('재생');
    await expect(page.locator('#hero-play-pause')).toHaveAttribute('aria-label', '배너 자동 전환 재생');
    await page.waitForTimeout(1500);
    await expect(slideFor(page, titles[0])).not.toHaveAttribute('hidden', '');
  });

  test('reduced-motion 환경이어도 재생 버튼을 명시적으로 누르면 자동전환이 허용된다', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.goto('/');
    const playPause = page.locator('#hero-play-pause');

    await expect(playPause).toHaveText('재생');
    await playPause.click();
    await expect(playPause).toHaveText('일시정지');
    await expect(playPause).toHaveAttribute('aria-label', '배너 자동 전환 일시정지');
  });

  test('기본 상태에서 5초 후 다음 배너로 자동 전환된다', async ({ page }) => {
    await page.goto('/');

    await indicatorFor(page, titles[0]).click();
    await expect(slideFor(page, titles[0])).not.toHaveAttribute('hidden', '');

    // 인디케이터 클릭 직후 마우스가 캐러셀 위에 남아 있으면 hover-pause 계약(7번)에 따라
    // 자동전환이 계속 정지된 상태이므로, 순수한 자동전환 동작만 보려면 마우스를 치워야 한다.
    await page.mouse.move(0, 0);
    await page.waitForTimeout(5500);
    await expect(slideFor(page, titles[0])).toHaveAttribute('hidden', '');
  });

  // hero-carousel.js는 자동전환 interval을 시작하는 모든 경로(초기 부트스트랩, prev/next, 인디케이터,
  // 재생/일시정지, hover/focus 진입·이탈)가 하나의 restartTimerIfNeeded() 함수를 거치고, 그 함수는
  // 항상 stopTimer()로 기존 interval을 먼저 지운 뒤에만 새로 만든다 - 즉 "생성 전에 항상 정리"가
  // 구조적으로 보장된다. window.setInterval/clearInterval을 가로채 실제 활성 interval 개수를 세어
  // 이 불변식이 실제 DOM 배선에서도 깨지지 않는지 확인한다.
  test('hover/focus 반복, hover 중 재생-일시정지 클릭, reduced-motion에서 명시적 재생을 거쳐도 활성 interval은 항상 최대 1개다', async ({ page }) => {
    await page.addInitScript(() => {
      window.__activeIntervalCount = 0;
      window.__maxActiveIntervalCount = 0;
      var originalSetInterval = window.setInterval.bind(window);
      var originalClearInterval = window.clearInterval.bind(window);
      window.setInterval = function () {
        window.__activeIntervalCount += 1;
        window.__maxActiveIntervalCount = Math.max(window.__maxActiveIntervalCount, window.__activeIntervalCount);
        return originalSetInterval.apply(null, arguments);
      };
      window.clearInterval = function (id) {
        if (id !== undefined && id !== null) {
          window.__activeIntervalCount = Math.max(0, window.__activeIntervalCount - 1);
        }
        return originalClearInterval(id);
      };
    });

    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.goto('/');
    await indicatorFor(page, titles[0]).click();

    const viewport = page.locator('#hero-viewport');
    const playPause = page.locator('#hero-play-pause');
    const brandLink = page.locator('.site-header__brand');

    // reduced-motion 상태에서 수동 재생 시작
    await expect(playPause).toHaveText('재생');
    await playPause.click();
    await expect(playPause).toHaveText('일시정지');

    // mouseenter -> mouseleave 반복
    for (let i = 0; i < 3; i++) {
      await viewport.hover();
      await page.mouse.move(0, 0);
    }

    // focusin -> focusout 반복 (컨트롤 안 <-> 컨트롤 밖)
    for (let i = 0; i < 3; i++) {
      await page.locator('#hero-next').focus();
      await brandLink.focus();
    }

    // hover 상태에서 재생/일시정지 클릭 (클릭 자체가 마우스를 뷰포트 밖 컨트롤로 옮기므로 mouseleave/mouseenter도 함께 발생한다)
    await viewport.hover();
    await playPause.click();
    await playPause.click();
    await page.mouse.move(0, 0);

    const maxActiveIntervalCount = await page.evaluate(() => window.__maxActiveIntervalCount);
    const finalActiveIntervalCount = await page.evaluate(() => window.__activeIntervalCount);

    expect(maxActiveIntervalCount, '위 상호작용 전체에서 동시에 활성화된 interval이 2개 이상이었던 적이 없어야 한다').toBeLessThanOrEqual(1);
    expect(finalActiveIntervalCount, '마지막에는 재생 중이므로 활성 interval이 정확히 1개여야 한다').toBe(1);
  });

  // 회귀 배경: hidden 상태인 두 번째 이후 슬라이드는 loading="lazy"였을 때 뷰포트 교차 트리거가
  // 전혀 발동하지 않아, 최초 전환 순간까지 이미지 요청 자체가 시작되지 않았다(Docker 8088에서 Playwright
  // 네트워크 이벤트로 직접 확인). 모든 슬라이드를 eager로 바꿔 페이지 로드 시점에 미리 요청되도록 했으므로,
  // 어떤 인디케이터/버튼도 클릭하지 않은 순수 페이지 로드 직후에 이미 요청이 발생했는지를 검증한다.
  test('인디케이터/버튼을 클릭하지 않아도 두 번째·세 번째 Hero 이미지 요청이 페이지 로드 시점에 이미 시작된다', async ({ page }) => {
    const requestedImageUrls = [];
    page.on('request', (req) => {
      if (req.url().includes('/api/files/')) {
        requestedImageUrls.push(req.url());
      }
    });

    await page.goto('/');

    const secondSlideImageSrc = await slideFor(page, titles[1]).locator('img').getAttribute('src');
    const thirdSlideImageSrc = await slideFor(page, titles[2]).locator('img').getAttribute('src');
    expect(secondSlideImageSrc).toBeTruthy();
    expect(thirdSlideImageSrc).toBeTruthy();

    expect(
      requestedImageUrls.some((url) => url.endsWith(secondSlideImageSrc)),
      `두 번째 배너 이미지(${secondSlideImageSrc}) 요청이 페이지 로드만으로 발생해야 한다. 실제 요청 목록: ${requestedImageUrls.join(', ')}`
    ).toBeTruthy();
    expect(
      requestedImageUrls.some((url) => url.endsWith(thirdSlideImageSrc)),
      `세 번째 배너 이미지(${thirdSlideImageSrc}) 요청도 페이지 로드만으로 발생해야 한다. 실제 요청 목록: ${requestedImageUrls.join(', ')}`
    ).toBeTruthy();
  });
});

// 반응형 조사에서 확인된 회귀 #1: /programs, /boards는 순수 Bootstrap list-group이라
// overflow-wrap이 없어서, 공백 없는 긴 제목 하나만으로 페이지 전체가 가로 스크롤됐다.
// (home.css의 #program-list a, #board-list a { overflow-wrap: break-word } 로 수정)
// 로컬 DB에 이미 있는 데이터에 의존하지 않도록 매 테스트마다 API로 데이터를 만들고 끝나면 지운다.
test.describe('긴 제목 오버플로우 회귀 검증', () => {
  test.skip(!ADMIN_LOGIN_ID || !ADMIN_PASSWORD, 'ADMIN_LOGIN_ID/ADMIN_PASSWORD 환경변수가 설정되지 않아 건너뜀');
  test.use({ viewport: { width: 375, height: 812 } });

  // 공백이 전혀 없어 정상적인 단어 경계 줄바꿈으로는 절대 해결되지 않는, overflow-wrap이 실제로
  // 필요한 최악의 케이스를 만든다.
  function longUnbreakableTitle(prefix) {
    return `${prefix}${'A'.repeat(150)}${Date.now()}`;
  }

  let xsrfToken;
  let programId;
  let boardId;

  test.beforeEach(async ({ context, baseURL }) => {
    await loginAsAdmin(context, baseURL);
    xsrfToken = await getXsrfToken(context);

    const programResponse = await context.request.post(`${baseURL}/api/admin/programs`, {
      headers: { 'X-XSRF-TOKEN': xsrfToken },
      data: {
        programType: 'COURSE',
        title: longUnbreakableTitle('LongProgramTitle'),
        content: 'overflow regression',
        isPublic: true,
      },
    });
    expect(programResponse.ok()).toBeTruthy();
    programId = (await programResponse.json()).data.id;

    const boardResponse = await context.request.post(`${baseURL}/api/admin/boards`, {
      headers: { 'X-XSRF-TOKEN': xsrfToken },
      data: { boardType: 'NOTICE', title: longUnbreakableTitle('LongBoardTitle'), isPublic: true },
    });
    expect(boardResponse.ok()).toBeTruthy();
    boardId = (await boardResponse.json()).data.id;
  });

  test.afterEach(async ({ context, baseURL }) => {
    await context.request.delete(`${baseURL}/api/admin/programs/${programId}`, {
      headers: { 'X-XSRF-TOKEN': xsrfToken },
    });
    await context.request.delete(`${baseURL}/api/admin/boards/${boardId}`, {
      headers: { 'X-XSRF-TOKEN': xsrfToken },
    });
  });

  async function overflowX(page) {
    return page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  }

  test('/programs: 공백 없는 긴 제목이 있어도 가로 스크롤이 생기지 않는다', async ({ page }) => {
    await page.goto('/programs');
    expect(await overflowX(page)).toBeLessThanOrEqual(0);
  });

  test('/boards: 공백 없는 긴 제목이 있어도 가로 스크롤이 생기지 않는다', async ({ page }) => {
    await page.goto('/boards');
    expect(await overflowX(page)).toBeLessThanOrEqual(0);
  });
});

// 반응형 조사에서 확인된 회귀 #2: 메인 Program/Gallery 카드 grid가 auto-fit이라, 실제 아이템 수보다
// 들어갈 수 있는 컬럼 수가 많은 상태(메인은 각각 최신 3건/5건만 노출하는데 1440px에서는 5/7컬럼까지
// 들어갈 폭)에서 남는 1fr 공간을 카드가 그대로 나눠 가져 비정상적으로 커졌다(실측 최대 636px).
// (home.css에서 auto-fit -> auto-fill로 수정)
// 이 상태는 메인이 항상 개수를 상한으로 자르기 때문에 현재 DB에 데이터가 몇 건이든 상시 재현되므로,
// 전체 개수를 통제할 필요 없이 "고유 제목의 아이템 1건이 최소 존재"하도록만 만들면 충분하다.
test.describe('메인 카드 폭 회귀 검증', () => {
  test.skip(!ADMIN_LOGIN_ID || !ADMIN_PASSWORD, 'ADMIN_LOGIN_ID/ADMIN_PASSWORD 환경변수가 설정되지 않아 건너뜀');
  test.use({ viewport: { width: 1440, height: 900 } });

  // CSS가 선언한 minmax() 하한(Program 220px, Gallery 160px) 대비 여유를 둔 상한.
  // auto-fill 적용 후 1440px 실측 예상치(Program ≈245px, Gallery ≈188px)보다는 넉넉하고,
  // auto-fit이었을 때의 회귀치(Program 636px)보다는 훨씬 작아 회귀를 확실히 잡아낸다.
  const PROGRAM_CARD_MAX_WIDTH = 320;
  const GALLERY_CARD_MAX_WIDTH = 240;

  let xsrfToken;
  let programId;
  let boardId;
  let programTitle;
  let boardTitle;

  test.beforeEach(async ({ context, baseURL }) => {
    await loginAsAdmin(context, baseURL);
    xsrfToken = await getXsrfToken(context);

    const runId = Date.now();
    programTitle = `카드 폭 회귀 확인용 프로그램 ${runId}`;
    boardTitle = `카드 폭 회귀 확인용 갤러리 ${runId}`;

    const programResponse = await context.request.post(`${baseURL}/api/admin/programs`, {
      headers: { 'X-XSRF-TOKEN': xsrfToken },
      data: { programType: 'COURSE', title: programTitle, content: '카드 폭 확인', isPublic: true },
    });
    expect(programResponse.ok()).toBeTruthy();
    programId = (await programResponse.json()).data.id;

    const boardResponse = await context.request.post(`${baseURL}/api/admin/boards`, {
      headers: { 'X-XSRF-TOKEN': xsrfToken },
      data: { boardType: 'GALLERY', title: boardTitle, isPublic: true },
    });
    expect(boardResponse.ok()).toBeTruthy();
    boardId = (await boardResponse.json()).data.id;
  });

  test.afterEach(async ({ context, baseURL }) => {
    await context.request.delete(`${baseURL}/api/admin/programs/${programId}`, {
      headers: { 'X-XSRF-TOKEN': xsrfToken },
    });
    await context.request.delete(`${baseURL}/api/admin/boards/${boardId}`, {
      headers: { 'X-XSRF-TOKEN': xsrfToken },
    });
  });

  test('1440px에서 Program 카드가 minmax 하한 대비 과도하게 커지지 않는다', async ({ page }) => {
    await page.goto('/');
    const card = page.locator('.program-card').filter({ has: page.locator(`text=${programTitle}`) });
    await expect(card).toBeVisible();
    const box = await card.boundingBox();
    expect(box.width).toBeLessThanOrEqual(PROGRAM_CARD_MAX_WIDTH);
  });

  test('1440px에서 Gallery 카드가 minmax 하한 대비 과도하게 커지지 않는다', async ({ page }) => {
    await page.goto('/');
    const card = page.locator('.gallery-card__link').filter({ has: page.locator(`text=${boardTitle}`) });
    await expect(card).toBeVisible();
    const thumb = card.locator('.gallery-card__thumb');
    const box = await thumb.boundingBox();
    expect(box.width).toBeLessThanOrEqual(GALLERY_CARD_MAX_WIDTH);
  });
});
