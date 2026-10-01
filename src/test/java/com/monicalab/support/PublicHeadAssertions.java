package com.monicalab.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.jsoup.nodes.Document;

// P15-T7A: 공개 공통 layout(home/layout/default.html) <head>의 SEO 계약. 공개 View 테스트들이 같은 검증을 공유한다.
// - meta description/og:description은 승인된 공통 문구, og:title은 최종 <title>과 동일
// - og:image/og:url/canonical은 최종 asset·도메인이 필요한 P15-T7B 범위라 없어야 한다
public final class PublicHeadAssertions {

    public static final String SITE_NAME = "모니카영어교육연구소";
    public static final String SITE_DESCRIPTION =
            "모니카영어교육연구소 홈페이지입니다. 연구소 소개와 교육 프로그램 안내, 공지사항·자료와 강의 후기를 확인할 수 있습니다.";

    private PublicHeadAssertions() {
    }

    public static void assertPublicHead(Document document, String expectedTitle) {
        assertThat(document.title()).isEqualTo(expectedTitle);
        assertThat(document.select("head > title")).hasSize(1);

        assertThat(document.select("head > meta[name=description]")).hasSize(1);
        assertThat(document.select("head > meta[name=description]").attr("content")).isEqualTo(SITE_DESCRIPTION);

        assertThat(ogContent(document, "og:title")).isEqualTo(expectedTitle);
        assertThat(ogContent(document, "og:description")).isEqualTo(SITE_DESCRIPTION);
        assertThat(ogContent(document, "og:type")).isEqualTo("website");
        assertThat(ogContent(document, "og:site_name")).isEqualTo(SITE_NAME);
        assertThat(ogContent(document, "og:locale")).isEqualTo("ko_KR");

        assertThat(document.select("meta[property=\"og:image\"]")).isEmpty();
        assertThat(document.select("meta[property=\"og:url\"]")).isEmpty();
        assertThat(document.select("link[rel=canonical]")).isEmpty();
        // 작성 주석까지 포함한 응답 HTML 전체에 T7B 범위 메타 이름이나 Task 주석이 새지 않는다(parser-level 주석 확인).
        assertThat(document.outerHtml()).doesNotContain("og:image", "og:url", "P15-T7A");
    }

    private static String ogContent(Document document, String property) {
        assertThat(document.select("head > meta[property=\"" + property + "\"]")).as(property).hasSize(1);
        return document.select("head > meta[property=\"" + property + "\"]").attr("content");
    }
}
