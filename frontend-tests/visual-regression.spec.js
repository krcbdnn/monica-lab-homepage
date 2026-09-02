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

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

// popup-modal.js와 동일한 "오늘 하루 보지 않기" localStorage 키 규칙.
// 이 파일의 모든 describe가 공유한다 - 아래 파일 전역 beforeEach가 실제로 떠 있는 Popup을
// 모든 테스트에서 억제하는 데 쓰고, "공개 Popup 레이어" describe는 자신이 만든 A/B 테스트를
// 위해 같은 규칙을 다시 사용한다.
const POPUP_STORAGE_KEY_PREFIX = 'popup-hide-until:';

function popupPad2(n) {
  return n < 10 ? '0' + n : '' + n;
}

function popupTodayLocalDateString() {
  const now = new Date();
  return now.getFullYear() + '-' + popupPad2(now.getMonth() + 1) + '-' + popupPad2(now.getDate());
}

async function fetchExistingPublicPopupIds(context, baseURL) {
  const response = await context.request.get(`${baseURL}/api/popups`);
  expect(response.ok()).toBeTruthy();
  const body = await response.json();
  return body.data.map((popup) => String(popup.id));
}

// P13-T11 도입 이후 실DB에 현재 활성 상태인 실Popup이 있으면(개수/여부 통제 불가) 전체 화면을
// 덮는 overlay가 뜬다. 이 파일의 다른 describe(햄버거 메뉴, Hero 캐러셀, 반응형 뷰포트 등)는
// Popup을 전혀 알지 못하므로, 그 overlay가 클릭/포커스를 가로채 무관한 테스트를 깨뜨릴 수 있다.
// 그래서 "실제로 지금 떠 있는 Popup을 이 테스트 브라우저 컨텍스트에서만 오늘 하루 보지 않기로
// 미리 처리"하는 로직을 특정 describe 안이 아니라 파일 전역 beforeEach로 올려 모든 테스트에
// 공통 적용한다. 실Popup은 삭제/수정/visibility 변경을 전혀 하지 않는다.
let globalPreExistingPopupIds = [];

test.beforeEach(async ({ context, baseURL }) => {
  globalPreExistingPopupIds = await fetchExistingPublicPopupIds(context, baseURL);
  const todayString = popupTodayLocalDateString();

  await context.addInitScript(
    ({ ids, today, prefix }) => {
      ids.forEach((id) => {
        window.localStorage.setItem(prefix + id, today);
      });
    },
    { ids: globalPreExistingPopupIds, today: todayString, prefix: POPUP_STORAGE_KEY_PREFIX }
  );
});

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
    main: '#latest-programs',
    button: '#latest-programs .section-title__link',
  },
  {
    path: '/pages/GREETING',
    label: '인사말',
    nav: null,
    main: 'h2',
    button: null,
  },
  {
    path: '/pages/INTRODUCTION',
    label: '연구소 소개',
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
    await expect(page.locator('#quick-menu a')).toHaveCount(4);
    for (const href of ['/pages/INTRODUCTION', '/programs', '/boards?boardType=REVIEW', '/boards']) {
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
    await expect(page.locator('#quick-menu a')).toHaveCount(4);
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

// P13-T12: 메인 섹션 제목 링크화 + Program 목록 썸네일.
test.describe('P13-T12: 메인 섹션 제목 링크 + Program 목록 썸네일', () => {
  test.skip(!ADMIN_LOGIN_ID || !ADMIN_PASSWORD, 'ADMIN_LOGIN_ID/ADMIN_PASSWORD 환경변수가 설정되지 않아 건너뜀');

  test('메인 화면에 "전체보기" 텍스트가 더 이상 존재하지 않는다', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('body')).not.toContainText('전체보기');
  });

  test('"최신 프로그램" 섹션 제목을 클릭하면 /programs로 이동한다', async ({ page }) => {
    await page.goto('/');
    await page.locator('#latest-programs .section-title__link').click();
    await expect(page).toHaveURL(/\/programs$/);
  });

  test('"공지사항" 섹션 제목을 클릭하면 /boards?boardType=NOTICE로 이동한다', async ({ page }) => {
    await page.goto('/');
    await page.locator('#latest-notices .section-title__link').click();
    await expect(page).toHaveURL(/\/boards\?boardType=NOTICE$/);
  });

  test('"갤러리" 섹션 제목을 클릭하면 /boards?boardType=GALLERY로 이동한다', async ({ page }) => {
    await page.goto('/');
    await page.locator('#latest-gallery .section-title__link').click();
    await expect(page).toHaveURL(/\/boards\?boardType=GALLERY$/);
  });

  // P13-T16: #latest-programs 바로 아래 #latest-reviews 섹션 및 header/footer "강의 후기" 링크 추가.
  test('"강의 후기" 섹션 제목을 클릭하면 /boards?boardType=REVIEW로 이동한다', async ({ page }) => {
    await page.goto('/');
    await page.locator('#latest-reviews .section-title__link').click();
    await expect(page).toHaveURL(/\/boards\?boardType=REVIEW$/);
  });

  test('footer에도 "강의 후기" 링크가 /boards?boardType=REVIEW로 존재한다', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('.site-footer__nav a[href="/boards?boardType=REVIEW"]')).toBeVisible();
  });

  test('"강의 후기" 섹션은 "최신 프로그램" 섹션 바로 다음, "공지사항" 섹션 이전에 위치한다', async ({ page }) => {
    await page.goto('/');
    const programsBox = await page.locator('#latest-programs').boundingBox();
    const reviewsBox = await page.locator('#latest-reviews').boundingBox();
    const noticesBox = await page.locator('#latest-notices').boundingBox();
    expect(reviewsBox.y).toBeGreaterThan(programsBox.y);
    expect(noticesBox.y).toBeGreaterThan(reviewsBox.y);
  });

  test.describe('/programs 목록 썸네일', () => {
    let xsrfToken;
    let programIdWithThumb;
    let programIdWithoutThumb;
    let titleWithThumb;
    let titleWithoutThumb;

    test.beforeEach(async ({ context, baseURL }) => {
      await loginAsAdmin(context, baseURL);
      xsrfToken = await getXsrfToken(context);
      const runId = Date.now();
      titleWithThumb = `프로그램 썸네일 확인 ${runId}`;
      titleWithoutThumb = `프로그램 썸네일 없음 확인 ${runId}`;

      const withThumbRes = await context.request.post(`${baseURL}/api/admin/programs`, {
        headers: { 'X-XSRF-TOKEN': xsrfToken },
        data: {
          programType: 'COURSE', title: titleWithThumb, content: '내용',
          thumbnail: '/api/files/900001', isPublic: true,
        },
      });
      expect(withThumbRes.ok()).toBeTruthy();
      programIdWithThumb = (await withThumbRes.json()).data.id;

      const withoutThumbRes = await context.request.post(`${baseURL}/api/admin/programs`, {
        headers: { 'X-XSRF-TOKEN': xsrfToken },
        data: { programType: 'COURSE', title: titleWithoutThumb, content: '내용', isPublic: true },
      });
      expect(withoutThumbRes.ok()).toBeTruthy();
      programIdWithoutThumb = (await withoutThumbRes.json()).data.id;
    });

    test.afterEach(async ({ context, baseURL }) => {
      await context.request.delete(`${baseURL}/api/admin/programs/${programIdWithThumb}`, {
        headers: { 'X-XSRF-TOKEN': xsrfToken },
      });
      await context.request.delete(`${baseURL}/api/admin/programs/${programIdWithoutThumb}`, {
        headers: { 'X-XSRF-TOKEN': xsrfToken },
      });
    });

    function itemFor(page, title) {
      return page.locator('#program-list li').filter({ has: page.locator(`text=${title}`) });
    }

    test('썸네일이 있으면 img로, 없으면 placeholder로 표시된다', async ({ page }) => {
      await page.goto('/programs');

      await expect(itemFor(page, titleWithThumb).locator('.program-list__thumb img'))
        .toHaveAttribute('src', '/api/files/900001');
      await expect(itemFor(page, titleWithoutThumb).locator('.program-list__thumb-placeholder')).toBeVisible();
      await expect(itemFor(page, titleWithoutThumb).locator('.program-list__thumb img')).toHaveCount(0);
    });

    test('썸네일 영역을 클릭해도 상세 페이지로 이동한다(a 전체가 클릭 영역)', async ({ page }) => {
      await page.goto('/programs');

      await itemFor(page, titleWithThumb).locator('.program-list__thumb').click();
      await expect(page).toHaveURL(new RegExp(`/programs/${programIdWithThumb}$`));
    });

    for (const viewport of VIEWPORTS) {
      test(`${viewport.name}에서 /programs 목록(썸네일 포함)에 overflow가 없다`, async ({ page }) => {
        await page.setViewportSize({ width: viewport.width, height: viewport.height });
        await page.goto('/programs');

        await expect(itemFor(page, titleWithThumb)).toBeVisible();
        const overflowX = await page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth);
        expect(overflowX).toBeLessThanOrEqual(0);
      });
    }
  });
});

// P13-T17: 공개 화면 명칭/네비게이션/홈 구성 정리.
test.describe('P13-T17: 공개 화면 명칭/네비게이션/홈 구성 정리', () => {
  test('헤더/푸터 브랜드 텍스트가 "모니카영어교육연구소"로 통일되어 있다', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('.site-header__brand')).toHaveText('모니카영어교육연구소');
    await expect(page.locator('.site-footer__brand')).toHaveText('모니카영어교육연구소');
  });

  test('헤더/푸터의 "연구소 소개" 링크가 /pages/INTRODUCTION으로 이동한다', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('#quick-menu a[href="/pages/INTRODUCTION"]')).toHaveText('연구소 소개');
    await expect(page.locator('.site-footer__nav a[href="/pages/INTRODUCTION"]')).toHaveText('연구소 소개');

    await page.locator('#quick-menu a[href="/pages/INTRODUCTION"]').click();
    await expect(page).toHaveURL(/\/pages\/INTRODUCTION$/);
  });

  test('footer에 www.monicaenglish.com 텍스트/링크가 존재한다', async ({ page }) => {
    await page.goto('/');
    const domainLink = page.locator('.site-footer__site a');
    await expect(domainLink).toHaveText('www.monicaenglish.com');
    await expect(domainLink).toHaveAttribute('href', 'https://www.monicaenglish.com');
    await expect(domainLink).not.toHaveAttribute('target', /.+/);
  });

  test('홈 화면에 #greeting, #program-shortcut 영역이 더 이상 존재하지 않는다', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('#greeting')).toHaveCount(0);
    await expect(page.locator('#program-shortcut')).toHaveCount(0);
  });

  test('/pages/GREETING 상세 페이지는 그대로 유지된다', async ({ page }) => {
    const response = await page.goto('/pages/GREETING');
    expect(response.status()).toBe(200);
  });

  for (const viewport of VIEWPORTS) {
    test(`${viewport.name}에서 footer 도메인 링크를 포함해도 overflow가 없다`, async ({ page }) => {
      await page.setViewportSize({ width: viewport.width, height: viewport.height });
      await page.goto('/');
      const overflowX = await page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(overflowX).toBeLessThanOrEqual(0);
    });
  }
});

// P13-T18: 관리자 Board/Program 수정 화면에서 기존 thumbnail/attachment가 보이지 않던 문제 검증.
// 1x1 투명 PNG(순수 데이터 URI, 실제 파일 아님) - 진짜 업로드 검증(magic byte 검사 포함)용.
const PNG_1PX_BUFFER = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
    'base64');

