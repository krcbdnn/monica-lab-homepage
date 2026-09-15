package com.monicalab.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

class HtmlSanitizerTest {

    @Test
    void removesScriptTagAndContent() {
        String result = HtmlSanitizer.sanitize("<script>alert(1)</script>");

        assertThat(result).doesNotContainIgnoringCase("<script")
                .doesNotContain("alert(1)");
    }

    @Test
    void removesEventHandlerAttribute() {
        String result = HtmlSanitizer.sanitize("<img src=x onerror=alert(1)>");

        assertThat(result).doesNotContainIgnoringCase("onerror")
                .doesNotContain("alert(1)")
                .containsIgnoringCase("<img");
    }

    @Test
    void removesJavascriptSchemeLink() {
        String result = HtmlSanitizer.sanitize("<a href=\"javascript:alert(1)\">click</a>");

        assertThat(result).doesNotContain("javascript:")
                .doesNotContain("alert(1)");
    }

    @Test
    void preservesAllowedTags() {
        String html = "<p>text</p><table><tr><td>cell</td></tr></table>"
                + "<h1>title</h1><strong>bold</strong><em>italic</em><u>underline</u><br>"
                + "<a href=\"https://example.com\">link</a>"
                + "<img src=\"https://example.com/image.png\">";

        String result = HtmlSanitizer.sanitize(html);

        assertThat(result).containsIgnoringCase("<p>")
                .containsIgnoringCase("<table>")
                .containsIgnoringCase("<tr>")
                .containsIgnoringCase("<td>")
                .containsIgnoringCase("<h1>")
                .containsIgnoringCase("<strong>")
                .containsIgnoringCase("<em>")
                .containsIgnoringCase("<u>")
                .containsIgnoringCase("<br")
                .contains("href=\"https://example.com\"")
                .contains("src=\"https://example.com/image.png\"");
    }

    @Test
    void removesIframeTag() {
        String result = HtmlSanitizer.sanitize("<iframe src=\"https://evil.example\"></iframe>");

        assertThat(result).doesNotContainIgnoringCase("<iframe");
    }

    @Test
    void nullInputReturnsNull() {
        assertThat(HtmlSanitizer.sanitize(null)).isNull();
    }

    @Test
    void preservesInternalRelativeFileImageSrc() {
        String result = HtmlSanitizer.sanitize("<p>A</p><img src=\"/api/files/123\">");

        assertThat(result).contains("src=\"/api/files/123\"");
    }

    @Test
    void removesDataSchemeImage() {
        String result = HtmlSanitizer.sanitize("<img src=\"data:image/png;base64,abcd\">");

        assertThat(result).doesNotContain("data:").doesNotContain("src=");
    }

    @Test
    void removesProtocolRelativeImageSrc() {
        String result = HtmlSanitizer.sanitize("<img src=\"//evil.com/x.png\">");

        assertThat(result).doesNotContain("evil.com").doesNotContain("src=");
    }

    @Test
    void removesArbitraryRelativePathImageSrc() {
        String result = HtmlSanitizer.sanitize("<img src=\"/admin/dashboard\">");

        assertThat(result).doesNotContain("/admin/dashboard").doesNotContain("src=");
    }

    @Test
    void removesNonNumericFileIdImageSrc() {
        String result = HtmlSanitizer.sanitize("<img src=\"/api/files/abc\">");

        assertThat(result).doesNotContain("/api/files/abc").doesNotContain("src=");
    }

    @Test
    void removesTraversalShapedImageSrc() {
        String result = HtmlSanitizer.sanitize("<img src=\"/api/files/123/../../etc/passwd\">");

        assertThat(result).doesNotContain("etc/passwd").doesNotContain("src=");
    }

    @Test
    void removesEventHandlerEvenWithAllowedRelativeSrc() {
        String result = HtmlSanitizer.sanitize("<img src=\"/api/files/1\" onerror=\"alert(1)\">");

        assertThat(result).contains("src=\"/api/files/1\"")
                .doesNotContainIgnoringCase("onerror")
                .doesNotContain("alert(1)");
    }

    @Test
    void preservesRelativeInternalLinkHref() {
        String result = HtmlSanitizer.sanitize("<a href=\"/boards/1\">내부 게시글</a>");

        assertThat(result).contains("href=\"/boards/1\"");
    }

    @Test
    void preservesDefaultFigureImageClass() {
        String result = HtmlSanitizer.sanitize("<figure class=\"image\"><img src=\"/api/files/1\"></figure>");

        assertThat(result).contains("<figure class=\"image\">")
                .contains("<img src=\"/api/files/1\">");
    }

    @Test
    void preservesImageStyleSideClass() {
        String result = HtmlSanitizer.sanitize(
                "<figure class=\"image image-style-side\"><img src=\"/api/files/1\"></figure>");

        assertThat(result).contains("class=\"image image-style-side\"");
    }

