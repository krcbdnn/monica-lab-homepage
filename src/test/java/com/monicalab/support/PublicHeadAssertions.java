package com.monicalab.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.jsoup.nodes.Document;

// P15-T7A: 공개 공통 layout(home/layout/default.html) <head>의 SEO 계약. 공개 View 테스트들이 같은 검증을 공유한다.
// - meta description/og:description은 승인된 공통 문구, og:title은 최종 <title>과 동일
// - canonical은 Phase 15 범위 밖이라 없어야 한다
// P15-T7B: favicon/apple-touch-icon link, og:url(운영 도메인 + 요청 path, query 제외), og:image(+width/height/alt)
public final class PublicHeadAssertions {

    public static final String SITE_NAME = "모니카영어교육연구소";
    public static final String SITE_DESCRIPTION =
            "모니카영어교육연구소 홈페이지입니다. 연구소 소개와 교육 프로그램 안내, 공지사항·자료와 강의 후기를 확인할 수 있습니다.";
    public static final String SITE_URL = "https://www.monicaenglish.com";
    public static final String OG_IMAGE_URL = SITE_URL + "/images/og-default.png";
    public static final String OG_IMAGE_ALT = "모니카영어교육연구소 로고";

    private PublicHeadAssertions() {
    }

    // expectedPath: 요청 path(query string 제외). og:url = SITE_URL + expectedPath
    public static void assertPublicHead(Document document, String expectedTitle, String expectedPath) {
        assertThat(document.title()).isEqualTo(expectedTitle);
        assertThat(document.select("head > title")).hasSize(1);

        assertThat(document.select("head > meta[name=description]")).hasSize(1);
        assertThat(document.select("head > meta[name=description]").attr("content")).isEqualTo(SITE_DESCRIPTION);

        assertThat(ogContent(document, "og:title")).isEqualTo(expectedTitle);
        assertThat(ogContent(document, "og:description")).isEqualTo(SITE_DESCRIPTION);
        assertThat(ogContent(document, "og:type")).isEqualTo("website");
        assertThat(ogContent(document, "og:site_name")).isEqualTo(SITE_NAME);
        assertThat(ogContent(document, "og:locale")).isEqualTo("ko_KR");

        assertThat(ogContent(document, "og:url")).isEqualTo(SITE_URL + expectedPath);
        assertThat(ogContent(document, "og:image")).isEqualTo(OG_IMAGE_URL);
        assertThat(ogContent(document, "og:image:width")).isEqualTo("1200");
        assertThat(ogContent(document, "og:image:height")).isEqualTo("630");
        assertThat(ogContent(document, "og:image:alt")).isEqualTo(OG_IMAGE_ALT);

        assertThat(document.select("head > link[rel=icon]")).hasSize(1);
        assertThat(document.select("head > link[rel=icon]").attr("href")).isEqualTo("/favicon.ico");
        assertThat(document.select("head > link[rel=icon]").attr("sizes")).isEqualTo("any");
        assertThat(document.select("head > link[rel=apple-touch-icon]")).hasSize(1);
        assertThat(document.select("head > link[rel=apple-touch-icon]").attr("href")).isEqualTo("/apple-touch-icon.png");

        assertThat(document.select("link[rel=canonical]")).isEmpty();
        // 작성 주석이 응답 HTML에 새지 않는다(parser-level 주석 확인). og:url에는 query string이 없다.
        assertThat(document.outerHtml()).doesNotContain("P15-T7A", "P15-T7B", "<!--/*");
        assertThat(ogContent(document, "og:url")).doesNotContain("?");
    }

    // 공개 layout을 쓰지 않는 화면(관리자/오류 페이지)에는 공개 OG/favicon link가 들어가지 않는다.
    public static void assertNoPublicBrandMeta(Document document) {
        assertThat(document.select("meta[property^=og:]")).isEmpty();
        assertThat(document.select("link[rel=apple-touch-icon]")).isEmpty();
        assertThat(document.outerHtml()).doesNotContain(OG_IMAGE_URL);
    }

    private static String ogContent(Document document, String property) {
        assertThat(document.select("head > meta[property=\"" + property + "\"]")).as(property).hasSize(1);
        return document.select("head > meta[property=\"" + property + "\"]").attr("content");
    }
}