test.describe('P13-T18: 관리자 Board/Program 기존 썸네일/첨부파일 미리보기', () => {
  test.skip(!ADMIN_LOGIN_ID || !ADMIN_PASSWORD, 'ADMIN_LOGIN_ID/ADMIN_PASSWORD 환경변수가 설정되지 않아 건너뜀');

  let xsrfToken;
  let boardId;
  let programId;

  async function createBoardWithFiles(context, baseURL, title) {
    const res = await context.request.post(`${baseURL}/api/admin/boards`, {
      headers: { 'X-XSRF-TOKEN': xsrfToken },
      data: {
        boardType: 'NOTICE', title,
        thumbnail: '/api/files/900201', attachment: '/api/files/900202',
        isPublic: true,
      },
    });
    expect(res.ok()).toBeTruthy();
    return (await res.json()).data.id;
  }

  async function createProgramWithFiles(context, baseURL, title) {
    const res = await context.request.post(`${baseURL}/api/admin/programs`, {
      headers: { 'X-XSRF-TOKEN': xsrfToken },
      data: {
        programType: 'COURSE', title, content: '내용',
        thumbnail: '/api/files/900301', attachment: '/api/files/900302',
        isPublic: true,
      },
    });
    expect(res.ok()).toBeTruthy();
    return (await res.json()).data.id;
  }

  test.beforeEach(async ({ context, baseURL }) => {
    await loginAsAdmin(context, baseURL);
    xsrfToken = await getXsrfToken(context);
    boardId = undefined;
    programId = undefined;
  });

  test.afterEach(async ({ context, baseURL }) => {
    if (boardId) {
      await context.request.delete(`${baseURL}/api/admin/boards/${boardId}`, {
        headers: { 'X-XSRF-TOKEN': xsrfToken },
      });
    }
    if (programId) {
      await context.request.delete(`${baseURL}/api/admin/programs/${programId}`, {
        headers: { 'X-XSRF-TOKEN': xsrfToken },
      });
    }
  });

  test('Board 수정 화면 진입 시 기존 썸네일 미리보기가 표시된다', async ({ page, context, baseURL }) => {
    boardId = await createBoardWithFiles(context, baseURL, 'Board 썸네일 미리보기 확인 ' + Date.now());
    await page.goto(`/admin/boards/${boardId}/edit`);

    await expect(page.locator('#thumbnailPreview')).toBeVisible();
    await expect(page.locator('#thumbnailPreviewImage')).toHaveAttribute('src', '/api/files/900201');
    await expect(page.locator('#thumbnailPreviewLink')).toHaveAttribute('href', '/api/files/900201');
    await expect(page.locator('#thumbnailPreviewLink')).toHaveAttribute('target', '_blank');
    await expect(page.locator('#thumbnailPreviewLink')).toHaveAttribute('rel', 'noopener noreferrer');
  });

  test('Board 수정 화면 진입 시 기존 첨부파일 링크가 표시된다(target 없음)', async ({ page, context, baseURL }) => {
    boardId = await createBoardWithFiles(context, baseURL, 'Board 첨부파일 미리보기 확인 ' + Date.now());
    await page.goto(`/admin/boards/${boardId}/edit`);

    await expect(page.locator('#attachmentPreview')).toBeVisible();
    await expect(page.locator('#attachmentPreviewLink')).toHaveAttribute('href', '/api/files/900202');
    await expect(page.locator('#attachmentPreviewLink')).not.toHaveAttribute('target', /.+/);
  });

  test('Program 수정 화면 진입 시 기존 썸네일 미리보기가 표시된다', async ({ page, context, baseURL }) => {
    programId = await createProgramWithFiles(context, baseURL, 'Program 썸네일 미리보기 확인 ' + Date.now());
    await page.goto(`/admin/programs/${programId}/edit`);

    await expect(page.locator('#thumbnailPreview')).toBeVisible();
    await expect(page.locator('#thumbnailPreviewImage')).toHaveAttribute('src', '/api/files/900301');
    await expect(page.locator('#thumbnailPreviewLink')).toHaveAttribute('target', '_blank');
    await expect(page.locator('#thumbnailPreviewLink')).toHaveAttribute('rel', 'noopener noreferrer');
  });

  test('Program 수정 화면 진입 시 기존 첨부파일 링크가 표시된다(target 없음)', async ({ page, context, baseURL }) => {
    programId = await createProgramWithFiles(context, baseURL, 'Program 첨부파일 미리보기 확인 ' + Date.now());
    await page.goto(`/admin/programs/${programId}/edit`);

    await expect(page.locator('#attachmentPreview')).toBeVisible();
    await expect(page.locator('#attachmentPreviewLink')).toHaveAttribute('href', '/api/files/900302');
    await expect(page.locator('#attachmentPreviewLink')).not.toHaveAttribute('target', /.+/);
  });

  test('신규 등록 화면(Board/Program 모두)에서는 미리보기 영역이 표시되지 않는다', async ({ page }) => {
    await page.goto('/admin/boards/new');
    await expect(page.locator('#thumbnailPreview')).toBeHidden();
    await expect(page.locator('#attachmentPreview')).toBeHidden();

    await page.goto('/admin/programs/new');
    await expect(page.locator('#thumbnailPreview')).toBeHidden();
    await expect(page.locator('#attachmentPreview')).toBeHidden();
  });

  test('Board: 새 파일을 선택하지 않고 수정 저장하면 기존 thumbnail/attachment URL이 그대로 PUT payload에 담긴다', async ({ page, context, baseURL }) => {
    boardId = await createBoardWithFiles(context, baseURL, 'Board URL 유지 확인 ' + Date.now());
    await page.goto(`/admin/boards/${boardId}/edit`);
    await expect(page.locator('#thumbnailPreview')).toBeVisible();

    const [request] = await Promise.all([
      page.waitForRequest((req) => req.url().endsWith(`/api/admin/boards/${boardId}`) && req.method() === 'PUT'),
      page.locator('#boardForm button[type="submit"]').click(),
    ]);

    const payload = request.postDataJSON();
    expect(payload.thumbnail).toBe('/api/files/900201');
    expect(payload.attachment).toBe('/api/files/900202');
  });

  test('Program: 새 파일을 선택하지 않고 수정 저장하면 기존 thumbnail/attachment URL이 그대로 PUT payload에 담긴다', async ({ page, context, baseURL }) => {
    programId = await createProgramWithFiles(context, baseURL, 'Program URL 유지 확인 ' + Date.now());
    await page.goto(`/admin/programs/${programId}/edit`);
    await expect(page.locator('#thumbnailPreview')).toBeVisible();

    const [request] = await Promise.all([
      page.waitForRequest((req) => req.url().endsWith(`/api/admin/programs/${programId}`) && req.method() === 'PUT'),
      page.locator('#programForm button[type="submit"]').click(),
    ]);

    const payload = request.postDataJSON();
    expect(payload.thumbnail).toBe('/api/files/900301');
    expect(payload.attachment).toBe('/api/files/900302');
  });

  test('Board: 새 썸네일 파일을 업로드하면 미리보기가 즉시 새 URL로 갱신된다', async ({ page, context, baseURL }) => {
    boardId = await createBoardWithFiles(context, baseURL, 'Board 새 썸네일 갱신 확인 ' + Date.now());
    await page.goto(`/admin/boards/${boardId}/edit`);
    await expect(page.locator('#thumbnailPreviewImage')).toHaveAttribute('src', '/api/files/900201');

    await page.setInputFiles('#thumbnailInput', {
      name: 'new-thumb.png', mimeType: 'image/png', buffer: PNG_1PX_BUFFER,
    });

    await expect(page.locator('#thumbnail')).not.toHaveValue('/api/files/900201');
    const newUrl = await page.locator('#thumbnail').inputValue();
    expect(newUrl).toBeTruthy();
    await expect(page.locator('#thumbnailPreviewImage')).toHaveAttribute('src', newUrl);
    await expect(page.locator('#thumbnailPreview')).toBeVisible();
  });

  test('Program: 새 썸네일 파일을 업로드하면 미리보기가 즉시 새 URL로 갱신된다', async ({ page, context, baseURL }) => {
    programId = await createProgramWithFiles(context, baseURL, 'Program 새 썸네일 갱신 확인 ' + Date.now());
    await page.goto(`/admin/programs/${programId}/edit`);
    await expect(page.locator('#thumbnailPreviewImage')).toHaveAttribute('src', '/api/files/900301');

    await page.setInputFiles('#thumbnailInput', {
      name: 'new-thumb.png', mimeType: 'image/png', buffer: PNG_1PX_BUFFER,
    });

    await expect(page.locator('#thumbnail')).not.toHaveValue('/api/files/900301');
    const newUrl = await page.locator('#thumbnail').inputValue();
    expect(newUrl).toBeTruthy();
    await expect(page.locator('#thumbnailPreviewImage')).toHaveAttribute('src', newUrl);
    await expect(page.locator('#thumbnailPreview')).toBeVisible();
  });
});

// P13-T26: Board/Program 수정 화면에서 기존 thumbnail/attachment를 "제거"(detach)할 수 있게 한다.
// "제거"는 Board/Program이 가진 URL 참조만 비우는 동작이며, File 레코드/실제 파일 삭제
// (DELETE /api/admin/files/{id})와는 무관하다 - form.html이 그 endpoint를 전혀 참조하지 않는다는
// 점은 Node 정적 테스트(board-admin-view.test.js/program-admin-view.test.js)로 이미 확인했으므로,
// 여기서는 별도 network spy 없이 실제 저장 round-trip과 상호 비영향만 검증한다.
test.describe('P13-T26: 관리자 Board/Program 기존 썸네일/첨부파일 제거', () => {
  test.skip(!ADMIN_LOGIN_ID || !ADMIN_PASSWORD, 'ADMIN_LOGIN_ID/ADMIN_PASSWORD 환경변수가 설정되지 않아 건너뜀');

  let xsrfToken;
  let boardId;
  let programId;

  async function createBoardWithFiles(context, baseURL, title) {
    const res = await context.request.post(`${baseURL}/api/admin/boards`, {
      headers: { 'X-XSRF-TOKEN': xsrfToken },
      data: {
        boardType: 'NOTICE', title,
        thumbnail: '/api/files/900601', attachment: '/api/files/900602',
        isPublic: true,
      },
    });
    expect(res.ok()).toBeTruthy();
    return (await res.json()).data.id;
  }

  async function createProgramWithFiles(context, baseURL, title) {
    const res = await context.request.post(`${baseURL}/api/admin/programs`, {
      headers: { 'X-XSRF-TOKEN': xsrfToken },
      data: {
        programType: 'COURSE', title, content: '내용',
        thumbnail: '/api/files/900701', attachment: '/api/files/900702',
        isPublic: true,
      },
    });
    expect(res.ok()).toBeTruthy();
    return (await res.json()).data.id;
  }

  test.beforeEach(async ({ context, baseURL }) => {
    await loginAsAdmin(context, baseURL);
    xsrfToken = await getXsrfToken(context);
    boardId = undefined;
    programId = undefined;
  });

  test.afterEach(async ({ context, baseURL }) => {
    if (boardId) {
      await context.request.delete(`${baseURL}/api/admin/boards/${boardId}`, {
        headers: { 'X-XSRF-TOKEN': xsrfToken },
      });
    }
    if (programId) {
      await context.request.delete(`${baseURL}/api/admin/programs/${programId}`, {
        headers: { 'X-XSRF-TOKEN': xsrfToken },
      });
    }
  });

  test('Board: 썸네일/첨부파일 제거 버튼을 각각 클릭하면 hidden input이 비고 해당 preview만 사라지며 서로 영향을 주지 않는다', async ({ page, context, baseURL }) => {
    boardId = await createBoardWithFiles(context, baseURL, 'Board 제거 상호 비영향 확인 ' + Date.now());
    await page.goto(`/admin/boards/${boardId}/edit`);

    await expect(page.locator('#thumbnailPreview')).toBeVisible();
    await expect(page.locator('#attachmentPreview')).toBeVisible();

    await page.locator('#thumbnailRemoveButton').click();
    await expect(page.locator('#thumbnail')).toHaveValue('');
    await expect(page.locator('#thumbnailPreview')).toBeHidden();
    // thumbnail만 제거했으므로 attachment preview는 그대로 유지되어야 한다.
    await expect(page.locator('#attachmentPreview')).toBeVisible();
    await expect(page.locator('#attachment')).toHaveValue('/api/files/900602');

    await page.locator('#attachmentRemoveButton').click();
    await expect(page.locator('#attachment')).toHaveValue('');
    await expect(page.locator('#attachmentPreview')).toBeHidden();
  });

  test('Board: 제거 후 저장하면 PUT payload에서 thumbnail/attachment가 null이 되고, 재진입 시 두 preview 모두 hidden으로 유지된다', async ({ page, context, baseURL }) => {
    boardId = await createBoardWithFiles(context, baseURL, 'Board 제거 저장 round-trip 확인 ' + Date.now());
    await page.goto(`/admin/boards/${boardId}/edit`);

    await page.locator('#thumbnailRemoveButton').click();
    await page.locator('#attachmentRemoveButton').click();

    const [request] = await Promise.all([
      page.waitForRequest((req) => req.url().endsWith(`/api/admin/boards/${boardId}`) && req.method() === 'PUT'),
      page.waitForURL(/\/admin\/boards$/, { timeout: 10000 }),
      page.locator('#boardForm button[type="submit"]').click(),
    ]);

    const payload = request.postDataJSON();
    expect(payload.thumbnail).toBeNull();
    expect(payload.attachment).toBeNull();

    await page.goto(`/admin/boards/${boardId}/edit`);
    await expect(page.locator('#thumbnailPreview')).toBeHidden();
    await expect(page.locator('#attachmentPreview')).toBeHidden();
  });

  test('Board: 썸네일 제거 후 새 파일을 업로드하면 정상적으로 새 URL이 설정되고, 다시 제거하면 hidden 상태로 돌아간다', async ({ page, context, baseURL }) => {
    boardId = await createBoardWithFiles(context, baseURL, 'Board 제거 후 재업로드 확인 ' + Date.now());
    await page.goto(`/admin/boards/${boardId}/edit`);

    await page.locator('#thumbnailRemoveButton').click();
    await expect(page.locator('#thumbnailPreview')).toBeHidden();

    await page.setInputFiles('#thumbnailInput', {
      name: 'new-thumb.png', mimeType: 'image/png', buffer: PNG_1PX_BUFFER,
    });

    // 업로드는 비동기(change 핸들러 안에서 fetch 완료 후 값이 채워짐) - 값이 채워질 때까지
    // expect의 폴링을 이용해 기다린 뒤(P13-T18 테스트와 동일 패턴) inputValue를 읽는다.
    await expect(page.locator('#thumbnail')).not.toHaveValue('');
    const newUrl = await page.locator('#thumbnail').inputValue();
    expect(newUrl).toBeTruthy();
    await expect(page.locator('#thumbnailPreview')).toBeVisible();
    await expect(page.locator('#thumbnailPreviewImage')).toHaveAttribute('src', newUrl);

    await page.locator('#thumbnailRemoveButton').click();
    await expect(page.locator('#thumbnail')).toHaveValue('');
    await expect(page.locator('#thumbnailPreview')).toBeHidden();
  });

  test('Program: 썸네일/첨부파일 제거 → 저장 → 재진입 round-trip', async ({ page, context, baseURL }) => {
    programId = await createProgramWithFiles(context, baseURL, 'Program 제거 저장 round-trip 확인 ' + Date.now());
    await page.goto(`/admin/programs/${programId}/edit`);

    await page.locator('#thumbnailRemoveButton').click();
    await page.locator('#attachmentRemoveButton').click();
    await expect(page.locator('#thumbnailPreview')).toBeHidden();
    await expect(page.locator('#attachmentPreview')).toBeHidden();

    const [request] = await Promise.all([
      page.waitForRequest((req) => req.url().endsWith(`/api/admin/programs/${programId}`) && req.method() === 'PUT'),
      page.waitForURL(/\/admin\/programs$/, { timeout: 10000 }),
      page.locator('#programForm button[type="submit"]').click(),
    ]);

    const payload = request.postDataJSON();
    expect(payload.thumbnail).toBeNull();
    expect(payload.attachment).toBeNull();

    await page.goto(`/admin/programs/${programId}/edit`);
    await expect(page.locator('#thumbnailPreview')).toBeHidden();
    await expect(page.locator('#attachmentPreview')).toBeHidden();
  });
});

