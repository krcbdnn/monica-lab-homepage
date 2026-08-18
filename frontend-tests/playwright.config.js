// @ts-check
const { defineConfig } = require('@playwright/test');

// P11-T0: 설치/실행 환경만 구성하는 최소 골격.
// 실제 테스트 스펙(뷰포트별 반응형 검증 등)은 P11-T1에서 추가한다.
module.exports = defineConfig({
  testDir: __dirname,
});
