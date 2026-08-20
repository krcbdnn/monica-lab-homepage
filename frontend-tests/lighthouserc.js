// P11-T2: 관리자 UI 접근성 검증.
// 앱은 실행 전에 별도로 기동되어 있어야 한다(server 자동 기동 없음, Playwright와 동일한 원칙).
// 인증이 필요한 관리자 화면은 대상에서 제외하고, 로그인 없이 접근 가능한 /admin/login만 측정한다.

const baseUrl = process.env.LHCI_BASE_URL || 'http://localhost:8080';

module.exports = {
  ci: {
    collect: {
      url: [`${baseUrl}/admin/login`],
      numberOfRuns: 1,
    },
    assert: {
      assertions: {
        'categories:accessibility': ['error', { minScore: 0.90 }],
      },
    },
  },
};
