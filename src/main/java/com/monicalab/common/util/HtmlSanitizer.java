package com.monicalab.common.util;

import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

public final class HtmlSanitizer {

    // 실제 요청 host와 무관한 더미 값이다. jsoup의 Safelist protocol 검사(Safelist#testValidProtocol)는
    // 속성값을 baseUri 기준으로 절대경로화한 뒤에만 protocol을 확인할 수 있어서, baseUri가 없으면
    // "/api/files/1" 같은 상대경로는 절대화가 안 돼 무조건 제거된다(허용 protocol이 http/https뿐이라도).
    // preserveRelativeLinks(true)와 함께 쓰면 protocol 검증에만 이 baseUri를 쓰고, 저장되는 값은
    // 항상 원래 입력 그대로의 상대경로를 유지한다 - 실제 요청 host를 DB에 박아넣지 않기 위함이다.
    private static final String DUMMY_BASE_URI = "http://sanitizer.invalid/";

    // jsoup 1차 정제는 "http/https로 절대화되는지"만 확인하므로 프로토콜 상대(//evil.com/x) 나
    // 이 앱의 다른 경로(/admin/...) 같은 임의의 상대경로까지 함께 통과시킨다. 이 앱이 실제로 서빙하는
    // 파일 URL 형태(FileResponse.url = "/api/files/" + id)에만 상대경로 img src를 좁게 허용하기 위해
    // 2차로 이 패턴을 적용한다.
    private static final Pattern INTERNAL_IMAGE_SRC = Pattern.compile("^/api/files/\\d+$");

    private static final Safelist SAFELIST = Safelist.none()
            .addTags(
                    "p", "br", "strong", "em", "u",
                    "h1", "h2", "h3", "h4", "h5", "h6",
                    "table", "tr", "td", "th",
                    "a", "img")
            .addAttributes("a", "href")
            .addAttributes("img", "src")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addProtocols("img", "src", "http", "https")
            .preserveRelativeLinks(true);

    private HtmlSanitizer() {
    }

    public static String sanitize(String html) {
        if (html == null) {
            return null;
        }
        String cleaned = Jsoup.clean(html, DUMMY_BASE_URI, SAFELIST);
        return restrictRelativeImageSources(cleaned);
    }

    private static String restrictRelativeImageSources(String cleanedHtml) {
        Document doc = Jsoup.parseBodyFragment(cleanedHtml);
        for (Element img : doc.select("img[src]")) {
            String src = img.attr("src");
            boolean isAbsoluteHttp = src.startsWith("http://") || src.startsWith("https://");
            if (!isAbsoluteHttp && !INTERNAL_IMAGE_SRC.matcher(src).matches()) {
                img.removeAttr("src");
            }
        }
        return doc.body().html();
    }
}
