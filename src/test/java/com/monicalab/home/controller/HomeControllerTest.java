package com.monicalab.home.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.monicalab.banner.entity.Banner;
import com.monicalab.banner.repository.BannerRepository;
import com.monicalab.board.entity.Board;
import com.monicalab.board.entity.BoardType;
import com.monicalab.board.repository.BoardRepository;
import com.monicalab.popup.entity.Popup;
import com.monicalab.popup.repository.PopupRepository;
import com.monicalab.program.entity.Program;
import com.monicalab.program.entity.ProgramType;
import com.monicalab.program.entity.RecruitStatus;
import com.monicalab.program.repository.ProgramRepository;
import com.monicalab.support.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class HomeControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BannerRepository bannerRepository;

    @Autowired
    private PopupRepository popupRepository;

    @Autowired
    private BoardRepository boardRepository;

    @Autowired
    private ProgramRepository programRepository;

    @BeforeEach
    void setUp() {
        bannerRepository.deleteAll();
        popupRepository.deleteAll();
        boardRepository.deleteAll();
        programRepository.deleteAll();
    }

    @Test
    void homeReturns200WithAllRequiredAreasAndFixedQuickMenuLinks() throws Exception {
        String body = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);

        assertThat(document.select("#banners")).isNotEmpty();
        assertThat(document.select("#popups")).isNotEmpty();
        assertThat(document.select("#latest-reviews")).isNotEmpty();
        assertThat(document.select("#latest-notices")).isNotEmpty();
        assertThat(document.select("#latest-gallery")).isNotEmpty();
        assertThat(document.select("#quick-menu")).isNotEmpty();

        Elements quickMenuLinks = document.select("#quick-menu a");
        assertThat(quickMenuLinks).hasSize(4);
        assertThat(quickMenuLinks.eachAttr("href"))
                .containsExactly("/pages/INTRODUCTION", "/programs", "/boards?boardType=REVIEW", "/boards");
        assertThat(quickMenuLinks.eachText())
                .containsExactly("연구소 소개", "프로그램", "강의 후기", "게시판");
    }

    // P13-T17: 홈 상단 #greeting(인사말 요약), 하단 #program-shortcut(CTA)를 제거했다.
    // /pages/GREETING 상세 페이지 자체는 유지되며 PageController/PageViewController가 별도로 검증한다.
    @Test
    void homeNoLongerRendersGreetingSummaryOrProgramShortcutSections() throws Exception {
        String body = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);

        assertThat(document.select("#greeting")).isEmpty();
        assertThat(document.select("#program-shortcut")).isEmpty();
    }

    @Test
    void homeListsBannerPopupNoticeAndGalleryDomainData() throws Exception {
        bannerRepository.saveAndFlush(Banner.builder()
                .title("메인 배너").image("/api/files/1").sortOrder(0).isVisible(true).build());
        popupRepository.saveAndFlush(Popup.builder()
                .title("공지 팝업")
                .startDate(LocalDateTime.now().minusHours(1))
                .endDate(LocalDateTime.now().plusHours(1))
                .isVisible(true)
                .build());
        Board notice = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE).title("최신 공지 제목")
                .isPublic(true).build());
        Board gallery = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.GALLERY).title("최신 갤러리 제목")
                .isPublic(true).build());

        String body = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);

        assertThat(document.select("#banners img").attr("src")).isEqualTo("/api/files/1");
        assertThat(document.select("#banners img").attr("loading")).isEqualTo("eager");
        assertThat(document.select("#popups").text()).contains("공지 팝업");
        assertThat(document.select("#latest-notices .notice-list__link").text()).contains("최신 공지 제목");
        assertThat(document.select("#latest-notices .notice-list__link").attr("href"))
                .isEqualTo("/boards/" + notice.getId());
        assertThat(document.select("#latest-gallery .gallery-card__link").text()).contains("최신 갤러리 제목");
        assertThat(document.select("#latest-gallery .gallery-card__link").attr("href"))
                .isEqualTo("/boards/" + gallery.getId());
    }

    @Test
    void latestProgramsShowsAtMostThreeMostRecentPublicPrograms() throws Exception {
        programRepository.saveAndFlush(publicProgram("가장 오래된 프로그램"));
        Thread.sleep(1100);
        programRepository.saveAndFlush(publicProgram("프로그램 A"));
        programRepository.saveAndFlush(publicProgram("프로그램 B"));
        programRepository.saveAndFlush(publicProgram("프로그램 C"));

        String body = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        Elements cards = document.select("#latest-programs .program-card");

        assertThat(cards).hasSize(3);
        String cardsText = cards.text();
        assertThat(cardsText).contains("프로그램 A", "프로그램 B", "프로그램 C");
        assertThat(cardsText).doesNotContain("가장 오래된 프로그램");
    }

    @Test
    void latestProgramsCardLinksToProgramDetailPage() throws Exception {
        Long id = programRepository.saveAndFlush(publicProgram("링크 확인용 프로그램")).getId();

        String body = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        assertThat(document.select("#latest-programs .program-card__link").attr("href"))
                .isEqualTo("/programs/" + id);
    }

    @Test
    void latestProgramsShowsKoreanRecruitStatusLabelsInsteadOfRawEnumName() throws Exception {
        programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.COURSE).title("모집중 프로그램").content("내용")
                .recruitStatus(RecruitStatus.OPEN).isPublic(true).build());
        programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.COURSE).title("마감 프로그램").content("내용")
                .recruitStatus(RecruitStatus.CLOSED).isPublic(true).build());

        String body = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        Elements statusBadges = document.select("#latest-programs .program-card__status");

        assertThat(statusBadges.text()).contains("모집중", "모집마감");
        assertThat(statusBadges.text()).doesNotContain("OPEN", "CLOSED");
    }

    @Test
    void latestProgramsShowsEmptyStateWhenNoProgramsExist() throws Exception {
        String body = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);

        assertThat(document.select("#latest-programs .program-card")).isEmpty();
        assertThat(document.select("#latest-programs .empty-state")).isNotEmpty();
    }

    @Test
    void latestProgramsSectionTitleLinksToProgramsListPage() throws Exception {
        String body = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);

        assertThat(document.select("#latest-programs .section-title__link").attr("href")).isEqualTo("/programs");
    }

    @Test
    void heroRendersAllVisibleBannersInSortOrderWhenMultipleExist() throws Exception {
        bannerRepository.saveAndFlush(Banner.builder()
                .title("첫 번째 배너").image("/api/files/1").sortOrder(0).isVisible(true).build());
        bannerRepository.saveAndFlush(Banner.builder()
                .title("두 번째 배너").image("/api/files/2").sortOrder(1).isVisible(true).build());
        bannerRepository.saveAndFlush(Banner.builder()
                .title("세 번째 배너").image("/api/files/3").sortOrder(2).isVisible(true).build());

        String body = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        Elements slides = document.select("#banners .hero__slide");
        Elements images = document.select("#banners .hero__slide img");
        Elements indicators = document.select("#banners .hero__indicator");

        assertThat(slides).hasSize(3);
        assertThat(images.eachAttr("src")).containsExactly("/api/files/1", "/api/files/2", "/api/files/3");
        assertThat(indicators).hasSize(3);
        assertThat(slides.get(0).hasAttr("hidden")).isFalse();
        assertThat(slides.get(1).hasAttr("hidden")).isTrue();
        assertThat(slides.get(2).hasAttr("hidden")).isTrue();
    }

    // hidden 상태인 두 번째 이후 슬라이드는 loading="lazy"의 뷰포트 교차 트리거가 발동하지 않아
    // 최초 전환 순간까지 이미지 요청 자체가 시작되지 않는 문제가 있었다(Docker 8088 Playwright 네트워크
    // 추적으로 확인). 모든 슬라이드를 eager로 페이지 로드 시점에 미리 받아오되, fetchpriority="high"는
    // LCP 후보인 첫 슬라이드에만 부여해 대역폭 경쟁을 최소화한다.
    @Test
    void heroAppliesEagerLoadingToAllSlidesAndFetchPriorityHighOnlyToFirst() throws Exception {
        bannerRepository.saveAndFlush(Banner.builder()
                .title("첫 번째 배너").image("/api/files/1").sortOrder(0).isVisible(true).build());
        bannerRepository.saveAndFlush(Banner.builder()
                .title("두 번째 배너").image("/api/files/2").sortOrder(1).isVisible(true).build());

        String body = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        Elements images = document.select("#banners .hero__slide img");

        assertThat(images).hasSize(2);
        assertThat(images.eachAttr("loading")).containsExactly("eager", "eager");
        assertThat(images.get(0).attr("fetchpriority")).isEqualTo("high");
        assertThat(images.get(1).hasAttr("fetchpriority")).isFalse();
    }

    @Test
    void heroHidesControlsWhenOnlyOneBannerExists() throws Exception {
        bannerRepository.saveAndFlush(Banner.builder()
                .title("단일 배너").image("/api/files/1").sortOrder(0).isVisible(true).build());

        String body = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);

        assertThat(document.select("#banners .hero__slide")).hasSize(1);
        assertThat(document.select("#banners .hero__slide img").attr("src")).isEqualTo("/api/files/1");
        assertThat(document.select("#banners .hero__slide").first().hasAttr("hidden")).isFalse();
        assertThat(document.select("#banners .hero__controls")).isEmpty();
        assertThat(document.select("#banners #hero-prev")).isEmpty();
        assertThat(document.select("#banners #hero-next")).isEmpty();
        assertThat(document.select("#banners #hero-play-pause")).isEmpty();
        assertThat(document.select("#banners .hero__indicator")).isEmpty();
    }

    @Test
    void heroShowsEmptyStateWhenNoBannersExist() throws Exception {
        String body = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);

        assertThat(document.select("#banners img")).isEmpty();
        assertThat(document.select("#banners .hero__empty")).isNotEmpty();
    }

    // #popup-overlay/.popup-modal은 SSR 시점부터 항상 hidden이어야 한다(P13-T10 계약: 서버는 1건으로
    // 자르거나 어느 것을 "활성"으로 표시할지 결정하지 않고, static/js/home/popup-modal.js가 전담한다).
    //
    // 이 테스트는 뷰가 content를 th:utext로 그대로(비이스케이프) 렌더링하는지만 검증한다. sanitize
    // 자체(스크립트 태그 제거 등)는 PopupService.create()/update() 쓰기 경로의 책임이고 별도로 검증되므로
    // (P2-T5/P7-T2), 다른 도메인 테스트들과 동일하게 repository로 직접 저장해 그 경로를 우회하는 이 테스트에서
    // 는 다루지 않는다.
    @Test
    void popupRendersTitleAndContentWithImageInsideDialogMarkup() throws Exception {
        Popup popup = popupRepository.saveAndFlush(Popup.builder()
                .title("이미지 포함 팝업")
                .content("<p>안내 내용</p><img src=\"/api/files/1\">")
                .startDate(LocalDateTime.now().minusHours(1))
                .endDate(LocalDateTime.now().plusHours(1))
                .isVisible(true)
                .build());

        String body = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        Elements modal = document.select("#popup-overlay .popup-modal");

        assertThat(modal).hasSize(1);
        assertThat(modal.attr("id")).isEqualTo("popup-modal-" + popup.getId());
        assertThat(modal.attr("role")).isEqualTo("dialog");
        // 비차단형 floating 카드로 변경(P13-T10 재정정): 배경을 막지 않으므로 aria-modal은 쓰지 않는다.
        assertThat(modal.hasAttr("aria-modal")).isFalse();
        assertThat(modal.attr("aria-labelledby")).isEqualTo("popup-modal-title-" + popup.getId());
        assertThat(document.select("#popup-modal-title-" + popup.getId()).text()).isEqualTo("이미지 포함 팝업");
        assertThat(document.select(".popup-modal__body p").text()).isEqualTo("안내 내용");
        assertThat(document.select(".popup-modal__body img").attr("src")).isEqualTo("/api/files/1");
    }

    @Test
    void popupOverlayAndAllModalsAreHiddenInServerRenderedMarkupRegardlessOfCount() throws Exception {
        popupRepository.saveAndFlush(Popup.builder()
                .title("첫 번째 팝업")
                .startDate(LocalDateTime.now().minusHours(1))
                .endDate(LocalDateTime.now().plusHours(1))
                .isVisible(true)
                .build());
        popupRepository.saveAndFlush(Popup.builder()
                .title("두 번째 팝업")
                .startDate(LocalDateTime.now().minusHours(1))
                .endDate(LocalDateTime.now().plusHours(1))
                .isVisible(true)
                .build());

        String body = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        Elements overlay = document.select("#popup-overlay");
        Elements modals = document.select(".popup-modal");

        assertThat(overlay).hasSize(1);
        assertThat(overlay.first().hasAttr("hidden")).isTrue();
        assertThat(modals).hasSize(2);
        assertThat(modals.get(0).hasAttr("hidden")).isTrue();
        assertThat(modals.get(1).hasAttr("hidden")).isTrue();
    }

    @Test
    void latestNoticesRendersTitleAndDateAndLinksToBoardDetail() throws Exception {
        Board notice = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE).title("공지 목록 확인용 제목")
                .isPublic(true).build());

        String body = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);

        assertThat(document.select("#latest-notices .notice-list__item")).hasSize(1);
        assertThat(document.select("#latest-notices .notice-list__title").text()).isEqualTo("공지 목록 확인용 제목");
        assertThat(document.select("#latest-notices .notice-list__date").text()).isNotBlank();
        assertThat(document.select("#latest-notices .notice-list__link").attr("href"))
                .isEqualTo("/boards/" + notice.getId());
    }

    @Test
    void latestNoticesShowsEmptyStateWhenNoNoticesExist() throws Exception {
        String body = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);

        assertThat(document.select("#latest-notices .notice-list")).isEmpty();
        assertThat(document.select("#latest-notices .empty-state")).isNotEmpty();
    }

    @Test
    void latestNoticesSectionTitleLinksToNoticeListPageAndHasNoLegacyMoreLink() throws Exception {
        String body = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);

        assertThat(document.select("#latest-notices .section-title__link").attr("href"))
                .isEqualTo("/boards?boardType=NOTICE");
        assertThat(document.select("#latest-notices .section__more")).isEmpty();
    }

    @Test
    void latestGalleryRendersThumbnailAndLinksToBoardDetail() throws Exception {
        Board gallery = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.GALLERY).title("갤러리 목록 확인용 제목")
                .thumbnail("/api/files/2")
                .isPublic(true).build());

        String body = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);

        assertThat(document.select("#latest-gallery .gallery-card__thumb img").attr("src"))
                .isEqualTo("/api/files/2");
        assertThat(document.select("#latest-gallery .gallery-card__thumb img").attr("loading")).isEqualTo("lazy");
        assertThat(document.select("#latest-gallery .gallery-card__title").text()).isEqualTo("갤러리 목록 확인용 제목");
        assertThat(document.select("#latest-gallery .gallery-card__link").attr("href"))
                .isEqualTo("/boards/" + gallery.getId());
    }

    @Test
    void latestGalleryShowsPlaceholderWhenThumbnailMissing() throws Exception {
        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.GALLERY).title("썸네일 없는 갤러리")
                .isPublic(true).build());

        String body = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);

        assertThat(document.select("#latest-gallery .gallery-card__thumb-placeholder")).isNotEmpty();
        assertThat(document.select("#latest-gallery .gallery-card__thumb img")).isEmpty();
    }

    @Test
    void latestGalleryShowsEmptyStateWhenNoGalleryExists() throws Exception {
        String body = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);

        assertThat(document.select("#latest-gallery .gallery-grid")).isEmpty();
        assertThat(document.select("#latest-gallery .empty-state")).isNotEmpty();
    }

    @Test
    void latestGallerySectionTitleLinksToGalleryListPageAndHasNoLegacyMoreLink() throws Exception {
        String body = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);

        assertThat(document.select("#latest-gallery .section-title__link").attr("href"))
                .isEqualTo("/boards?boardType=GALLERY");
        assertThat(document.select("#latest-gallery .section__more")).isEmpty();
    }

    @Test
    void latestReviewsShowsAtMostThreeMostRecentPublicReviews() throws Exception {
        boardRepository.saveAndFlush(publicReview("가장 오래된 후기"));
        Thread.sleep(1100);
        boardRepository.saveAndFlush(publicReview("후기 A"));
        boardRepository.saveAndFlush(publicReview("후기 B"));
        boardRepository.saveAndFlush(publicReview("후기 C"));

        String body = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        Elements cards = document.select("#latest-reviews .gallery-card");

        assertThat(cards).hasSize(3);
        String cardsText = cards.text();
        assertThat(cardsText).contains("후기 A", "후기 B", "후기 C");
        assertThat(cardsText).doesNotContain("가장 오래된 후기");
    }

    @Test
    void latestReviewsRendersThumbnailAndLinksToBoardDetail() throws Exception {
        Board review = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.REVIEW).title("강의 후기 목록 확인용 제목")
                .thumbnail("/api/files/3")
                .isPublic(true).build());

        String body = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);

        assertThat(document.select("#latest-reviews .gallery-card__thumb img").attr("src"))
                .isEqualTo("/api/files/3");
        assertThat(document.select("#latest-reviews .gallery-card__thumb img").attr("loading")).isEqualTo("lazy");
        assertThat(document.select("#latest-reviews .gallery-card__title").text()).isEqualTo("강의 후기 목록 확인용 제목");
        assertThat(document.select("#latest-reviews .gallery-card__link").attr("href"))
                .isEqualTo("/boards/" + review.getId());
    }

    @Test
    void latestReviewsShowsPlaceholderWhenThumbnailMissing() throws Exception {
        boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.REVIEW).title("썸네일 없는 후기")
                .isPublic(true).build());

        String body = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);

        assertThat(document.select("#latest-reviews .gallery-card__thumb-placeholder")).isNotEmpty();
        assertThat(document.select("#latest-reviews .gallery-card__thumb img")).isEmpty();
    }

    @Test
    void latestReviewsShowsEmptyStateWhenNoReviewsExist() throws Exception {
        String body = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);

        assertThat(document.select("#latest-reviews .gallery-grid")).isEmpty();
        assertThat(document.select("#latest-reviews .empty-state")).isNotEmpty();
    }

    @Test
    void latestReviewsSectionTitleLinksToReviewListPage() throws Exception {
        String body = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);

        assertThat(document.select("#latest-reviews .section-title__link").attr("href"))
                .isEqualTo("/boards?boardType=REVIEW");
    }

    private Board publicReview(String title) {
        return Board.builder()
                .boardType(BoardType.REVIEW).title(title)
                .isPublic(true).build();
    }

    private Program publicProgram(String title) {
        return Program.builder()
                .programType(ProgramType.COURSE)
                .title(title)
                .content("내용")
                .recruitStatus(RecruitStatus.OPEN)
                .isPublic(true)
                .build();
    }
}