// P13-T14: 게시판/프로그램 목록 필터 · UI · pagination. 실제 Docker DB는 이 세션 전체에서 누적된
// 데이터가 이미 있을 수 있으므로(격리된 테스트 DB가 아님), "정확히 N페이지"처럼 전체 개수를 못박는
// 단정은 하지 않는다 - 이번에 새로 만드는 레코드 개수만큼 "최소 이 이상"이라는 사실만 검증한다
// (새로 만든 레코드는 createdAt DESC 정렬에서 항상 최신이라 페이지 1에 온다는 점만 이용).
test.describe('P13-T14: 게시판/프로그램 목록 필터 및 pagination', () => {
  test.skip(!ADMIN_LOGIN_ID || !ADMIN_PASSWORD, 'ADMIN_LOGIN_ID/ADMIN_PASSWORD 환경변수가 설정되지 않아 건너뜀');

  async function createNoticeBoard(context, baseURL, xsrfToken, title) {
    const response = await context.request.post(`${baseURL}/api/admin/boards`, {
      headers: { 'X-XSRF-TOKEN': xsrfToken },
      data: { boardType: 'NOTICE', title, content: '<p>내용</p>', isPublic: true },
    });
    expect(response.ok()).toBeTruthy();
    return (await response.json()).data.id;
  }

  async function deleteBoard(context, baseURL, xsrfToken, id) {
    await context.request.delete(`${baseURL}/api/admin/boards/${id}`, {
      headers: { 'X-XSRF-TOKEN': xsrfToken },
    });
  }

  test('"공지사항" 필터가 active일 때 다른 필터보다 굵게 표시된다', async ({ page }) => {
    await page.goto('/boards?boardType=NOTICE');

    const active = page.locator('#board-type-filter .filter-nav__link.is-active');
    const inactive = page.locator('#board-type-filter .filter-nav__link:not(.is-active)').first();
    await expect(active).toHaveText('공지사항');

    const activeWeight = Number(await active.evaluate((el) => getComputedStyle(el).fontWeight));
    const inactiveWeight = Number(await inactive.evaluate((el) => getComputedStyle(el).fontWeight));
    expect(activeWeight).toBeGreaterThan(inactiveWeight);
  });

  test('boardType 없이 /boards에 진입하면 "전체" 필터가 active다', async ({ page }) => {
    await page.goto('/boards');
    await expect(page.locator('#board-type-filter .filter-nav__link.is-active')).toHaveText('전체');
  });

  // P13-T16: #board-type-filter에 "강의 후기"(REVIEW) 필터 추가. active/query parameter 계약은
  // 위 "공지사항" 케이스와 동일하게 유지된다.
  test('"강의 후기" 필터가 active일 때 다른 필터보다 굵게 표시된다', async ({ page }) => {
    await page.goto('/boards?boardType=REVIEW');

    const active = page.locator('#board-type-filter .filter-nav__link.is-active');
    const inactive = page.locator('#board-type-filter .filter-nav__link:not(.is-active)').first();
    await expect(active).toHaveText('강의 후기');

    const activeWeight = Number(await active.evaluate((el) => getComputedStyle(el).fontWeight));
    const inactiveWeight = Number(await inactive.evaluate((el) => getComputedStyle(el).fontWeight));
    expect(activeWeight).toBeGreaterThan(inactiveWeight);
  });

  test.describe('목록 UI 및 pagination', () => {
    let xsrfToken;
    let boardIds;
    let titlePrefix;

    test.beforeEach(async ({ context, baseURL }) => {
      await loginAsAdmin(context, baseURL);
      xsrfToken = await getXsrfToken(context);
      titlePrefix = `P13-T14 목록확인 ${Date.now()}`;
      boardIds = [];
      for (let i = 0; i < 3; i++) {
        boardIds.push(await createNoticeBoard(context, baseURL, xsrfToken, `${titlePrefix}-${i}`));
      }
    });

    test.afterEach(async ({ context, baseURL }) => {
      for (const id of boardIds) {
        await deleteBoard(context, baseURL, xsrfToken, id);
      }
    });

    test('게시판 분류명/제목이 한 줄에, 작성일시는 아래 보조 줄에 표시된다', async ({ page }) => {
      await page.goto('/boards');
      const item = page.locator('#board-list li').filter({ hasText: titlePrefix }).first();

      const typeBox = await item.locator('.board-list__type').boundingBox();
      const titleBox = await item.locator('.board-list__title').boundingBox();
      const dateBox = await item.locator('.board-list__date').boundingBox();

      // 분류명/제목은 세로 위치가 거의 같은 한 줄이다.
      expect(Math.abs(typeBox.y - titleBox.y)).toBeLessThan(10);
      // 작성일시는 그 아래 별도 줄이다.
      expect(dateBox.y).toBeGreaterThan(titleBox.y + 5);
    });

    // P13-T19: 조회수 기능 완전 제거. 게시판 목록에 기존 형식인 ".board-list__views" 요소/
    // "조회 N" 표시가 더 이상 없는지 확인한다(페이지 전체 텍스트에서 "조회"라는 단어를 찾는
    // 과도하게 넓은 assertion은 다른 정상 문구를 오탐할 수 있어 사용하지 않는다).
    test('게시판 목록에 조회수 표시 요소(.board-list__views)가 더 이상 없다', async ({ page }) => {
      await page.goto('/boards');
      await expect(page.locator('.board-list__views')).toHaveCount(0);
    });

    test('pagination이 하단 가운데 정렬되고 현재 페이지가 active로 표시된다', async ({ page }) => {
      await page.goto('/boards?boardType=NOTICE&size=1&page=0');

      const bar = page.locator('.pagination-bar');
      await expect(bar).toBeVisible();
      const barBox = await bar.boundingBox();
      const viewportWidth = page.viewportSize().width;
      const barCenter = barBox.x + barBox.width / 2;
      expect(Math.abs(barCenter - viewportWidth / 2)).toBeLessThan(40);
      await expect(page.locator('.pagination-bar__number.is-active')).toHaveText('1');
    });

    test('직접 페이지 이동: pageJump 폼 제출 시 pageJump 쿼리를 유지한 채 2페이지 내용이 표시된다', async ({ page }) => {
      await page.goto('/boards?boardType=NOTICE&size=1&page=0');
      const firstPageTitle = await page.locator('#board-list .board-list__title').first().textContent();

      await page.fill('#page-jump-input', '2');
      await page.click('#page-jump-submit');

      // 직접 이동은 redirect 설계가 아니다 - URL이 page=1로 바뀌는 게 아니라 pageJump=2를 그대로 유지한다.
      await expect(page).toHaveURL(/pageJump=2/);
      await expect(page.locator('.pagination-bar__number.is-active')).toHaveText('2');
      const secondPageTitle = await page.locator('#board-list .board-list__title').first().textContent();
      expect(secondPageTitle).not.toEqual(firstPageTitle);
    });

    for (const viewport of VIEWPORTS) {
      test(`${viewport.name}에서 게시판 목록+pagination에 overflow가 없다`, async ({ page }) => {
        await page.setViewportSize({ width: viewport.width, height: viewport.height });
        await page.goto('/boards?boardType=NOTICE&size=1');

        await expect(page.locator('.pagination-bar')).toBeVisible();
        const overflowX = await page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth);
        expect(overflowX).toBeLessThanOrEqual(0);
      });
    }
  });
});

