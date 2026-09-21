package com.monicalab.common.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.jsoup.select.Elements;

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

    // P13-T23: CKEditor 5 41.4.2 classic build의 실제 getData() 출력을 헤드리스로 직접 확인해 결정한
    // 최소 허용 목록이다(추측 금지). ImageStyle이 block 이미지에 붙이는 class는 항상 <figure>에만
    // 나타나고 <img>에는 붙지 않으며(inline 스타일은 <figure> 자체가 없는 bare <img>로 표현되어 애초에
    // class가 필요 없음을 실측 확인), 값은 이 5개 토큰 조합으로만 나타난다. class 값 전체를 정규식으로
    // 검증하지 않고 whitespace로 분리한 토큰 단위로 화이트리스트 대조 후 안전한 토큰만 남긴다.
    private static final Set<String> ALLOWED_FIGURE_CLASS_TOKENS = Set.of(
            "image", "image-style-side",
            "image-style-align-left", "image-style-align-right", "image-style-align-center",
            "image_resized");

    // P13-T40: 관리자가 25%/50%/75%/원본(속성 없음) preset으로만 이미지 크기를 조절하는 자체 기능의
    // 서버측 화이트리스트다. CKEditor의 공식 ImageResize plugin(우리 build엔 없음)이 만드는 형식과
    // 동일하게 맞췄지만(헤드리스로 직접 확인: <figure class="image image_resized" style="width:50%;">),
    // 값 자체는 이 3개 preset으로만 엄격히 제한한다(자유 드래그 리사이즈는 P13-T40 범위 밖).
    // "width" 하나만 있는 단일 선언만 허용하고, 공백/세미콜론의 흔한 변형(있음/없음)만 유연하게
    // 받아들인다 - 다른 프로퍼티가 하나라도 섞이면(예: "width:50%;background:red") 정규식 전체 일치가
    // 실패해 style 전체를 폐기한다(일부만 골라 살리는 permissive 파서를 의도적으로 피함).
    private static final Pattern FIGURE_WIDTH_STYLE_PATTERN =
            Pattern.compile("^\\s*width\\s*:\\s*(25|50|75)%\\s*;?\\s*$");

    // P13-T39: CKEditor의 ImageTextAlternative("대체 텍스트")/ImageCaption("캡션 넣기/빼기") 툴바
    // 버튼이 실제로 만드는 output(헤드리스로 직접 확인, P13-T23와 동일한 방식)을 그대로 보존한다.
    // alt는 <img>의 평범한 문자열 attribute이고, figcaption은 <figure> 안의 형제 tag일 뿐이라 이
    // 둘을 허용해도 새로운 실행 경로가 생기지 않는다(jsoup은 attribute 값을 항상 이스케이프해
    // 직렬화하므로 alt 안의 "<script>" 같은 문자열이 실제 태그로 되살아나지 않고, figcaption에는
    // 아래에서 어떤 attribute도 허용하지 않으므로 style/class/id/on* 이벤트 핸들러가 전부 제거된다).
    private static final Safelist SAFELIST = Safelist.none()
            .addTags(
                    "p", "br", "strong", "em", "u",
                    "h1", "h2", "h3", "h4", "h5", "h6",
                    "table", "tr", "td", "th",
                    "a", "img", "figure", "figcaption")
            .addAttributes("a", "href")
            .addAttributes("img", "src", "alt")
            .addAttributes("figure", "class")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addProtocols("img", "src", "http", "https")
            .preserveRelativeLinks(true);

    private HtmlSanitizer() {
    }

    // P13-T39: figcaption을 새로 허용하면서 jsoup의 기본 HTML 출력 설정이 그것을 block 태그로 취급해
    // 줄바꿈/들여쓰기를 끼워 넣는 것을 발견했다(다른 허용 태그에는 없던 동작). CKEditor가 실제로 만드는
    // compact 출력을 그대로 보존하기 위해 pretty-print를 끈다 - 3개 직렬화 지점(최초 clean + 아래 두
    // 커스텀 후처리 pass) 전부에 동일하게 적용해야 파이프라인 전체에서 포맷이 다시 흐트러지지 않는다.
    private static final Document.OutputSettings COMPACT_OUTPUT = new Document.OutputSettings().prettyPrint(false);

    public static String sanitize(String html) {
        if (html == null) {
            return null;
        }
        // P13-T40: Safelist는 figure의 style attribute를 전혀 허용하지 않는다(변경 없음, 의도적).
        // 그래서 width 값은 Safelist clean이 원본 style을 완전히 벗겨내기 *전에* 원본 raw HTML에서
        // 직접 검증/추출해 두고, clean이 끝난 뒤 검증된 값만 다시 그려 넣는다("extract → clean →
        // reinject"). 이 방식은 "허용되지 않은 attribute를 임시로 허용했다가 사후에 걸러내는" 방식과
        // 달리, style이 Safelist를 통과하는 경로 자체가 코드 어디에도 존재하지 않는다.
        List<String> validatedFigureWidths = extractValidatedFigureWidths(html);
        String cleaned = Jsoup.clean(html, DUMMY_BASE_URI, SAFELIST, COMPACT_OUTPUT);
        String restrictedSources = restrictRelativeImageSources(cleaned);
        String restrictedClasses = restrictFigureClasses(restrictedSources);
        return reinjectValidatedFigureWidths(restrictedClasses, validatedFigureWidths);
    }

    private static String restrictRelativeImageSources(String cleanedHtml) {
        Document doc = Jsoup.parseBodyFragment(cleanedHtml);
        doc.outputSettings(COMPACT_OUTPUT);
        for (Element img : doc.select("img[src]")) {
            String src = img.attr("src");
            boolean isAbsoluteHttp = src.startsWith("http://") || src.startsWith("https://");
            if (!isAbsoluteHttp && !INTERNAL_IMAGE_SRC.matcher(src).matches()) {
                img.removeAttr("src");
            }
        }
        return doc.body().html();
    }

    private static String restrictFigureClasses(String cleanedHtml) {
        Document doc = Jsoup.parseBodyFragment(cleanedHtml);
        doc.outputSettings(COMPACT_OUTPUT);
        for (Element figure : doc.select("figure[class]")) {
            List<String> safeTokens = Arrays.stream(figure.attr("class").trim().split("\\s+"))
                    .filter(ALLOWED_FIGURE_CLASS_TOKENS::contains)
                    .toList();
            if (safeTokens.isEmpty()) {
                figure.removeAttr("class");
            } else {
                figure.attr("class", String.join(" ", safeTokens));
            }
        }
        return doc.body().html();
    }

    // P13-T40: 원본 raw HTML을 (Safelist clean 이전에) 그대로 파싱해 <figure>를 문서 순서대로 순회하며,
    // 각 figure의 style이 FIGURE_WIDTH_STYLE_PATTERN과 완전히 일치할 때만 그 preset 값("25"/"50"/"75")을
    // 기록한다. 일치하지 않으면(다른 프로퍼티 혼입, 범위 밖 값, 소수/음수, calc()/var()/expression() 등)
    // 그 figure 자리에는 null을 남긴다 - 리스트의 인덱스가 그대로 figure의 문서 순서와 대응한다.
    //
    // 이 인덱스 대응이 안전한 이유(순서가 뒤섞이는 "취약한 index matching"이 아님): <figure>는
    // SAFELIST에 항상 허용된 태그라서, 이후 Jsoup.clean()이나 restrictRelativeImageSources/
    // restrictFigureClasses 어느 단계도 <figure> 요소 자체를 제거하거나 순서를 바꾸거나 중복시키지
    // 않는다(허용되지 않은 조상/형제 태그는 "벗겨내기(unwrap)"만 될 뿐 <figure>를 삼키지 않는다).
    // 즉 원본과 clean 후 결과의 <figure> 개수·순서는 항상 1:1로 동일하다 - reinjectFigureWidthsTest의
    // 회귀 테스트가 이 불변조건을 직접 증명한다(제거되는 형제/조상 태그가 섞여 있어도 순서가 안 깨짐).
    private static List<String> extractValidatedFigureWidths(String rawHtml) {
        Document rawDoc = Jsoup.parseBodyFragment(rawHtml);
        Elements figures = rawDoc.select("figure");
        List<String> widths = new ArrayList<>(figures.size());
        for (Element figure : figures) {
            widths.add(extractValidWidthOrNull(figure));
        }
        return widths;
    }

    private static String extractValidWidthOrNull(Element figure) {
        if (!figure.hasAttr("style")) {
            return null;
        }
        Matcher matcher = FIGURE_WIDTH_STYLE_PATTERN.matcher(figure.attr("style"));
        return matcher.matches() ? matcher.group(1) : null;
    }

    // clean이 끝난 최종 HTML을 다시 파싱해 <figure>를 같은 순서로 순회하며, extractValidatedFigureWidths가
    // 미리 검증해 둔 값만 그려 넣는다(값이 있으면 style+image_resized class를 함께 설정, 없으면 둘 다
    // 확실히 제거해 "resize 안 된 상태"와 "resize 취소(원본) 상태"가 항상 style/class 없는 동일한 모습이
    // 되도록 정규화한다 - 공격자가 style 없이 image_resized class만 주입해도 여기서 제거된다).
    private static String reinjectValidatedFigureWidths(String html, List<String> validatedWidths) {
        Document doc = Jsoup.parseBodyFragment(html);
        doc.outputSettings(COMPACT_OUTPUT);
        Elements figures = doc.select("figure");
        for (int i = 0; i < figures.size() && i < validatedWidths.size(); i++) {
            Element figure = figures.get(i);
            String width = validatedWidths.get(i);
            if (width != null) {
                figure.attr("style", "width:" + width + "%;");
                figure.addClass("image_resized");
            } else {
                figure.removeAttr("style");
                figure.removeClass("image_resized");
            }
        }
        return doc.body().html();
    }
}