    @Test
    void preservesImageStyleAlignLeftRightCenterClasses() {
        assertThat(HtmlSanitizer.sanitize(
                "<figure class=\"image image-style-align-left\"><img src=\"/api/files/1\"></figure>"))
                .contains("class=\"image image-style-align-left\"");
        assertThat(HtmlSanitizer.sanitize(
                "<figure class=\"image image-style-align-right\"><img src=\"/api/files/1\"></figure>"))
                .contains("class=\"image image-style-align-right\"");
        assertThat(HtmlSanitizer.sanitize(
                "<figure class=\"image image-style-align-center\"><img src=\"/api/files/1\"></figure>"))
                .contains("class=\"image image-style-align-center\"");
    }

    @Test
    void removesDisallowedClassTokenButKeepsAllowedTokensInSameAttribute() {
        String result = HtmlSanitizer.sanitize(
                "<figure class=\"image evil-class\"><img src=\"/api/files/1\"></figure>");

        assertThat(result).contains("class=\"image\"")
                .doesNotContain("evil-class");
    }

    @Test
    void removesClassAttributeEntirelyWhenNoTokenIsAllowed() {
        String result = HtmlSanitizer.sanitize(
                "<figure class=\"evil-class another-evil\"><img src=\"/api/files/1\"></figure>");

        assertThat(result).contains("<figure>")
                .doesNotContain("class=")
                .doesNotContain("evil-class");
    }

    @Test
    void removesEventHandlerAttributeOnFigureWhileKeepingAllowedClass() {
        String result = HtmlSanitizer.sanitize(
                "<figure class=\"image\" onmouseover=\"alert(1)\"><img src=\"/api/files/1\"></figure>");

        assertThat(result).contains("class=\"image\"")
                .doesNotContainIgnoringCase("onmouseover")
                .doesNotContain("alert(1)");
    }

    @Test
    void removesScriptTagInsideFigure() {
        String result = HtmlSanitizer.sanitize(
                "<figure class=\"image\"><img src=\"/api/files/1\"><script>alert(1)</script></figure>");

        assertThat(result).doesNotContainIgnoringCase("<script")
                .doesNotContain("alert(1)")
                .contains("class=\"image\"");
    }

    @Test
    void preservesBareInlineImageWithoutFigureWrapper() {
        String result = HtmlSanitizer.sanitize("<p>before <img src=\"/api/files/1\"> after</p>");

        assertThat(result).isEqualTo("<p>before <img src=\"/api/files/1\"> after</p>");
    }

    @Test
    void figureWithoutClassAttributeIsUnaffected() {
        String result = HtmlSanitizer.sanitize("<figure><img src=\"/api/files/1\"></figure>");

        assertThat(result).contains("<figure>")
                .doesNotContain("class=");
    }

    // P13-T39: CKEditor의 ImageTextAlternative("대체 텍스트") 툴바 버튼이 만드는 img[alt]를
    // 저장 시 보존한다. P13-T23에서 발견사항으로만 기록되고 미해결이던 항목을 해소한다.
    @Test
    void preservesImgAltAttribute() {
        String result = HtmlSanitizer.sanitize("<img src=\"/api/files/1\" alt=\"Sample image\">");

        assertThat(result).contains("src=\"/api/files/1\"")
                .contains("alt=\"Sample image\"");
    }

    @Test
    void preservesKoreanAltText() {
        String result = HtmlSanitizer.sanitize("<img src=\"/api/files/1\" alt=\"수업 안내 이미지\">");

        assertThat(result).contains("alt=\"수업 안내 이미지\"");
    }

    // 빈 alt=""는 "장식용 이미지"라는 의미를 갖는 유효한 접근성 패턴이다(스크린리더가 건너뜀).
    // CKEditor도 실제로 이 값을 그대로 유지한다(헤드리스로 확인) - 속성 자체를 제거하거나 임의
    // 텍스트로 채우지 않고 빈 값 그대로 보존한다.
    @Test
    void preservesEmptyAltAttributeAsIs() {
        String result = HtmlSanitizer.sanitize("<img src=\"/api/files/1\" alt=\"\">");

        assertThat(result).contains("alt=\"\"");
    }

    // jsoup은 attribute 값을 항상 HTML-escape해서 직렬화하므로, alt 안에 "&", "<", ">" 같은 문자가
    // 있어도 attribute 경계를 벗어나지 않고 하나의 값으로만 유지된다.
    @Test
    void preservesAltWithSpecialCharactersWithoutBreakingOutOfAttribute() {
        String result = HtmlSanitizer.sanitize("<img src=\"/api/files/1\" alt=\"A & B < C > D\">");

        Document doc = Jsoup.parseBodyFragment(result);
        Element img = doc.selectFirst("img");

        assertThat(img).isNotNull();
        assertThat(img.attr("alt")).isEqualTo("A & B < C > D");
        assertThat(img.attributes().asList()).hasSize(2); // src, alt만 존재
    }