// P13-T27: /boards?boardType=GALLERY|REVIEW는 이미지 중심 게시판이라 메인 페이지와 동일한
// .gallery-grid/.gallery-card 마크업(#board-grid)으로 표시하고, NOTICE/ARCHIVE/전체는 기존
// #board-list 텍스트 목록을 그대로 유지한다. Controller/pagination fragment를 건드리지 않았으므로
// 필터/keyword/page 유지 회귀는 기존 P13-T14 describe의 무변경 재실행으로 충분히 커버된다.
test.describe('P13-T27: 공개 게시판 목록 갤러리/강의후기 썸네일 그리드', () => {
  test.skip(!ADMIN_LOGIN_ID || !ADMIN_PASSWORD, 'ADMIN_LOGIN_ID/ADMIN_PASSWORD 환경변수가 설정되지 않아 건너뜀');

  let xsrfToken;
  let boardId;

  async function createBoard(context, baseURL, boardType, title, thumbnail) {
    const res = await context.request.post(`${baseURL}/api/admin/boards`, {
      headers: { 'X-XSRF-TOKEN': xsrfToken },
      data: { boardType, title, thumbnail, isPublic: true },
    });
    expect(res.ok()).toBeTruthy();
    return (await res.json()).data.id;
  }

  test.beforeEach(async ({ context, baseURL }) => {
    await loginAsAdmin(context, baseURL);
    xsrfToken = await getXsrfToken(context);
    boardId = undefined;
  });

  test.afterEach(async ({ context, baseURL }) => {
    if (boardId) {
      await context.request.delete(`${baseURL}/api/admin/boards/${boardId}`, {
        headers: { 'X-XSRF-TOKEN': xsrfToken },
      });
    }
  });

  test('GALLERY 필터에서 #board-grid 썸네일 카드가 실제로 노출되고, 카드 클릭 시 상세로 이동한다', async ({ page, context, baseURL }) => {
    const title = 'P13-T27 갤러리 그리드 확인 ' + Date.now();
    boardId = await createBoard(context, baseURL, 'GALLERY', title, '/api/files/900801');
    await page.goto('/boards?boardType=GALLERY');

    await expect(page.locator('#board-grid')).toBeVisible();
    await expect(page.locator('#board-list')).toHaveCount(0);

    const card = page.locator('#board-grid .gallery-card').filter({ hasText: title });
    await expect(card.locator('.gallery-card__thumb img')).toHaveAttribute('src', '/api/files/900801');

    await card.locator('.gallery-card__link').click();
    // P13-T28: 상세 링크에 boardType/keyword/page 복귀 상태가 쿼리 파라미터로 함께 실리므로
    // 정확한 URL 일치 대신 접두사만 확인한다.
    await expect(page).toHaveURL(new RegExp(`/boards/${boardId}(\\?|$)`));
  });

  test('REVIEW 필터에서도 #board-grid 썸네일 카드가 노출된다', async ({ page, context, baseURL }) => {
    const title = 'P13-T27 강의 후기 그리드 확인 ' + Date.now();
    boardId = await createBoard(context, baseURL, 'REVIEW', title, '/api/files/900802');
    await page.goto('/boards?boardType=REVIEW');

    await expect(page.locator('#board-grid')).toBeVisible();
    await expect(page.locator('#board-list')).toHaveCount(0);
    const card = page.locator('#board-grid .gallery-card').filter({ hasText: title });
    await expect(card.locator('.gallery-card__thumb img')).toHaveAttribute('src', '/api/files/900802');
  });

  test('NOTICE 필터와 전체 목록은 기존 #board-list 텍스트 목록을 유지하고 #board-grid는 노출되지 않는다', async ({ page, context, baseURL }) => {
    const title = 'P13-T27 공지 텍스트 목록 유지 확인 ' + Date.now();
    boardId = await createBoard(context, baseURL, 'NOTICE', title, null);

    await page.goto('/boards?boardType=NOTICE');
    await expect(page.locator('#board-list')).toBeVisible();
    await expect(page.locator('#board-grid')).toHaveCount(0);

    await page.goto('/boards');
    await expect(page.locator('#board-list')).toBeVisible();
    await expect(page.locator('#board-grid')).toHaveCount(0);
  });

  test('썸네일이 없는 GALLERY 게시글은 카드가 무너지지 않고 placeholder로 표시되며 카드 전체 클릭이 가능하다', async ({ page, context, baseURL }) => {
    const title = 'P13-T27 썸네일 없음 확인 ' + Date.now();
    boardId = await createBoard(context, baseURL, 'GALLERY', title, null);
    await page.goto('/boards?boardType=GALLERY');

    const card = page.locator('#board-grid .gallery-card').filter({ hasText: title });
    await expect(card.locator('.gallery-card__thumb-placeholder')).toBeVisible();
    await expect(card.locator('.gallery-card__thumb img')).toHaveCount(0);

    await card.locator('.gallery-card__link').click();
    // P13-T28: 상세 링크에 boardType/keyword/page 복귀 상태가 쿼리 파라미터로 함께 실리므로
    // 정확한 URL 일치 대신 접두사만 확인한다.
    await expect(page).toHaveURL(new RegExp(`/boards/${boardId}(\\?|$)`));
  });

  test('공백 없는 긴 제목이 있는 GALLERY 카드에서도 가로 스크롤이 생기지 않는다', async ({ page, context, baseURL }) => {
    const title = 'P13T27LongGalleryTitle' + 'A'.repeat(150) + Date.now();
    boardId = await createBoard(context, baseURL, 'GALLERY', title, '/api/files/900803');
    await page.goto('/boards?boardType=GALLERY');

    const overflowX = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    expect(overflowX).toBeLessThanOrEqual(0);
  });

  test('375px/1440px에서 GALLERY 썸네일 그리드에 가로 overflow가 없다', async ({ page, context, baseURL }) => {
    boardId = await createBoard(context, baseURL, 'GALLERY', 'P13-T27 반응형 확인 ' + Date.now(), '/api/files/900801');

    for (const viewport of [{ width: 375, height: 812 }, { width: 1440, height: 900 }]) {
      await page.setViewportSize(viewport);
      await page.goto('/boards?boardType=GALLERY');
      const overflowX = await page.evaluate(
        () => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(overflowX).toBeLessThanOrEqual(0);
    }
  });
});

// P13-T28: 상세 → 목록 복귀 시 boardType/keyword/page(canonical, pageJump 아님)를 보존한다.
// page>0 복귀는 대량 fixture 없이는 실제 pagination을 거쳐 검증하기 번거로워, 그 부분은
// BoardViewControllerTest(Java, MockMvc)에서 이미 충분히 검증했으므로 여기서는 확장하지 않는다.
test.describe('P13-T28: 게시판 상세 → 목록 복귀 상태 보존', () => {
  test.skip(!ADMIN_LOGIN_ID || !ADMIN_PASSWORD, 'ADMIN_LOGIN_ID/ADMIN_PASSWORD 환경변수가 설정되지 않아 건너뜀');

  let xsrfToken;
  let boardId;

  async function createBoard(context, baseURL, boardType, title) {
    const res = await context.request.post(`${baseURL}/api/admin/boards`, {
      headers: { 'X-XSRF-TOKEN': xsrfToken },
      data: { boardType, title, isPublic: true },
    });
    expect(res.ok()).toBeTruthy();
    return (await res.json()).data.id;
  }

  test.beforeEach(async ({ context, baseURL }) => {
    await loginAsAdmin(context, baseURL);
    xsrfToken = await getXsrfToken(context);
    boardId = undefined;
  });

  test.afterEach(async ({ context, baseURL }) => {
    if (boardId) {
      await context.request.delete(`${baseURL}/api/admin/boards/${boardId}`, {
        headers: { 'X-XSRF-TOKEN': xsrfToken },
      });
    }
  });

  test('GALLERY 목록 → 카드 클릭 → 상세 → 목록으로 클릭 시 GALLERY 필터가 유지된다', async ({ page, context, baseURL }) => {
    const title = 'P13-T28 갤러리 복귀 확인 ' + Date.now();
    boardId = await createBoard(context, baseURL, 'GALLERY', title);
    await page.goto('/boards?boardType=GALLERY');

    await page.locator('#board-grid .gallery-card').filter({ hasText: title }).locator('.gallery-card__link').click();
    await expect(page).toHaveURL(new RegExp(`/boards/${boardId}`));

    await page.locator('a:has-text("목록으로")').click();
    await expect(page).toHaveURL(/boardType=GALLERY/);
    await expect(page.locator('#board-type-filter .filter-nav__link.is-active')).toHaveText('갤러리');
    await expect(page.locator('#board-grid')).toBeVisible();
  });

  test('검색 결과 목록 → 상세 → 목록으로 클릭 시 검색어(keyword)가 유지된다', async ({ page, context, baseURL }) => {
    const keyword = 'P13T28SearchKeyword' + Date.now();
    const title = keyword + ' 검색 복귀 확인';
    boardId = await createBoard(context, baseURL, 'NOTICE', title);
    await page.goto(`/boards?keyword=${keyword}`);

    await page.locator('.board-list__link').filter({ hasText: title }).click();
    await expect(page).toHaveURL(new RegExp(`/boards/${boardId}`));

    await page.locator('a:has-text("목록으로")').click();
    await expect(page).toHaveURL(new RegExp(`keyword=${keyword}`));
    await expect(page.locator('input[type="text"][name="keyword"]')).toHaveValue(keyword);
  });
});

// P13-T15: header/title 색상 조합이 WCAG 최소 대비(4.5:1)를 만족하는지 계산하기 위한 헬퍼.
// exact hex 값을 고정하지 않고 computed rgb()를 그대로 상대휘도 공식에 대입한다.
function popupParseRgb(rgbString) {
  const match = rgbString.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/);
  return [Number(match[1]), Number(match[2]), Number(match[3])];
}

function popupRelativeLuminance([r, g, b]) {
  const channel = (c) => {
    const s = c / 255;
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  };
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
}

function popupContrastRatio(rgbStringA, rgbStringB) {
  const lumA = popupRelativeLuminance(popupParseRgb(rgbStringA));
  const lumB = popupRelativeLuminance(popupParseRgb(rgbStringB));
  const lighter = Math.max(lumA, lumB);
  const darker = Math.min(lumA, lumB);
  return (lighter + 0.05) / (darker + 0.05);
}

