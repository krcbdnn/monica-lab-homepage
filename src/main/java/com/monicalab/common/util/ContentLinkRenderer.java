package com.monicalab.common.util;

import java.util.Locale;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public final class ContentLinkRenderer {

    private ContentLinkRenderer() {
    }

    public static String externalLinksOpenInNewTab(String html) {
        if (html == null) {
            return null;
        }
        Document doc = Jsoup.parseBodyFragment(html);
        for (Element a : doc.select("a[href]")) {
            if (isExternal(a.attr("href"))) {
                a.attr("target", "_blank");
                a.attr("rel", "noopener noreferrer");
            }
        }
        return doc.body().html();
    }

    private static boolean isExternal(String href) {
        String lower = href.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("//");
    }
}