    // alt는 실행 가능한 HTML이 아니라 단순 문자열 attribute여야 한다. 원본 입력의 alt 값 자체가
    // "<script>...</script>"이어도 실제 <script> 태그로 되살아나지 않고 텍스트로만 보존된다.
    // 참고: HTML5 스펙상 attribute 값 안의 "<"/">"는 이스케이프가 필수가 아니며(따옴표로 감싸인
    // attribute 값 상태에서는 "<"가 새 태그 시작으로 해석되지 않는다), jsoup도 이 규칙을 그대로
    // 따르므로 sanitize 결과 문자열에 "<script>"가 리터럴로 남아 있는 것 자체는 정상이다 - 중요한
    // 것은 그것이 실제 DOM에서 <script> "요소"가 되지 않고 img의 alt 값 안에만 갇혀 있다는 것이다.
    @Test
    void scriptLikeAltContentStaysAsInertTextNotAsExecutableTag() {
        String result = HtmlSanitizer.sanitize(
                "<img src=\"/api/files/1\" alt=\"<script>alert(1)</script>\">");

        Document doc = Jsoup.parseBodyFragment(result);

        assertThat(doc.select("script")).isEmpty();
        Element img = doc.selectFirst("img");
        assertThat(img).isNotNull();
        assertThat(img.attr("alt")).contains("script").contains("alert(1)");
        assertThat(doc.body().children()).hasSize(1); // img 하나뿐, 별도 script 요소로 쪼개지지 않음
    }

    // alt 값이 attribute 경계를 탈출해 새 attribute(onerror 등)를 주입하려는 것처럼 "보이는" 입력도,
    // jsoup이 파싱 시점에 이미 하나의 attribute 값으로 확정하므로 실제로는 onerror가 별도 attribute로
    // 살아남지 않는다(작은따옴표로 감싸 그 안의 큰따옴표를 리터럴 텍스트로 만든 공격 형태).
    @Test
    void maliciousLookingAltValueDoesNotBecomeANewLiveAttribute() {
        String result = HtmlSanitizer.sanitize(
                "<img src=\"/api/files/1\" alt='\" onerror=\"alert(1)'>");

        Document doc = Jsoup.parseBodyFragment(result);
        Element img = doc.selectFirst("img");

        assertThat(img).isNotNull();
        assertThat(img.attributes().hasKeyIgnoreCase("onerror")).isFalse();
        assertThat(img.attributes().asList()).hasSize(2); // src, alt만 존재
    }

    // P13-T39: CKEditor의 ImageCaption("캡션 넣기/빼기") 툴바 버튼이 만드는 <figcaption>을 태그와
    // 텍스트 모두 보존한다. 기존에는 figcaption 태그만 사라지고 내부 텍스트가 <figure> 바로 아래
    // 벌거벗은 텍스트로 leak됐다(P13-T23에서 발견사항으로만 기록).
    @Test
    void preservesFigcaptionTagAndText() {
        String result = HtmlSanitizer.sanitize(
                "<figure class=\"image\"><img src=\"/api/files/1\"><figcaption>이미지 캡션</figcaption></figure>");

        assertThat(result).contains("<figcaption>이미지 캡션</figcaption>")
                .contains("class=\"image\"");
    }

    @Test
    void preservesKoreanFigcaptionAlongsideAlt() {
        String result = HtmlSanitizer.sanitize(
                "<figure class=\"image\"><img src=\"/api/files/1\" alt=\"대체텍스트\">"
                        + "<figcaption>캡션 텍스트</figcaption></figure>");

        assertThat(result).contains("alt=\"대체텍스트\"")
                .contains("<figcaption>캡션 텍스트</figcaption>");
    }

    // figcaption에는 어떤 attribute도 허용하지 않는다(style/class/id/이벤트 핸들러 전부 제거).
    @Test
    void removesAllAttributesFromFigcaption() {
        String result = HtmlSanitizer.sanitize(
                "<figure class=\"image\"><img src=\"/api/files/1\">"
                        + "<figcaption class=\"evil\" style=\"color:red\" onclick=\"alert(1)\">caption</figcaption></figure>");

        assertThat(result).contains("<figcaption>caption</figcaption>")
                .doesNotContain("class=\"evil\"")
                .doesNotContain("style=")
                .doesNotContainIgnoringCase("onclick");
    }

    // figcaption 안에 <script>가 있어도 실행 가능한 형태로 보존되지 않는다(기존 script 제거 정책과
    // 동일하게 동작함을 figcaption이 새로 허용된 이후에도 재확인).
    @Test
    void removesScriptTagInsideFigcaptionWhileKeepingCaptionText() {
        String result = HtmlSanitizer.sanitize(
                "<figure class=\"image\"><img src=\"/api/files/1\">"
                        + "<figcaption>caption<script>alert(1)</script></figcaption></figure>");

        assertThat(result).doesNotContainIgnoringCase("<script")
                .doesNotContain("alert(1)")
                .contains("<figcaption>caption</figcaption>");
    }
}
