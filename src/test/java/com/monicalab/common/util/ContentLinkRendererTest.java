package com.monicalab.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContentLinkRendererTest {

    @Test
    void nullInputReturnsNull() {
        assertThat(ContentLinkRenderer.externalLinksOpenInNewTab(null)).isNull();
    }

    @Test
    void httpsLinkGetsTargetBlankAndRel() {
        String result = ContentLinkRenderer.externalLinksOpenInNewTab(
                "<a href=\"https://example.com\">link</a>");

        assertThat(result).contains("href=\"https://example.com\"")
                .contains("target=\"_blank\"")
                .contains("rel=\"noopener noreferrer\"");
    }

    @Test
    void httpLinkGetsTargetBlankAndRel() {
        String result = ContentLinkRenderer.externalLinksOpenInNewTab(
                "<a href=\"http://example.com\">link</a>");

        assertThat(result).contains("target=\"_blank\"")
                .contains("rel=\"noopener noreferrer\"");
    }

    @Test
    void protocolRelativeLinkGetsTargetBlankAndRel() {
        String result = ContentLinkRenderer.externalLinksOpenInNewTab(
                "<a href=\"//example.com\">link</a>");

        assertThat(result).contains("target=\"_blank\"")
                .contains("rel=\"noopener noreferrer\"");
    }

    @Test
    void uppercaseSchemeIsTreatedAsExternal() {
        String result = ContentLinkRenderer.externalLinksOpenInNewTab(
                "<a href=\"HTTPS://EXAMPLE.COM\">link</a>");

        assertThat(result).contains("target=\"_blank\"")
                .contains("rel=\"noopener noreferrer\"");
    }

    @Test
    void relativeInternalLinkIsUnchanged() {
        String result = ContentLinkRenderer.externalLinksOpenInNewTab(
                "<a href=\"/boards/1\">internal</a>");

        assertThat(result).contains("href=\"/boards/1\"")
                .doesNotContain("target=")
                .doesNotContain("rel=");
    }

    @Test
    void mailtoLinkIsUnchanged() {
        String result = ContentLinkRenderer.externalLinksOpenInNewTab(
                "<a href=\"mailto:test@example.com\">mail</a>");

        assertThat(result).contains("href=\"mailto:test@example.com\"")
                .doesNotContain("target=")
                .doesNotContain("rel=");
    }

    @Test
    void anchorLinkIsUnchanged() {
        String result = ContentLinkRenderer.externalLinksOpenInNewTab(
                "<a href=\"#section\">anchor</a>");

        assertThat(result).contains("href=\"#section\"")
                .doesNotContain("target=")
                .doesNotContain("rel=");
    }

    @Test
    void nonLinkContentSurvivesReparse() {
        String html = "<p>text</p><table><tr><td>cell</td></tr></table>"
                + "<h1>title</h1><strong>bold</strong><em>italic</em><u>underline</u><br>"
                + "<img src=\"/api/files/123\">";

        String result = ContentLinkRenderer.externalLinksOpenInNewTab(html);

        assertThat(result).containsIgnoringCase("<p>")
                .containsIgnoringCase("<table>")
                .containsIgnoringCase("<tr>")
                .containsIgnoringCase("<td>")
                .containsIgnoringCase("<h1>")
                .containsIgnoringCase("<strong>")
                .containsIgnoringCase("<em>")
                .containsIgnoringCase("<u>")
                .containsIgnoringCase("<br")
                .contains("src=\"/api/files/123\"");
    }

    @Test
    void mixedExternalAndInternalLinksAreClassifiedIndependently() {
        String html = "<a href=\"https://example.com\">external</a>"
                + "<a href=\"/boards/1\">internal</a>";

        String result = ContentLinkRenderer.externalLinksOpenInNewTab(html);

        assertThat(result).contains("href=\"https://example.com\" target=\"_blank\" rel=\"noopener noreferrer\"")
                .contains("href=\"/boards/1\">internal");
    }

    @Test
    void isIdempotentOnAlreadyRenderedContent() {
        String once = ContentLinkRenderer.externalLinksOpenInNewTab("<a href=\"https://example.com\">link</a>");
        String twice = ContentLinkRenderer.externalLinksOpenInNewTab(once);

        assertThat(twice).isEqualTo(once);
    }
}
