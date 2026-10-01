// @ts-check
const { defineConfig } = require('@playwright/test');

// P11-T1: 반응형 검증을 위해 baseURL을 구성한다.
// 앱은 테스트 실행 전에 별도로 기동되어 있어야 한다(server 자동 기동 없음).
module.exports = defineConfig({
  testDir: __dirname,
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:8080',
    // P15-T3: 로컬 Docker stack의 HTTPS(Nginx :443)는 자체서명 인증서를 쓰므로 인증서 오류를 허용한다.
    // 이 설정을 받지 않는 직접 생성 context(browser.newContext/request.newContext)에는 각자 같은 옵션을 준다.
    ignoreHTTPSErrors: true,
  },
});