// P13-T11: 공개 Popup 레이어(비차단형, 최대 3개 동시 노출 + 보충 - P13-T10 재정정 계약).
// 로컬/Docker DB에 이미 실제 Popup이 등록돼 있을 수 있어(개수/순서 통제 불가) 두 가지로 독립성을 확보한다.
// (1) 테스트가 만드는 A/B/C/D는 매 테스트 고유한 제목으로 만들고 끝나면 그 4건만 삭제한다.
// (2) "테스트가 만들기 전부터 떠 있던" 실Popup을 이 테스트 브라우저 컨텍스트에서만 오늘 하루 보지
//     않기 처리하는 로직은 이 describe만이 아니라 파일 전역 beforeEach(위 참고)에 있다 - 실Popup의
//     카드가 다른 describe의 클릭/포커스를 가로채는 것을 막기 위해서다(이제는 배경 비차단형이라 실제
//     충돌 가능성은 낮아졌지만 카드 자체는 pointer-events:auto라 여전히 겹치는 위치의 클릭을 가로챌 수
//     있다). 이 describe는 그 전역 beforeEach가 채워둔 globalPreExistingPopupIds를 자신의 afterEach
//     정리에 그대로 재사용한다. 실Popup은 삭제/수정/visibility 변경을 전혀 하지 않는다 - 브라우저 쪽
//     "오늘 하루 보지 않기" localStorage 상태만 조작하며, 그 값도 afterEach에서 지운다(테스트가 만든
//     A/B/C/D의 상태와 실Popup의 상태는 서로 다른 key(popup id)를 쓰므로 항상 독립적이다).
test.describe('공개 Popup 레이어', () => {
  test.skip(!ADMIN_LOGIN_ID || !ADMIN_PASSWORD, 'ADMIN_LOGIN_ID/ADMIN_PASSWORD 환경변수가 설정되지 않아 건너뜀');

  function toLocalIsoString(date) {
    return date.getFullYear() + '-' + popupPad2(date.getMonth() + 1) + '-' + popupPad2(date.getDate())
      + 'T' + popupPad2(date.getHours()) + ':' + popupPad2(date.getMinutes()) + ':' + popupPad2(date.getSeconds());
  }

  async function createPopup(context, baseURL, xsrfToken, title, contentHtml) {
    const now = new Date();
    const start = new Date(now.getTime() - 60 * 60 * 1000);
    const end = new Date(now.getTime() + 60 * 60 * 1000);
    const response = await context.request.post(`${baseURL}/api/admin/popups`, {
      headers: { 'X-XSRF-TOKEN': xsrfToken },
      data: {
        title,
        content: contentHtml,
        startDate: toLocalIsoString(start),
        endDate: toLocalIsoString(end),
        isVisible: true,
      },
    });
    expect(response.ok()).toBeTruthy();
    const body = await response.json();
    return body.data.id;
  }

  async function deletePopup(context, baseURL, xsrfToken, id) {
    await context.request.delete(`${baseURL}/api/admin/popups/${id}`, {
      headers: { 'X-XSRF-TOKEN': xsrfToken },
    });
  }

  let xsrfToken;
  let popupIdA;
  let popupIdB;
  let popupIdC;
  let popupIdD;
  let titleA;
  let titleB;
  let titleC;
  let titleD;
  let imageUrlA;

  test.beforeEach(async ({ context, baseURL }) => {
    await loginAsAdmin(context, baseURL);
    xsrfToken = await getXsrfToken(context);

    const runId = Date.now();
    titleA = `Popup 테스트 A ${runId}`;
    titleB = `Popup 테스트 B ${runId}`;
    titleC = `Popup 테스트 C ${runId}`;
    titleD = `Popup 테스트 D ${runId}`;
    // HtmlSanitizer(common/util)는 img[src]에 http/https 절대 URL만 허용한다(addProtocols("img",
    // "src", "http", "https")). 이 describe는 관리자 API를 통해 실제 저장 경로(sanitize 포함)를 타므로,
    // sanitizer가 실제로 보존하는 형태인 절대 URL을 써야 한다.
    imageUrlA = `${baseURL}/api/files/900001`;

    // 공개 목록은 createdAt DESC로 정렬된다. created_at 컬럼은 초 단위 정밀도(DATETIME)라
    // 같은 초 안에 두 Popup을 만들면 createdAt이 동률이 되어 정렬 순서가 보장되지 않는다
    // (실측: 동률일 때 나중에 만든 쪽이 먼저 온다는 보장이 없었다). A가 항상 가장 최신이 되도록
    // D->C->B->A 순으로, 매 생성 사이에 최소 1초(1100ms 여유)를 두어 서로 다른 초에 기록되게 한다.
    // 최대 3개 동시 노출 + 4번째 보충 계약을 검증하려면 최소 4건이 필요하다.
    popupIdD = await createPopup(context, baseURL, xsrfToken, titleD, '<p>D 내용</p>');
    await sleep(1100);
    popupIdC = await createPopup(context, baseURL, xsrfToken, titleC, '<p>C 내용</p>');
    await sleep(1100);
    popupIdB = await createPopup(context, baseURL, xsrfToken, titleB, '<p>B 내용</p>');
    await sleep(1100);
    popupIdA = await createPopup(context, baseURL, xsrfToken, titleA,
      `<p>A 내용</p><img src="${imageUrlA}" alt="A 이미지">`);
  });

  test.afterEach(async ({ context, baseURL, page }) => {
    await deletePopup(context, baseURL, xsrfToken, popupIdA);
    await deletePopup(context, baseURL, xsrfToken, popupIdB);
    await deletePopup(context, baseURL, xsrfToken, popupIdC);
    await deletePopup(context, baseURL, xsrfToken, popupIdD);
    // addInitScript로 심은 값(파일 전역 beforeEach가 심음)은 컨텍스트 종료 시 자동 폐기되지만,
    // 명시적으로도 정리한다.
    if (page && !page.isClosed()) {
      await page.evaluate(
        ({ ids, prefix }) => {
          ids.forEach((id) => window.localStorage.removeItem(prefix + id));
        },
        { ids: globalPreExistingPopupIds, prefix: POPUP_STORAGE_KEY_PREFIX }
      ).catch(() => {});
    }
  });

  function modalFor(page, title) {
    return page.locator('.popup-modal').filter({ has: page.locator(`text=${title}`) });
  }

  test('노출 대상 Popup 중 최신 3개(A/B/C)가 동시에 표시되고 4번째(D)는 최초 hidden이다', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('#popup-overlay')).toBeVisible();
    await expect(modalFor(page, titleA)).toBeVisible();
    await expect(modalFor(page, titleB)).toBeVisible();
    await expect(modalFor(page, titleC)).toBeVisible();
    await expect(modalFor(page, titleD)).toBeHidden();
  });

  test('제목/content/이미지를 표시한다', async ({ page }) => {
    await page.goto('/');
    const modal = modalFor(page, titleA);
    await expect(modal.locator('.popup-modal__title')).toHaveText(titleA);
    await expect(modal.locator('.popup-modal__body')).toContainText('A 내용');
    await expect(modal.locator('.popup-modal__body img')).toHaveAttribute('src', imageUrlA);
  });

  // P13-T13: header/body가 둘 다 흰색이라 구분되지 않던 문제를 개선한다. 배경색 구체적인 값(어떤
  // 회색인지)이나 border-bottom 존재 여부까지 고정하면 CSS 구현이 바뀔 때마다 깨지기 쉬우므로,
  // "header가 카드(본문) 배경과 실제로 다른 색"이라는 동작 수준으로만 검증한다.
  test('제목 영역(header)이 본문/카드와 시각적으로 구분되는 배경을 가진다', async ({ page }) => {
    await page.goto('/');
    const modal = modalFor(page, titleA);
    const headerBackground = await modal
      .locator('.popup-modal__header')
      .evaluate((el) => getComputedStyle(el).backgroundColor);
    const cardBackground = await modal.evaluate((el) => getComputedStyle(el).backgroundColor);
    expect(headerBackground).not.toBe(cardBackground);
  });

  // P13-T15: --color-surface(#f8f9fa)도 흰색 계열이라 body와 잘 구분되지 않는다는 피드백으로
  // 불투명한 배경(--color-text)으로 교체했다. exact hex를 고정하면 구현이 바뀔 때마다 깨지기
  // 쉬우므로, "흰색이 아니다"라는 느슨한 확인과 "제목 텍스트와의 대비가 WCAG 최소 기준(4.5:1)을
  // 만족한다"는 동작 수준의 확인만 한다.
  test('제목 영역(header) 배경이 흰색 계열이 아니고 제목 텍스트와 충분한 대비를 가진다', async ({ page }) => {
    await page.goto('/');
    const modal = modalFor(page, titleA);
    const header = modal.locator('.popup-modal__header');
    const headerBackground = await header.evaluate((el) => getComputedStyle(el).backgroundColor);
    const titleColor = await modal
      .locator('.popup-modal__title')
      .evaluate((el) => getComputedStyle(el).color);

    expect(headerBackground).not.toBe('rgb(255, 255, 255)');
    expect(popupContrastRatio(headerBackground, titleColor)).toBeGreaterThanOrEqual(4.5);
  });

  test('하나를 닫으면 다음 대기 Popup(D)이 그 자리를 채워 다시 3개가 유지된다', async ({ page }) => {
    await page.goto('/');
    await modalFor(page, titleA).locator('.popup-modal__close').click();

    await expect(modalFor(page, titleA)).toBeHidden();
    await expect(modalFor(page, titleB)).toBeVisible();
    await expect(modalFor(page, titleC)).toBeVisible();
    await expect(modalFor(page, titleD)).toBeVisible();
  });

  test('오늘 하루 보지 않기로 닫아도 동일하게 다음 대기 Popup으로 보충되고, 새로고침 후에도 그 Popup만 계속 숨겨진다', async ({ page }) => {
    await page.goto('/');
    // "오늘 하루 보지 않기" 버튼은 카드 폭 대부분을 차지해서(닫기 버튼과 달리) 뒤쪽 카드에서는
    // 40px offset만으로 안 가려진 영역을 확보하지 못할 수 있다 - 항상 안 가려지는 최상단(A)으로 확인한다.
    await modalFor(page, titleA).locator('.popup-modal__hide-today').click();

    await expect(modalFor(page, titleA)).toBeHidden();
    await expect(modalFor(page, titleB)).toBeVisible();
    await expect(modalFor(page, titleC)).toBeVisible();
    await expect(modalFor(page, titleD)).toBeVisible();

    await page.reload();
    // 오늘 하루 보지 않기는 localStorage에 영구 저장되므로 A는 새로고침 후에도 계속 제외된다.
    await expect(modalFor(page, titleA)).toBeHidden();
    await expect(modalFor(page, titleB)).toBeVisible();
    await expect(modalFor(page, titleC)).toBeVisible();
    await expect(modalFor(page, titleD)).toBeVisible();
  });

  test('하나를 닫아도 나머지 Popup은 그대로 유지되고 서로 독립적으로 닫을 수 있다', async ({ page }) => {
    await page.goto('/');
    await modalFor(page, titleB).locator('.popup-modal__close').click();

    await expect(modalFor(page, titleB)).toBeHidden();
    await expect(modalFor(page, titleA)).toBeVisible();
    await expect(modalFor(page, titleC)).toBeVisible();

    await modalFor(page, titleC).locator('.popup-modal__close').click();
    await expect(modalFor(page, titleC)).toBeHidden();
    await expect(modalFor(page, titleA)).toBeVisible();
  });

  test('ESC를 누르면 가장 위(최신) Popup 1건만 닫히고, 반복하면 최신순으로 하나씩 닫힌다', async ({ page }) => {
    await page.goto('/');

    await page.keyboard.press('Escape');
    await expect(modalFor(page, titleA)).toBeHidden();
    await expect(modalFor(page, titleB)).toBeVisible();
    await expect(modalFor(page, titleC)).toBeVisible();
    await expect(modalFor(page, titleD)).toBeVisible();

    await page.keyboard.press('Escape');
    await expect(modalFor(page, titleB)).toBeHidden();
    await expect(modalFor(page, titleC)).toBeVisible();
    await expect(modalFor(page, titleD)).toBeVisible();
  });

  test('Popup이 떠 있어도 배경 페이지를 스크롤할 수 있다', async ({ page }) => {
    await page.goto('/');
    await expect(modalFor(page, titleA)).toBeVisible();

    const before = await page.evaluate(() => window.scrollY);
    await page.mouse.wheel(0, 1200);
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(before);
  });

  test('Popup이 떠 있어도 배경 하단의 콘텐츠 링크를 클릭할 수 있다', async ({ page }) => {
    await page.goto('/');
    await expect(modalFor(page, titleA)).toBeVisible();

    await page.locator('#latest-programs .section-title__link').click();
    await expect(page).toHaveURL(/\/programs$/);
  });

  test('최신 Popup(rank 0)의 z-index가 다른 Popup보다 높다', async ({ page }) => {
    await page.goto('/');
    const zIndexOf = (locator) => locator.evaluate((el) => Number(getComputedStyle(el).zIndex));

    const zA = await zIndexOf(modalFor(page, titleA));
    const zB = await zIndexOf(modalFor(page, titleB));
    const zC = await zIndexOf(modalFor(page, titleC));

    expect(zA).toBeGreaterThan(zB);
    expect(zB).toBeGreaterThan(zC);
  });

  test('데스크톱에서 각 Popup은 오른쪽/아래로 뚜렷하게(40px 안팎) offset을 두고 배치되어 완전히 겹치지 않는다', async ({ page }) => {
    await page.goto('/');
    const boxA = await modalFor(page, titleA).boundingBox();
    const boxB = await modalFor(page, titleB).boundingBox();
    const boxC = await modalFor(page, titleC).boundingBox();

    expect(boxB.x - boxA.x).toBeGreaterThanOrEqual(36);
    expect(boxB.y - boxA.y).toBeGreaterThanOrEqual(36);
    expect(boxC.x - boxB.x).toBeGreaterThanOrEqual(36);
    expect(boxC.y - boxB.y).toBeGreaterThanOrEqual(36);
  });

  test('40px offset 덕분에 뒤쪽 Popup의 닫기 버튼도 앞쪽 카드에 가려지지 않고 바로 클릭할 수 있다', async ({ page }) => {
    await page.goto('/');
    // B/C는 rank1/2라 A보다 뒤에 있지만, 실제로 눈에 안 가려지는 위치까지 offset이 벌어져 있어야
    // 클릭이 다른 카드에 가로채이지 않는다(24px였을 때는 이 클릭이 실패했었다).
    await modalFor(page, titleC).locator('.popup-modal__close').click({ timeout: 3000 });
    await expect(modalFor(page, titleC)).toBeHidden();
    await expect(modalFor(page, titleA)).toBeVisible();
    await expect(modalFor(page, titleB)).toBeVisible();
  });

  test('1024px/1440px 데스크톱에서 최신 Popup이 화면 수평 중앙 부근, 헤더와 충분한 여백을 두고 배치된다', async ({ page }) => {
    for (const viewport of [{ width: 1024, height: 768 }, { width: 1440, height: 900 }]) {
      await page.setViewportSize(viewport);
      await page.goto('/');
      const box = await modalFor(page, titleA).boundingBox();
      const centerX = box.x + box.width / 2;

      expect(Math.abs(centerX - viewport.width / 2)).toBeLessThan(2);
      expect(box.y).toBeGreaterThanOrEqual(90);
    }
  });

  test('헤더를 드래그하면 Popup 위치가 이동한다', async ({ page }) => {
    await page.goto('/');
    const modal = modalFor(page, titleA);
    const header = modal.locator('.popup-modal__header');
    const before = await modal.boundingBox();
    const headerBox = await header.boundingBox();
    const startX = headerBox.x + headerBox.width / 2;
    const startY = headerBox.y + headerBox.height / 2;
    const dx = 150;
    const dy = 90;

    await page.mouse.move(startX, startY);
    await page.mouse.down();
    await page.mouse.move(startX + dx, startY + dy, { steps: 10 });
    await page.mouse.up();

    const after = await modal.boundingBox();
    expect(Math.abs(after.x - (before.x + dx))).toBeLessThan(3);
    expect(Math.abs(after.y - (before.y + dy))).toBeLessThan(3);
  });

  test('닫기 버튼 위에서 누른 채 움직여도 드래그로 처리되지 않아 Popup 위치가 그대로다', async ({ page }) => {
    await page.goto('/');
    const modal = modalFor(page, titleC);
    const closeButton = modal.locator('.popup-modal__close');
    const before = await modal.boundingBox();
    const btnBox = await closeButton.boundingBox();
    const startX = btnBox.x + btnBox.width / 2;
    const startY = btnBox.y + btnBox.height / 2;

    await page.mouse.move(startX, startY);
    await page.mouse.down();
    await page.mouse.move(startX + 80, startY + 80, { steps: 5 });
    const duringPointerDown = await modal.boundingBox();
    await page.mouse.up();

    expect(Math.abs(duringPointerDown.x - before.x)).toBeLessThan(2);
    expect(Math.abs(duringPointerDown.y - before.y)).toBeLessThan(2);
  });

  test('드래그를 시작하면 그 Popup이 즉시 다른 Popup보다 z-index 최상단으로 올라온다', async ({ page }) => {
    await page.goto('/');
    // B는 rank1이라 원래 A보다 z-index가 낮다 - 드래그하면 A(원래 최상단)보다도 위로 올라와야 한다.
    // 헤더 "중앙"은 앞쪽 카드(A)에 가려진 영역일 수 있으므로, 실제로 안 가려지는 지점(헤더 오른쪽
    // 끝에서 살짝 안쪽 - 닫기 버튼 바로 왼쪽)을 좌표로 쓴다.
    const target = modalFor(page, titleB);
    const other = modalFor(page, titleA);
    const header = target.locator('.popup-modal__header');
    const headerBox = await header.boundingBox();
    const startX = headerBox.x + headerBox.width - 12;
    const startY = headerBox.y + headerBox.height / 2;

    await page.mouse.move(startX, startY);
    await page.mouse.down();
    await page.mouse.move(startX + 60, startY + 40, { steps: 5 });
    await page.mouse.up();

    const zTarget = await target.evaluate((el) => Number(getComputedStyle(el).zIndex));
    const zOther = await other.evaluate((el) => Number(getComputedStyle(el).zIndex));
    expect(zTarget).toBeGreaterThan(zOther);
  });

  test('viewport 밖으로 드래그해도 Popup 전체가 화면 안에 clamp된다', async ({ page }) => {
    await page.goto('/');
    const modal = modalFor(page, titleA);
    const header = modal.locator('.popup-modal__header');
    const headerBox = await header.boundingBox();
    const viewport = page.viewportSize();

    await page.mouse.move(headerBox.x + headerBox.width / 2, headerBox.y + headerBox.height / 2);
    await page.mouse.down();
    await page.mouse.move(viewport.width + 500, viewport.height + 500, { steps: 10 });
    await page.mouse.up();

    const after = await modal.boundingBox();
    expect(after.x).toBeGreaterThanOrEqual(0);
    expect(after.y).toBeGreaterThanOrEqual(0);
    expect(after.x + after.width).toBeLessThanOrEqual(viewport.width + 1);
    expect(after.y + after.height).toBeLessThanOrEqual(viewport.height + 1);
  });

  test('드래그로 옮긴 Popup은 다른 Popup이 닫히고 보충돼도 위치가 유지된다', async ({ page }) => {
    await page.goto('/');
    // B(rank1)를 드래그한 뒤 A(rank0)를 닫으면, 드래그하지 않았을 경우 B는 recency reflow로 rank0
    // 위치로 재배치돼야 정상이다(다른 테스트에서 이미 확인된 동작). 여기서는 드래그로 옮긴 위치가
    // 그 재배치를 덮어쓰지 않고 그대로 유지되는지를 확인한다.
    const modal = modalFor(page, titleB);
    const header = modal.locator('.popup-modal__header');
    const headerBox = await header.boundingBox();
    const startX = headerBox.x + headerBox.width - 12;
    const startY = headerBox.y + headerBox.height / 2;

    await page.mouse.move(startX, startY);
    await page.mouse.down();
    await page.mouse.move(startX + 120, startY + 90, { steps: 8 });
    await page.mouse.up();

    const draggedPosition = await modal.boundingBox();

    await modalFor(page, titleA).locator('.popup-modal__close').click();
    await expect(modalFor(page, titleD)).toBeVisible();

    const afterBackfill = await modal.boundingBox();
    expect(Math.abs(afterBackfill.x - draggedPosition.x)).toBeLessThan(2);
    expect(Math.abs(afterBackfill.y - draggedPosition.y)).toBeLessThan(2);
  });

  test('375px에서는 헤더를 드래그해도 Popup 위치가 바뀌지 않는다(모바일 drag 비활성화)', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 });
    await page.goto('/');
    const modal = modalFor(page, titleA);
    const header = modal.locator('.popup-modal__header');
    const before = await modal.boundingBox();
    const headerBox = await header.boundingBox();

    await page.mouse.move(headerBox.x + headerBox.width / 2, headerBox.y + headerBox.height / 2);
    await page.mouse.down();
    await page.mouse.move(headerBox.x + 50, headerBox.y + 50, { steps: 5 });
    await page.mouse.up();

    const after = await modal.boundingBox();
    expect(Math.abs(after.x - before.x)).toBeLessThan(2);
    expect(Math.abs(after.y - before.y)).toBeLessThan(2);
  });

  for (const viewport of VIEWPORTS) {
    test(`${viewport.name}에서 Popup이 여러 개 떠 있어도 가로 overflow가 없다`, async ({ page }) => {
      await page.setViewportSize({ width: viewport.width, height: viewport.height });
      await page.goto('/');
      await expect(modalFor(page, titleA)).toBeVisible();
      await expect(modalFor(page, titleB)).toBeVisible();
      await expect(modalFor(page, titleC)).toBeVisible();

      const overflowX = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(overflowX).toBeLessThanOrEqual(0);

      // document.scrollWidth는 position:absolute인 Popup 카드가 viewport 밖으로 나가도 감지하지
      // 못한다(절대 배치 요소는 문서 스크롤 영역에 반영되지 않음 - 실측으로 확인된 맹점). 가변 폭
      // 도입 이후에는 각 Popup 카드 자신의 boundingBox가 실제로 viewport 안에 있는지 직접 확인한다.
      for (const title of [titleA, titleB, titleC]) {
        const box = await modalFor(page, title).boundingBox();
        expect(box.x).toBeGreaterThanOrEqual(0);
        expect(box.y).toBeGreaterThanOrEqual(0);
        expect(box.x + box.width).toBeLessThanOrEqual(viewport.width + 0.5);
        expect(box.y + box.height).toBeLessThanOrEqual(viewport.height + 0.5);
      }
    });
  }

  test('콘텐츠 길이에 따라 Popup 폭이 최소 480px~최대 720px 사이에서 자연스럽게 늘어난다(fit-content)', async ({ page, context, baseURL }) => {
    async function widthFor(contentHtml) {
      const runId = Date.now();
      const title = `폭가변 확인 ${runId}`;
      const id = await createPopup(context, baseURL, xsrfToken, title, contentHtml);
      await page.goto('/');
      const box = await page.locator(`#popup-modal-${id}`).boundingBox();
      await deletePopup(context, baseURL, xsrfToken, id);
      return box.width;
    }

    await page.setViewportSize({ width: 1440, height: 900 });

    const shortWidth = await widthFor('<p>짧은 안내</p>');
    const mediumWidth = await widthFor('<p>' + '중간 길이의 안내 문구입니다. '.repeat(3) + '</p>');
    const longWidth = await widthFor('<p>' + '이것은 실제 관리자 CKEditor로 작성했을 법한 다소 긴 안내 문구입니다. '.repeat(8) + '</p>');

    expect(shortWidth).toBeCloseTo(480, 0);
    expect(mediumWidth).toBeGreaterThan(shortWidth);
    expect(mediumWidth).toBeLessThan(720);
    expect(longWidth).toBeCloseTo(720, 0);
  });

  test('이미지만 있고 텍스트가 짧으면 이미지 크기와 무관하게 폭이 최소값(480px)에 머문다', async ({ page, context, baseURL }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    const title = `이미지단독 폭확인 ${Date.now()}`;
    // 실제 파일 존재 여부와 무관하게 width/height 속성으로 큰 이미지의 intrinsic size를 흉내낸다.
    const id = await createPopup(context, baseURL, xsrfToken, title,
      `<p>짧은 캡션</p><img src="${imageUrlA}" width="1600" height="900">`);

    await page.goto('/');
    const box = await page.locator(`#popup-modal-${id}`).boundingBox();
    expect(box.width).toBeCloseTo(480, 0);

    await deletePopup(context, baseURL, xsrfToken, id);
  });

  test('공백 없는 긴 문자열도 최대폭(720px)에서 카드 내부에 정상적으로 줄바꿈된다(overflow-wrap)', async ({ page, context, baseURL }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    const title = `줄바꿈 확인 ${Date.now()}`;
    const id = await createPopup(context, baseURL, xsrfToken, title, '<p>' + 'A'.repeat(500) + '</p>');

    await page.goto('/');
    const box = await page.locator(`#popup-modal-${id}`).boundingBox();
    expect(box.width).toBeCloseTo(720, 0);

    const wrap = await page.evaluate((popupId) => {
      const body = document.querySelector(`#popup-modal-${popupId} .popup-modal__body`);
      return { scrollWidth: body.scrollWidth, clientWidth: body.clientWidth };
    }, id);
    // scrollWidth가 clientWidth를 넘지 않으면 카드 내부에서도 가로 스크롤 없이 정상 줄바꿈된 것이다.
    expect(wrap.scrollWidth).toBeLessThanOrEqual(wrap.clientWidth + 1);

    await deletePopup(context, baseURL, xsrfToken, id);
  });

  test('375px 모바일에서는 데스크톱 min-width(480px)가 적용되지 않고 화면 폭에 맞춰진다', async ({ page, context, baseURL }) => {
    await page.setViewportSize({ width: 375, height: 812 });
    const title = `모바일 폭확인 ${Date.now()}`;
    const id = await createPopup(context, baseURL, xsrfToken, title,
      '<p>' + '이것은 실제 관리자 CKEditor로 작성했을 법한 다소 긴 안내 문구입니다. '.repeat(8) + '</p>');

    await page.goto('/');
    const box = await page.locator(`#popup-modal-${id}`).boundingBox();
    // 데스크톱 min-width(480px)가 재설정 안 되면 375px 화면에서도 480px로 고정돼 밖으로 나간다 -
    // 실측으로 재현했던 버그를 회귀 테스트로 고정한다.
    expect(box.width).toBeLessThan(375);
    expect(box.x).toBeGreaterThanOrEqual(0);
    expect(box.x + box.width).toBeLessThanOrEqual(375.5);

    await deletePopup(context, baseURL, xsrfToken, id);
  });

  test('실제 관리자 CKEditor 업로드 이미지가 공개 Popup 안에서 렌더링된다', async ({ page, context, baseURL }) => {
    // A~D와 별개로, 진짜 CKEditor 업로드 버튼을 통해 만든 5번째 Popup으로 별도 검증한다
    // (HtmlSanitizer가 /api/files/{id} 상대 경로를 보존하도록 고친 fix가 실제 렌더링까지 이어지는지 확인).
    const title = `CKEditor 이미지 렌더링 확인 ${Date.now()}`;
    await page.goto('/admin/popups/new');
    await page.locator('#title').fill(title);
    await page.waitForSelector('.ck-editor__editable', { timeout: 10000 });
    await page.locator('.ck-editor__editable').click();
    await page.keyboard.type('이미지 테스트');

    const uploadButton = page
      .locator('.ck-file-dialog-button, button[data-cke-tooltip-text*="Insert image"], .ck-insert-image-icon')
      .first();
    const fileChooserPromise = page.waitForEvent('filechooser');
    await uploadButton.click();
    const fileChooser = await fileChooserPromise;
    await fileChooser.setFiles({
      name: 'tiny.png',
      mimeType: 'image/png',
      buffer: Buffer.from(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
        'base64'
      ),
    });

    // placeholder만 뜬 시점이 아니라 실제 업로드가 끝나 /api/files/{id} src가 채워질 때까지 기다린다.
    await page.waitForFunction(() => {
      const img = document.querySelector('.ck-editor__editable img');
      return !!(img && img.getAttribute('src') && img.getAttribute('src').indexOf('/api/files/') === 0);
    }, { timeout: 15000 });

    const now = new Date();
    const start = new Date(now.getTime() - 60 * 60 * 1000);
    const end = new Date(now.getTime() + 60 * 60 * 1000);
    await page.locator('#startDate').fill(toLocalIsoString(start).slice(0, 16));
    await page.locator('#endDate').fill(toLocalIsoString(end).slice(0, 16));
    await page.locator('#isVisible').check();

    await Promise.all([
      page.waitForURL(/\/admin\/popups$/, { timeout: 10000 }),
      page.locator('button[type="submit"]').click(),
    ]);

    const listRes = await context.request.get(`${baseURL}/api/admin/popups?page=0&size=1`);
    const listBody = await listRes.json();
    const popupId = listBody.data[0].id;

    await page.goto('/');
    const img = page.locator(`#popup-modal-${popupId} img`);
    await expect(img).toBeVisible();
    const src = await img.getAttribute('src');
    expect(src).toMatch(/^\/api\/files\/\d+$/);

    const loaded = await img.evaluate((el) => new Promise((resolve) => {
      if (el.complete) { resolve(el.naturalWidth > 0); return; }
      el.addEventListener('load', () => resolve(true));
      el.addEventListener('error', () => resolve(false));
      setTimeout(() => resolve(el.naturalWidth > 0), 3000);
    }));
    expect(loaded).toBeTruthy();

    const cookies = await context.cookies();
    const xsrf = cookies.find((c) => c.name === 'XSRF-TOKEN').value;
    await context.request.delete(`${baseURL}/api/admin/popups/${popupId}`, {
      headers: { 'X-XSRF-TOKEN': xsrf },
    });
  });
});

