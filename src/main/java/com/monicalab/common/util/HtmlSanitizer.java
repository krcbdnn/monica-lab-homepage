package com.monicalab.common.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

public final class HtmlSanitizer {

    private static final Safelist SAFELIST = Safelist.none()
            .addTags(
                    "p", "br", "strong", "em", "u",
                    "h1", "h2", "h3", "h4", "h5", "h6",
                    "table", "tr", "td", "th",
                    "a", "img")
            .addAttributes("a", "href")
            .addAttributes("img", "src")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addProtocols("img", "src", "http", "https");

    private HtmlSanitizer() {
    }

    public static String sanitize(String html) {
        if (html == null) {
            return null;
        }
        return Jsoup.clean(html, SAFELIST);
    }
}
