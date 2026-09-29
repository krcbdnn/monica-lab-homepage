package com.monicalab.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    // ===== P13-T40: 이미지 크기 조절(25%/50%/75%/원본 preset) sanitizer 화이트리스트 =====

    @Test
    void preservesValid25PercentResizeWidthWithImageResizedClass() {
        String result = HtmlSanitizer.sanitize(
                "<figure class=\"image image_resized\" style=\"width:25%;\"><img src=\"/api/files/1\"></figure>");

        assertThat(result).contains("class=\"image image_resized\"")
                .contains("style=\"width:25%;\"");
    }

    @Test
    void preservesValid50And75PercentResizeWidth() {
        assertThat(HtmlSanitizer.sanitize(
                "<figure class=\"image image_resized\" style=\"width: 50%;\"><img src=\"/api/files/1\"></figure>"))
                .contains("style=\"width:50%;\"");
        assertThat(HtmlSanitizer.sanitize(
                "<figure class=\"image image_resized\" style=\"width:75%\"><img src=\"/api/files/1\"></figure>"))
                .contains("style=\"width:75%;\"");
    }

    // CKEditor가 실제로 생성 가능한 흔한 공백/세미콜론 변형만 허용한다는 것을 명시적으로 확인한다.
    @Test
    void acceptsCommonWhitespaceAndSemicolonVariants() {
        String[] validInputs = {
                "width:25%", "width: 25%;", "width:50%", "width: 50%;", "width:75%", "width: 75%;"
        };
        for (String style : validInputs) {
            String result = HtmlSanitizer.sanitize(
                    "<figure class=\"image\" style=\"" + style + "\"><img src=\"/api/files/1\"></figure>");
            assertThat(result).as("input style=%s", style).contains("style=\"width:");
        }
    }

    @Test
    void figureWithoutStyleHasNoWidthOrResizedClassAdded() {
        String result = HtmlSanitizer.sanitize("<figure class=\"image\"><img src=\"/api/files/1\"></figure>");

        assertThat(result).contains("class=\"image\"")
                .doesNotContain("style=")
                .doesNotContain("image_resized");
    }

    // 원본(리사이즈 취소) 상태: 재편집 후 원본으로 되돌리면 style/class 둘 다 남지 않아야 한다.
    @Test
    void resizeCancelledToOriginalLeavesNoStyleAndNoResizedClass() {
        String result = HtmlSanitizer.sanitize(
                "<figure class=\"image\"><img src=\"/api/files/1\"></figure>");

        assertThat(result).isEqualTo("<figure class=\"image\"><img src=\"/api/files/1\"></figure>");
    }

    // 공격자가 style 없이 image_resized class만 주입해도(가짜 "리사이즈됨" 표시) 여기서 제거된다 -
    // style과 class는 항상 함께 있거나 함께 없어야 한다는 불변조건.
    @Test
    void removesOrphanImageResizedClassWhenNoValidWidthStylePresent() {
        String result = HtmlSanitizer.sanitize(
                "<figure class=\"image image_resized\"><img src=\"/api/files/1\"></figure>");

        assertThat(result).contains("class=\"image\"")
                .doesNotContain("image_resized")
                .doesNotContain("style=");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "width:100%", "width:0%", "width:1%", "width:24%", "width:26%", "width:49%",
            "width:51%", "width:74%", "width:76%", "width:101%", "width:-1%", "width:50.5%",
            "width:calc(100% - 10px)", "width:var(--w)", "width:expression(alert(1))",
            "background:url(x)", "width:50%;background:red", "background:red;width:50%",
            "width:50%;width:75%", "not-a-css-declaration", "width", "width:", "width:50"
    })
    void discardsEntireStyleWhenNotExactlyAnAllowedWidthOnlyDeclaration(String maliciousStyle) {
        String result = HtmlSanitizer.sanitize(
                "<figure class=\"image\" style=\"" + maliciousStyle + "\"><img src=\"/api/files/1\"></figure>");

        assertThat(result).as("style=%s must be fully discarded", maliciousStyle)
                .doesNotContain("style=")
                .doesNotContain("image_resized");
    }

    // 다른 element(img/p)의 style, figure 이외 element의 width style은 애초에 Safelist 단계에서
    // style 자체가 허용되지 않는 태그이므로 그대로 사라져야 한다(회귀 확인).
    @Test
    void doesNotPreserveStyleOnNonFigureElements() {
        String result = HtmlSanitizer.sanitize(
                "<p style=\"width:50%\">text</p>"
                        + "<img src=\"/api/files/1\" style=\"width:50%\">");

        assertThat(result).doesNotContain("style=");
    }

    // figure에 style attribute가 중복 선언된 비정상 HTML - jsoup 파서가 결정하는 단일 값만 남으므로
    // 그 값 기준으로 정상적으로 검증/처리된다(크래시 없음, 부분 조합으로 우회되지 않음).
    @Test
    void handlesDuplicateStyleAttributeOnFigureWithoutCrashing() {
        String result = HtmlSanitizer.sanitize(
                "<figure class=\"image\" style=\"width:25%\" style=\"width:999%\">"
                        + "<img src=\"/api/files/1\"></figure>");

        assertThat(result).doesNotContain("width:999%");
    }

    // image-style-side(정렬) + resize(50%)가 동시에 걸린 경우 둘 다 유지되어야 한다(공존, 회귀 없음).
    @Test
    void preservesImageStyleSideAlongsideResizeWidth() {
        String result = HtmlSanitizer.sanitize(
                "<figure class=\"image image_resized image-style-side\" style=\"width:50%;\">"
                        + "<img src=\"/api/files/1\"></figure>");

        assertThat(result).contains("class=\"image image_resized image-style-side\"")
                .contains("style=\"width:50%;\"");
    }

    // caption + alt + resize가 동시에 걸린 경우 전부 유지되어야 한다.
    @Test
    void preservesCaptionAndAltAlongsideResizeWidth() {
        String result = HtmlSanitizer.sanitize(
                "<figure class=\"image image_resized\" style=\"width:75%;\">"
                        + "<img src=\"/api/files/1\" alt=\"설명\"><figcaption>캡션</figcaption></figure>");

        assertThat(result).contains("style=\"width:75%;\"")
                .contains("alt=\"설명\"")
                .contains("<figcaption>캡션</figcaption>");
    }

    // 핵심 불변조건 회귀 테스트: 여러 <figure>가 존재하고 그 사이에 Safelist가 제거/벗겨낼 요소(허용
    // 안 된 <div>, <script>)가 섞여 있어도, extract 단계와 reinject 단계의 <figure> 인덱스 대응이
    // 절대 어긋나지 않는다는 것을 직접 증명한다(§2 "복잡한 index matching에 취약해지는 방식 회피" 요구
    // 사항에 대한 근거: <figure>는 항상 허용된 태그라 제거/재정렬/중복이 절대 발생하지 않기 때문에
    // 이 index 대응은 안전하다).
    @Test
    void figureIndexCorrelationSurvivesDisallowedSiblingsAndWrappers() {
        String raw = "<div class=\"not-allowed-wrapper\">"
                + "<figure class=\"image\" style=\"width:25%\"><img src=\"/api/files/1\"></figure>"
                + "</div>"
                + "<script>alert('x')</script>"
                + "<figure class=\"image\"><img src=\"/api/files/2\"></figure>"
                + "<p>중간 텍스트</p>"
                + "<figure class=\"image image_resized\" style=\"width:75%;\"><img src=\"/api/files/3\"></figure>";

        String result = HtmlSanitizer.sanitize(raw);

        Document doc = Jsoup.parseBodyFragment(result);
        Elements figures = doc.select("figure");
        assertThat(figures).hasSize(3);

        assertThat(figures.get(0).attr("style")).isEqualTo("width:25%;");
        assertThat(figures.get(0).selectFirst("img").attr("src")).isEqualTo("/api/files/1");

        assertThat(figures.get(1).hasAttr("style")).isFalse();
        assertThat(figures.get(1).selectFirst("img").attr("src")).isEqualTo("/api/files/2");

        assertThat(figures.get(2).attr("style")).isEqualTo("width:75%;");
        assertThat(figures.get(2).selectFirst("img").attr("src")).isEqualTo("/api/files/3");

        assertThat(result).doesNotContainIgnoringCase("<script").doesNotContain("not-allowed-wrapper");
    }

    // round-trip: 25%로 저장된 콘텐츠를 다시 sanitize에 통과시켜도(재편집 후 재저장을 흉내) 동일한
    // 결과를 유지해야 한다(idempotent).
    @Test
    void sanitizeIsIdempotentForAlreadyResizedContent() {
        String first = HtmlSanitizer.sanitize(
                "<figure class=\"image image_resized\" style=\"width:25%;\"><img src=\"/api/files/1\"></figure>");
        String second = HtmlSanitizer.sanitize(first);

        assertThat(second).isEqualTo(first);
    }

    // ===== P15-T4: CKEditor 41.4.2 실제 출력(목록/인용/기울임/표 머리글/셀 병합) 보존 =====
    // 기대값은 41.4.2 classic CDN build의 getData() 실측 출력(툴바/Autoformat/붙여넣기)을 기준으로 한다.

    @Test
    void preservesItalicAndExistingEm() {
        assertThat(HtmlSanitizer.sanitize("<p><i><strong>abc</strong></i></p>"))
                .isEqualTo("<p><i><strong>abc</strong></i></p>");
        assertThat(HtmlSanitizer.sanitize("<p><em>legacy</em></p>")).isEqualTo("<p><em>legacy</em></p>");
    }

    @Test
    void preservesUnorderedList() {
        assertThat(HtmlSanitizer.sanitize("<ul><li>a</li><li>b</li></ul>"))
                .isEqualTo("<ul><li>a</li><li>b</li></ul>");
    }

    @Test
    void preservesOrderedList() {
        assertThat(HtmlSanitizer.sanitize("<ol><li>a</li><li>b</li></ol>"))
                .isEqualTo("<ol><li>a</li><li>b</li></ol>");
    }

    // CKEditor indent는 하위 목록을 부모 li 안에 중첩한다(IndentBlock 없음 - 목록 중첩 전용).
    @Test
    void preservesNestedListStructure() {
        String html = "<ul><li>a<ul><li>b<ol><li>c</li></ol></li></ul></li></ul>";

        assertThat(HtmlSanitizer.sanitize(html)).isEqualTo(html);
    }

    @Test
    void preservesBlockquoteWithParagraphsAndInnerList() {
        assertThat(HtmlSanitizer.sanitize("<blockquote><p>q1</p><p>q2</p></blockquote>"))
                .isEqualTo("<blockquote><p>q1</p><p>q2</p></blockquote>");
        assertThat(HtmlSanitizer.sanitize("<blockquote><ul><li>x</li></ul><h2>h</h2></blockquote>"))
                .isEqualTo("<blockquote><ul><li>x</li></ul><h2>h</h2></blockquote>");
    }

    // 표 머리글 행(thead > th)과 머리글 열(tbody 안 행 첫 칸 th) 구조를 모두 유지한다.
    @Test
    void preservesTableHeadBodyAndRowHeaderCells() {
        String html = "<table><thead><tr><th>h1</th><th>h2</th></tr></thead>"
                + "<tbody><tr><th>r1</th><td>d1</td></tr></tbody></table>";

        assertThat(HtmlSanitizer.sanitize(html)).isEqualTo(html);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "2", "50"})
    void preservesValidColspan(String value) {
        String html = "<table><tbody><tr><td colspan=\"" + value + "\">c</td></tr></tbody></table>";

        assertThat(HtmlSanitizer.sanitize(html)).isEqualTo(html);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "2", "50"})
    void preservesValidRowspan(String value) {
        String html = "<table><tbody><tr><th rowspan=\"" + value + "\">c</th></tr></tbody></table>";

        assertThat(HtmlSanitizer.sanitize(html)).isEqualTo(html);
    }

    @Test
    void preservesColspanAndRowspanTogether() {
        String html = "<table><tbody><tr><td colspan=\"2\" rowspan=\"2\">m</td><td>x</td></tr>"
                + "<tr><td>y</td></tr></tbody></table>";

        assertThat(HtmlSanitizer.sanitize(html)).isEqualTo(html);
    }

    // CKEditor getData() 실측 형태: 표는 <figure class="table">로 감싸진다. figure의 "table" class
    // 토큰은 기존 ALLOWED_FIGURE_CLASS_TOKENS 대상이 아니라 제거되는 기존 동작(P15-T4 범위 밖)이고,
    // 표 구조(thead/tbody/th/병합 span)와 셀 안 목록/인용/기울임은 보존된다.
    @Test
    void preservesRealCkeditorTableFixtureWithMergedCellsAndRichCellContent() {
        String html = "<figure class=\"table\"><table>"
                + "<thead><tr><th>&nbsp;</th><th>&nbsp;</th><th>&nbsp;</th></tr></thead>"
                + "<tbody><tr><th>r</th><td colspan=\"2\" rowspan=\"2\">merged</td></tr>"
                + "<tr><th>r2</th></tr>"
                + "<tr><th>r3</th><td><ul><li>x</li></ul><blockquote><p>q</p></blockquote></td>"
                + "<td><p><i>i</i></p></td></tr>"
                + "</tbody></table></figure>";

        String result = HtmlSanitizer.sanitize(html);

        assertThat(result).isEqualTo(html.replace("<figure class=\"table\">", "<figure>"));
    }

    // 기존 이미지 계약(resize style/figure class/alt/figcaption)과 새 구조가 한 문서에 섞여도 서로 간섭하지 않는다.
    @Test
    void preservesNewStructuresAlongsideResizedCaptionedImage() {
        String html = "<ul><li>a</li></ul>"
                + "<figure class=\"image image-style-side image_resized\" style=\"width:50%;\">"
                + "<img src=\"/api/files/1\" alt=\"대체\"><figcaption>캡션</figcaption></figure>"
                + "<blockquote><p><i>q</i></p></blockquote>"
                + "<figure class=\"table\"><table><tbody><tr><td rowspan=\"2\">m</td><td>x</td></tr>"
                + "<tr><td>y</td></tr></tbody></table></figure>"
                + "<figure class=\"image image_resized\" style=\"width:25%;\"><img src=\"/api/files/2\"></figure>";

        String result = HtmlSanitizer.sanitize(html);

        assertThat(result).isEqualTo(html.replace("<figure class=\"table\">", "<figure>"));
        assertThat(HtmlSanitizer.sanitize(result)).isEqualTo(result);
    }

    // 정확히 ASCII 10진 정수 1~50만 허용한다. 공백/부호/선행 0/지수/전각 숫자/단위 등은 attribute만 제거하고
    // 셀(td/th)과 셀 텍스트, 같은 셀의 다른 유효 span은 그대로 유지한다.
    @ParameterizedTest
    @ValueSource(strings = {"0", "51", "-1", "1.5", "abc", "", " 2", "2 ", "02", "+2", "1e1", "９", "2;x", "50%"})
    void removesOnlyInvalidColspanAttributeAndKeepsCell(String value) {
        String result = HtmlSanitizer.sanitize(
                "<table><tbody><tr><td colspan=\"" + value + "\" rowspan=\"2\">cell</td>"
                        + "<th colspan=\"" + value + "\">head</th></tr></tbody></table>");

        assertThat(result).as("colspan=[%s]", value).isEqualTo(
                "<table><tbody><tr><td rowspan=\"2\">cell</td><th>head</th></tr></tbody></table>");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "51", "-1", "1.5", "abc", "", " 2", "2 ", "02", "+2", "1e1", "９", "2;x", "50%"})
    void removesOnlyInvalidRowspanAttributeAndKeepsCell(String value) {
        String result = HtmlSanitizer.sanitize(
                "<table><tbody><tr><td rowspan=\"" + value + "\" colspan=\"2\">cell</td>"
                        + "<th rowspan=\"" + value + "\">head</th></tr></tbody></table>");

        assertThat(result).as("rowspan=[%s]", value).isEqualTo(
                "<table><tbody><tr><td colspan=\"2\">cell</td><th>head</th></tr></tbody></table>");
    }

    // colspan/rowspan은 td/th 전용이다. 다른 태그에 붙은 동일 이름 attribute는 Safelist에서 제거된다.
    @Test
    void doesNotAllowSpanAttributesOnNonCellElements() {
        String result = HtmlSanitizer.sanitize(
                "<table colspan=\"2\"><tbody rowspan=\"2\"><tr colspan=\"2\"><td>c</td></tr></tbody></table>"
                        + "<p colspan=\"2\">p</p>");

        assertThat(result).isEqualTo("<table><tbody><tr><td>c</td></tr></tbody></table><p>p</p>");
    }

    @Test
    void removesAllAttributesFromNewStructuralTags() {
        String result = HtmlSanitizer.sanitize(
                "<ul onclick=\"alert(1)\" style=\"color:red\" class=\"x\" id=\"i\">"
                        + "<li onmouseover=\"alert(1)\" value=\"3\" class=\"y\">a</li></ul>"
                        + "<ol start=\"5\" type=\"a\" reversed><li>b</li></ol>"
                        + "<blockquote cite=\"javascript:alert(1)\" onclick=\"x\" style=\"a\"><p>q</p></blockquote>"
                        + "<p><i class=\"c\" style=\"s\" onclick=\"x\">i</i></p>"
                        + "<table><thead onclick=\"x\" style=\"a\" class=\"c\">"
                        + "<tr><th scope=\"col\" style=\"width:1px\" onclick=\"x\" id=\"h\">h</th></tr></thead>"
                        + "<tbody id=\"b\"><tr><td>d</td></tr></tbody></table>");

        assertThat(result).isEqualTo("<ul><li>a</li></ul><ol><li>b</li></ol><blockquote><p>q</p></blockquote>"
                + "<p><i>i</i></p><table><thead><tr><th>h</th></tr></thead><tbody><tr><td>d</td></tr></tbody></table>");
    }

    @Test
    void removesDangerousElementsNestedInsideNewStructures() {
        String result = HtmlSanitizer.sanitize(
                "<ul><li><script>alert(1)</script>x<img src=x onerror=alert(1)></li>"
                        + "<li><iframe src=\"https://evil.example\"></iframe>"
                        + "<iframe srcdoc=\"<script>alert(1)</script>\"></iframe>y</li></ul>"
                        + "<blockquote><svg onload=alert(1)><script>alert(1)</script></svg><math><mi>m</mi></math></blockquote>"
                        + "<ol><li><i><object data=\"x\"></object><embed src=\"x\">"
                        + "<form action=\"javascript:alert(1)\"><input autofocus onfocus=alert(1)></form>z</i></li></ol>");

        Document doc = Jsoup.parseBodyFragment(result);

        assertThat(doc.select("script, iframe, svg, math, object, embed, form, input")).isEmpty();
        assertThat(result).doesNotContain("alert(1)").doesNotContain("srcdoc").doesNotContain("onerror")
                .doesNotContain("onload").doesNotContain("onfocus");
        assertThat(doc.select("li")).hasSize(3);
        assertThat(doc.select("blockquote")).hasSize(1);
        assertThat(doc.selectFirst("img").hasAttr("src")).isFalse();
    }

    @Test
    void removesUnsafeLinkSchemesInsideListItems() {
        String result = HtmlSanitizer.sanitize("<ol><li><a href=\"javascript:alert(1)\">x</a>"
                + "<a href=\"JaVaScRiPt:alert(1)\">y</a><a href=\"data:text/html,alert(1)\">z</a>"
                + "<a href=\"https://example.com\">ok</a></li></ol>");

        assertThat(result).isEqualTo(
                "<ol><li><a>x</a><a>y</a><a>z</a><a href=\"https://example.com\">ok</a></li></ol>");
    }

    // colspan 값 안에서 attribute 경계를 탈출하려는 입력은 jsoup이 하나의 값으로 확정하므로 새 attribute가
    // 생기지 않고, 그 값은 1~50 정수가 아니므로 colspan 자체가 제거된다.
    @Test
    void spanAttributeEscapeAttemptDoesNotCreateNewAttribute() {
        String result = HtmlSanitizer.sanitize(
                "<table><tbody><tr><td colspan='2\" onmouseover=\"alert(1)'>c</td></tr></tbody></table>");

        assertThat(result).isEqualTo("<table><tbody><tr><td>c</td></tr></tbody></table>");
    }

    @Test
    void malformedNestingIsNormalizedWithoutLeakingDisallowedMarkup() {
        String result = HtmlSanitizer.sanitize("<ul><li>a<blockquote><li>b</ul></blockquote>"
                + "<td colspan=5 onclick=x>orphan</td><i><b>x</i></b><script>alert(1)</script>");

        Document doc = Jsoup.parseBodyFragment(result);

        assertThat(doc.select("script, b")).isEmpty();
        assertThat(result).doesNotContain("onclick").doesNotContain("alert(1)")
                .contains("<ul><li>a<blockquote><li>b</li></blockquote></li></ul>")
                .contains("orphan")
                .contains("<i>x</i>");
        assertThat(HtmlSanitizer.sanitize(result)).isEqualTo(result);
    }

    // MediaEmbed는 지원하지 않는다(P15-T4에서 editor plugin 제거). sanitizer도 oembed/iframe을 계속 허용하지 않는다.
    @Test
    void stillRemovesMediaEmbedAndIframeMarkup() {
        String result = HtmlSanitizer.sanitize(
                "<figure class=\"media\"><oembed url=\"https://www.youtube.com/watch?v=abc\"></oembed></figure>"
                        + "<ul><li><iframe src=\"https://www.google.com/maps/embed?pb=x\"></iframe>map</li></ul>");

        assertThat(result).isEqualTo("<figure></figure><ul><li>map</li></ul>");
    }
}
