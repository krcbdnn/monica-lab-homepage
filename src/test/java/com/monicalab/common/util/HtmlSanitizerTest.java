package com.monicalab.common.util;

import static org.assertj.core.api.Assertions.assertThat;

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
}