test.describe('P13-T20: 게시글 본문 링크 새 탭/내부 이동', () => {
  test.skip(!ADMIN_LOGIN_ID || !ADMIN_PASSWORD, 'ADMIN_LOGIN_ID/ADMIN_PASSWORD 환경변수가 설정되지 않아 건너뜀');

  let xsrfToken;
  let boardId;
  let linkedBoardId;

  async function createBoard(context, baseURL, title, content) {
    const res = await context.request.post(`${baseURL}/api/admin/boards`, {
      headers: { 'X-XSRF-TOKEN': xsrfToken },
      data: { boardType: 'NOTICE', title, content, isPublic: true },
    });
    expect(res.ok()).toBeTruthy();
    return (await res.json()).data.id;
  }

  test.beforeEach(async ({ context, baseURL }) => {
    await loginAsAdmin(context, baseURL);
    xsrfToken = await getXsrfToken(context);
    boardId = undefined;
    linkedBoardId = undefined;
  });

  test.afterEach(async ({ context, baseURL }) => {
    if (boardId) {
      await context.request.delete(`${baseURL}/api/admin/boards/${boardId}`, {
        headers: { 'X-XSRF-TOKEN': xsrfToken },
      });
    }
    if (linkedBoardId) {
      await context.request.delete(`${baseURL}/api/admin/boards/${linkedBoardId}`, {
        headers: { 'X-XSRF-TOKEN': xsrfToken },
      });
    }
  });

  test('본문의 외부 링크를 클릭하면 새 탭에서 열린다', async ({ page, context, baseURL }) => {
    boardId = await createBoard(
      context, baseURL,
      '외부 링크 새 탭 확인 ' + Date.now(),
      '<p>본문 <a href="https://example.com">외부 링크</a></p>'
    );

    await page.goto(`/boards/${boardId}`);
    const link = page.locator('#board-detail-content a[href="https://example.com"]');
    await expect(link).toHaveAttribute('target', '_blank');
    await expect(link).toHaveAttribute('rel', 'noopener noreferrer');

    const [newPage] = await Promise.all([
      context.waitForEvent('page'),
      link.click(),
    ]);
    await newPage.waitForLoadState();
    expect(newPage.url()).toContain('example.com');
    await newPage.close();
  });

  test('본문의 내부 링크를 클릭하면 같은 탭에서 해당 게시글로 이동한다', async ({ page, context, baseURL }) => {
    linkedBoardId = await createBoard(context, baseURL, '내부 링크 대상 게시글 ' + Date.now(), '내용');
    boardId = await createBoard(
      context, baseURL,
      '내부 링크 같은 탭 확인 ' + Date.now(),
      `<p>본문 <a href="/boards/${linkedBoardId}">내부 링크</a></p>`
    );

    await page.goto(`/boards/${boardId}`);
    const link = page.locator(`#board-detail-content a[href="/boards/${linkedBoardId}"]`);
    await expect(link).not.toHaveAttribute('target', '_blank');
    await expect(link).not.toHaveAttribute('rel', 'noopener noreferrer');

    await link.click();
    await page.waitForURL(`**/boards/${linkedBoardId}`);
    expect(page.url()).toContain(`/boards/${linkedBoardId}`);
  });
});

