// @ts-check
const { defineConfig } = require('@playwright/test');

// P11-T1: 반응형 검증을 위해 baseURL을 구성한다.
// 앱은 테스트 실행 전에 별도로 기동되어 있어야 한다(server 자동 기동 없음).
module.exports = defineConfig({
  testDir: __dirname,
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:8080',
  },
});