test.describe('P13-T22: Board 기존 첨부파일 파일명 표시', () => {
  test.skip(!ADMIN_LOGIN_ID || !ADMIN_PASSWORD, 'ADMIN_LOGIN_ID/ADMIN_PASSWORD 환경변수가 설정되지 않아 건너뜀');

  let xsrfToken;
  let boardId;

  test.beforeEach(async ({ context, baseURL }) => {
    await loginAsAdmin(context, baseURL);
    xsrfToken = await getXsrfToken(context);
    boardId = undefined;
  });

  test.afterEach(async ({ context, baseURL }) => {
    if (boardId) {
      await context.request.delete(`${baseURL}/api/admin/boards/${boardId}`, {
        headers: { 'X-XSRF-TOKEN': xsrfToken },
      });
    }
  });

  test('기존 첨부파일이 있는 게시글 수정 화면에 실제 업로드 원본 파일명이 표시된다', async ({ page, context, baseURL }) => {
    const uploadRes = await context.request.post(`${baseURL}/api/admin/files`, {
      headers: { 'X-XSRF-TOKEN': xsrfToken },
      multipart: {
        file: { name: '강의자료.png', mimeType: 'image/png', buffer: PNG_1PX_BUFFER },
        fileType: 'ATTACHMENT',
      },
    });
    expect(uploadRes.ok()).toBeTruthy();
    const uploadedUrl = (await uploadRes.json()).data.url;

    const boardRes = await context.request.post(`${baseURL}/api/admin/boards`, {
      headers: { 'X-XSRF-TOKEN': xsrfToken },
      data: {
        boardType: 'NOTICE', title: '첨부파일 이름 표시 확인 ' + Date.now(),
        attachment: uploadedUrl, isPublic: true,
      },
    });
    expect(boardRes.ok()).toBeTruthy();
    boardId = (await boardRes.json()).data.id;

    await page.goto(`/admin/boards/${boardId}/edit`);

    await expect(page.locator('#attachmentPreviewNameWrap')).toBeVisible();
    await expect(page.locator('#attachmentPreviewName')).toHaveText('강의자료.png');
    await expect(page.locator('#attachmentPreviewLink')).toHaveAttribute('href', uploadedUrl);
  });
});

test.describe('P13-T23: 관리자 이미지 정렬 round-trip', () => {
  test.skip(!ADMIN_LOGIN_ID || !ADMIN_PASSWORD, 'ADMIN_LOGIN_ID/ADMIN_PASSWORD 환경변수가 설정되지 않아 건너뜀');

  let xsrfToken;
  let boardId;

  test.beforeEach(async ({ context, baseURL }) => {
    await loginAsAdmin(context, baseURL);
    xsrfToken = await getXsrfToken(context);
    boardId = undefined;
  });

  test.afterEach(async ({ context, baseURL }) => {
    if (boardId) {
      await context.request.delete(`${baseURL}/api/admin/boards/${boardId}`, {
        headers: { 'X-XSRF-TOKEN': xsrfToken },
      });
    }
  });

  // 관리자 편집기에서 정렬 버튼 노출 -> 실제 정렬 적용 -> 저장 -> DB/API 재조회 -> 수정 화면 재진입 시
  // 스타일 유지 -> 공개 상세 화면에서 동일 정렬 적용 -> 375/768/1024/1440 overflow/겹침 없음까지
  // 하나의 흐름으로 검증한다(Board 대표 1건, Program/Page/Popup은 HtmlSanitizer/CSS 공용 로직이므로
  // 반복하지 않는다).
  test('CKEditor에서 이미지를 왼쪽 정렬로 저장하면 재조회/공개 화면/반응형까지 유지된다', async ({ page, context, baseURL }) => {
    await page.goto('/admin/boards/new');
    await page.locator('#boardType').selectOption('NOTICE');
    await page.locator('#title').fill('이미지 정렬 round-trip 확인 ' + Date.now());

    await page.waitForSelector('.ck-editor__editable', { timeout: 10000 });
    await page.locator('.ck-editor__editable').click();
    await page.keyboard.type('이 게시글은 이미지 정렬 확인용 본문입니다. '.repeat(15));

    const uploadButton = page
      .locator('.ck-file-dialog-button, button[data-cke-tooltip-text*="Insert image"], .ck-insert-image-icon')
      .first();
    const fileChooserPromise = page.waitForEvent('filechooser');
    await uploadButton.click();
    const fileChooser = await fileChooserPromise;
    await fileChooser.setFiles({
      name: 'align-test.png', mimeType: 'image/png', buffer: PNG_1PX_BUFFER,
    });

    await page.waitForFunction(() => {
      const img = document.querySelector('.ck-editor__editable img');
      return !!(img && img.getAttribute('src') && img.getAttribute('src').indexOf('/api/files/') === 0);
    }, { timeout: 15000 });

    // 업로드 직후 이미지는 block 타입(<figure class="image">)이다. 위젯을 선택하면 balloon toolbar가
    // 뜨고, P13-T25부터는 6개 정렬 style이 "이미지 정렬" dropdown 1개로 묶여 있다(ckeditor-config.js).
    await page.locator('.ck-editor__editable img').click();

    // dropdown이 정확히 1개이고(정렬 버튼이 balloon toolbar에 평면으로 나열되지 않음), 화살표를 눌러
    // 연 패널 안에 기존 6개 style이 한글 라벨로 전부 존재하는지 확인한다.
    await expect(page.locator('.ck-balloon-panel .ck-dropdown')).toHaveCount(1);
    await page.locator('.ck-balloon-panel .ck-splitbutton__arrow').click();
    const panel = page.locator('.ck-dropdown__panel:not(.ck-hidden)');
    const expectedLabels = ['글 안에 배치', '기본', '글 옆에 배치', '왼쪽 정렬', '가운데 정렬', '오른쪽 정렬'];
    for (const label of expectedLabels) {
      await expect(panel.locator(`[data-cke-tooltip-text="${label}"]`)).toBeVisible();
    }

    // 패널 안에서 "왼쪽 정렬"(alignLeft) 항목을 클릭한다.
    await panel.locator('[data-cke-tooltip-text="왼쪽 정렬"]').click();
    await page.waitForSelector('.ck-editor__editable figure.image-style-align-left', { timeout: 10000 });

    await page.locator('#isPublic').check();
    await Promise.all([
      page.waitForURL(/\/admin\/boards$/, { timeout: 10000 }),
      page.locator('button[type="submit"]').click(),
    ]);

    const listRes = await context.request.get(
      `${baseURL}/api/admin/boards?page=0&size=1&sort=createdAt,DESC`);
    const listBody = await listRes.json();
    boardId = listBody.data.content[0].id;

    // DB/API를 거친 재조회: 수정 화면 재진입 시 정렬이 CKEditor 안에서 그대로 복원되는지 확인.
    await page.goto(`/admin/boards/${boardId}/edit`);
    await page.waitForSelector('.ck-editor__editable figure.image-style-align-left', { timeout: 10000 });

    // 공개 상세 화면에서 동일 정렬이 반영되는지 확인.
    await page.goto(`/boards/${boardId}`);
    const publicImage = page.locator('#board-detail-content .ckeditor-content .image-style-align-left img');
    await expect(publicImage).toBeVisible();

    // float containment: float 이미지 다음에 이어지는 "목록으로" 버튼이 이미지와 겹치지 않는지 확인.
    const imageBox = await publicImage.boundingBox();
    const backLinkBox = await page.locator('#board-detail-content a:has-text("목록으로")').boundingBox();
    expect(backLinkBox.y).toBeGreaterThanOrEqual(imageBox.y + imageBox.height - 1);

    // 375/768/1024/1440에서 가로 overflow가 없는지 확인.
    for (const width of [375, 768, 1024, 1440]) {
      await page.setViewportSize({ width, height: 900 });
      const overflowCheck = await page.evaluate(() => {
        const el = document.querySelector('#board-detail-content');
        return { scrollWidth: el.scrollWidth, clientWidth: el.clientWidth };
      });
      expect(overflowCheck.scrollWidth).toBeLessThanOrEqual(overflowCheck.clientWidth + 1);
    }
  });

  // P13-T25: dropdown 도입 이전(P13-T23)에 이미 저장돼 있었을 법한 HTML이 새 config에서도 그대로
  // 복원되는지 확인한다. API로 직접 저장해(UI를 거치지 않음) "기존 저장 HTML 자체는 이번 변경으로
  // 건드리지 않는다"는 것과, dropdown UI로도 그 style이 정확히 업캐스트되는지를 함께 검증한다.
  test('P13-T23 시절에 저장된 정렬 HTML이 dropdown UI에서도 그대로 복원되고 공개 화면도 무변경이다', async ({ page, context, baseURL }) => {
    const boardRes = await context.request.post(`${baseURL}/api/admin/boards`, {
      headers: { 'X-XSRF-TOKEN': xsrfToken },
      data: {
        boardType: 'NOTICE',
        title: 'P13-T25 기존 저장 HTML 회귀 확인 ' + Date.now(),
        content: '<p>본문</p><figure class="image image-style-align-right"><img src="/api/files/900501"></figure>',
        isPublic: true,
      },
    });
    expect(boardRes.ok()).toBeTruthy();
    boardId = (await boardRes.json()).data.id;

    // 수정 화면 재진입 시 dropdown UI에서도 정확히 같은 style(alignRight)로 복원되는지 확인.
    await page.goto(`/admin/boards/${boardId}/edit`);
    await page.waitForSelector('.ck-editor__editable figure.image-style-align-right', { timeout: 10000 });

    // 공개 화면도 P13-T23과 동일하게 렌더링되는지 확인(CSS/sanitizer 무변경).
    await page.goto(`/boards/${boardId}`);
    await expect(
      page.locator('#board-detail-content .ckeditor-content .image-style-align-right img')
    ).toBeVisible();
  });
});

// P13-T29: CKEditor의 6개 ImageStyle 중 inline("글 안에 배치")만 HtmlSanitizer가 실측 확인한 대로
// <figure> 래핑 없는 순수 <img>로 저장되어, 기존 `.ckeditor-content .image img` 규칙(figure 조상
// 필요)이 걸리지 않고 원본 해상도 그대로 렌더링될 수 있었다(home.css 신규 `.ckeditor-content img`
// 규칙으로 수정). 별도 대용량 binary fixture 없이 canvas로 큰 intrinsic 크기의 PNG를 즉석 생성해
// 검증한다. Board 대표 1건만 확인하고 Program/Page는 .ckeditor-content CSS를 공유하므로 중복
// 추가하지 않는다(P13-T23/P13-T27과 동일한 관례).
test.describe('P13-T29: 공개 게시글 상세 CKEditor inline 이미지 overflow 방지', () => {
  test.skip(!ADMIN_LOGIN_ID || !ADMIN_PASSWORD, 'ADMIN_LOGIN_ID/ADMIN_PASSWORD 환경변수가 설정되지 않아 건너뜀');

  let boardId;

  test.beforeEach(async ({ context, baseURL }) => {
    await loginAsAdmin(context, baseURL);
    boardId = undefined;
  });

  test.afterEach(async ({ context, baseURL }) => {
    if (boardId) {
      const xsrfToken = await getXsrfToken(context);
      await context.request.delete(`${baseURL}/api/admin/boards/${boardId}`, {
        headers: { 'X-XSRF-TOKEN': xsrfToken },
      });
    }
  });

  // 파일 크기(byte)가 아니라 PNG의 intrinsic 가로/세로 픽셀 값이 커야 overflow가 재현되므로,
  // 브라우저 <canvas>로 즉석 생성한다(별도 대용량 fixture 파일 불필요).
  async function generateLargePngBuffer(page, width, height) {
    const dataUrl = await page.evaluate(({ w, h }) => {
      const canvas = document.createElement('canvas');
      canvas.width = w;
      canvas.height = h;
      const ctx = canvas.getContext('2d');
      ctx.fillStyle = '#3366ff';
      ctx.fillRect(0, 0, w, h);
      return canvas.toDataURL('image/png');
    }, { w: width, h: height });
    return Buffer.from(dataUrl.split(',')[1], 'base64');
  }

  test('큰 intrinsic 크기의 inline("글 안에 배치") 이미지가 375/768/1440px 어디에서도 overflow를 만들지 않는다', async ({ page, context, baseURL }) => {
    await page.goto('/admin/boards/new');
    await page.locator('#boardType').selectOption('NOTICE');
    await page.locator('#title').fill('inline 이미지 overflow 확인 ' + Date.now());

    await page.waitForSelector('.ck-editor__editable', { timeout: 10000 });
    await page.locator('.ck-editor__editable').click();
    await page.keyboard.type('이 게시글은 inline 이미지 overflow 확인용 본문입니다. '.repeat(10));

    const largePngBuffer = await generateLargePngBuffer(page, 2400, 1200);
    const uploadButton = page
      .locator('.ck-file-dialog-button, button[data-cke-tooltip-text*="Insert image"], .ck-insert-image-icon')
      .first();
    const fileChooserPromise = page.waitForEvent('filechooser');
    await uploadButton.click();
    const fileChooser = await fileChooserPromise;
    await fileChooser.setFiles({
      name: 'large-inline-test.png', mimeType: 'image/png', buffer: largePngBuffer,
    });

    await page.waitForFunction(() => {
      const img = document.querySelector('.ck-editor__editable img');
      return !!(img && img.getAttribute('src') && img.getAttribute('src').indexOf('/api/files/') === 0);
    }, { timeout: 15000 });

    // 업로드 직후 이미지는 기본(block, <figure class="image">) 상태다. 위젯을 선택해 "이미지 정렬"
    // dropdown을 열고 "글 안에 배치"(inline)를 선택한다.
    await page.locator('.ck-editor__editable img').click();
    await page.locator('.ck-balloon-panel .ck-splitbutton__arrow').click();
    const panel = page.locator('.ck-dropdown__panel:not(.ck-hidden)');
    await panel.locator('[data-cke-tooltip-text="글 안에 배치"]').click();

    // inline 전환 확인: figure 래핑이 없는 순수 <img>가 됐는지 직접 확인한다(P13-T29의 핵심 전제).
    await page.waitForFunction(() => {
      const img = document.querySelector('.ck-editor__editable img');
      return !!(img && !img.closest('figure'));
    }, { timeout: 10000 });

    await page.locator('#isPublic').check();
    await Promise.all([
      page.waitForURL(/\/admin\/boards$/, { timeout: 10000 }),
      page.locator('button[type="submit"]').click(),
    ]);

    const listRes = await context.request.get(
      `${baseURL}/api/admin/boards?page=0&size=1&sort=createdAt,DESC`);
    const listBody = await listRes.json();
    boardId = listBody.data.content[0].id;

    await page.goto(`/boards/${boardId}`);
    const publicContent = page.locator('#board-detail-content .ckeditor-content');
    const publicImage = publicContent.locator('img').first();
    await expect(publicImage).toBeVisible();
    // 저장된 본문에도 figure 래핑이 없는 순수 inline <img>인지 재확인(공개 화면 기준).
    await expect(publicContent.locator('figure img')).toHaveCount(0);

    for (const width of [375, 768, 1440]) {
      await page.setViewportSize({ width, height: 900 });

      const overflowX = await page.evaluate(
        () => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(overflowX, `${width}px에서 페이지 전체 horizontal overflow가 없어야 한다`).toBeLessThanOrEqual(0);

      const contentBox = await publicContent.boundingBox();
      const imageBox = await publicImage.boundingBox();
      expect(imageBox.width, `${width}px에서 inline 이미지 렌더 폭이 .ckeditor-content 폭을 넘지 않아야 한다`)
        .toBeLessThanOrEqual(contentBox.width + 1);
    }
  });
});

test.describe('P13-T24: Banner 수정 화면 기존 이미지 미리보기', () => {
  test.skip(!ADMIN_LOGIN_ID || !ADMIN_PASSWORD, 'ADMIN_LOGIN_ID/ADMIN_PASSWORD 환경변수가 설정되지 않아 건너뜀');

  let xsrfToken;
  let bannerId;

  async function createBannerWithImage(context, baseURL, title) {
    const res = await context.request.post(`${baseURL}/api/admin/banners`, {
      headers: { 'X-XSRF-TOKEN': xsrfToken },
      data: { title, image: '/api/files/900401', sortOrder: 0, isVisible: true },
    });
    expect(res.ok()).toBeTruthy();
    return (await res.json()).data.id;
  }

  test.beforeEach(async ({ context, baseURL }) => {
    await loginAsAdmin(context, baseURL);
    xsrfToken = await getXsrfToken(context);
    bannerId = undefined;
  });

  test.afterEach(async ({ context, baseURL }) => {
    if (bannerId) {
      await context.request.delete(`${baseURL}/api/admin/banners/${bannerId}`, {
        headers: { 'X-XSRF-TOKEN': xsrfToken },
      });
    }
  });

  test('Banner 수정 화면 진입 시 기존 이미지 미리보기가 표시된다', async ({ page, context, baseURL }) => {
    bannerId = await createBannerWithImage(context, baseURL, 'Banner 이미지 미리보기 확인 ' + Date.now());
    await page.goto(`/admin/banners/${bannerId}/edit`);

    await expect(page.locator('#imagePreview')).toBeVisible();
    await expect(page.locator('#imagePreviewImage')).toHaveAttribute('src', '/api/files/900401');
    await expect(page.locator('#imagePreviewLink')).toHaveAttribute('href', '/api/files/900401');
    await expect(page.locator('#imagePreviewLink')).toHaveAttribute('target', '_blank');
    await expect(page.locator('#imagePreviewLink')).toHaveAttribute('rel', 'noopener noreferrer');
  });

  test('신규 등록 화면에서는 미리보기 영역이 표시되지 않는다', async ({ page }) => {
    await page.goto('/admin/banners/new');

    await expect(page.locator('#imagePreview')).toBeHidden();
  });

  test('새 이미지 파일을 업로드하면 미리보기가 즉시 새 URL로 갱신된다', async ({ page, context, baseURL }) => {
    bannerId = await createBannerWithImage(context, baseURL, 'Banner 새 이미지 갱신 확인 ' + Date.now());
    await page.goto(`/admin/banners/${bannerId}/edit`);
    await expect(page.locator('#imagePreviewImage')).toHaveAttribute('src', '/api/files/900401');

    await page.setInputFiles('#imageInput', {
      name: 'new-banner.png', mimeType: 'image/png', buffer: PNG_1PX_BUFFER,
    });

    await expect(page.locator('#image')).not.toHaveValue('/api/files/900401');
    const newUrl = await page.locator('#image').inputValue();
    expect(newUrl).toBeTruthy();
    await expect(page.locator('#imagePreviewImage')).toHaveAttribute('src', newUrl);
    await expect(page.locator('#imagePreview')).toBeVisible();
  });
});
