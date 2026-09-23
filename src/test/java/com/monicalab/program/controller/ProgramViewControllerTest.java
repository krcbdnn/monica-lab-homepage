package com.monicalab.program.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.monicalab.program.entity.Program;
import com.monicalab.program.entity.ProgramType;
import com.monicalab.program.entity.RecruitStatus;
import com.monicalab.program.repository.ProgramRepository;
import com.monicalab.support.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class ProgramViewControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProgramRepository programRepository;

    @BeforeEach
    void setUp() {
        programRepository.deleteAll();
    }

    @Test
    void listMapsToProgramsPathAndResolvesToHomeProgramListView() throws Exception {
        mockMvc.perform(get("/programs"))
                .andExpect(status().isOk())
                .andExpect(view().name("home/program/list"));
    }

    @Test
    void detailMapsToProgramsIdPathAndResolvesToHomeProgramDetailView() throws Exception {
        Long id = programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.COURSE)
                .title("상세보기 테스트")
                .content("내용")
                .recruitStatus(RecruitStatus.OPEN)
                .isPublic(true)
                .build()).getId();

        mockMvc.perform(get("/programs/{id}", id))
                .andExpect(status().isOk())
                .andExpect(view().name("home/program/detail"));
    }

    @Test
    void detailRendersExternalContentLinkWithTargetBlankAndRelNoopener() throws Exception {
        Long id = programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.COURSE)
                .title("외부 링크 포함 프로그램")
                .content("<p>본문 <a href=\"https://example.com\">외부 링크</a></p>")
                .recruitStatus(RecruitStatus.OPEN)
                .isPublic(true)
                .build()).getId();

        String body = mockMvc.perform(get("/programs/{id}", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        Elements link = document.select("#program-detail-content a[href=https://example.com]");
        assertThat(link).hasSize(1);
        assertThat(link.attr("target")).isEqualTo("_blank");
        assertThat(link.attr("rel")).isEqualTo("noopener noreferrer");
    }

    @Test
    void detailShowsApplyLinkWhenGoogleFormUrlIsPresent() throws Exception {
        Long id = programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.COURSE)
                .title("여름 정규반")
                .content("내용")
                .googleFormUrl("https://forms.gle/abc123")
                .recruitStatus(RecruitStatus.OPEN)
                .isPublic(true)
                .build()).getId();

        String body = mockMvc.perform(get("/programs/{id}", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        assertThat(document.select("#apply-link")).isNotEmpty();
        assertThat(document.select("#apply-link").attr("href")).isEqualTo("https://forms.gle/abc123");
        assertThat(document.select("#apply-link").attr("target")).isEqualTo("_blank");
        assertThat(document.select("#apply-link").attr("rel")).isEqualTo("noopener noreferrer");
    }

    @Test
    void detailShowsAttachmentLinkWithTargetBlankAndRelNoopenerWhenPresent() throws Exception {
        Long id = programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.COURSE)
                .title("첨부파일 있는 프로그램")
                .content("내용")
                .attachment("/api/files/1")
                .recruitStatus(RecruitStatus.OPEN)
                .isPublic(true)
                .build()).getId();

        String body = mockMvc.perform(get("/programs/{id}", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        Elements link = document.select("#program-detail-content a[href=/api/files/1]");
        assertThat(link).hasSize(1);
        assertThat(link.attr("target")).isEqualTo("_blank");
        assertThat(link.attr("rel")).isEqualTo("noopener noreferrer");
    }

    @Test
    void detailHidesApplyLinkWhenGoogleFormUrlIsNull() throws Exception {
        Long id = programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.COURSE)
                .title("구글폼 없는 프로그램")
                .content("내용")
                .recruitStatus(RecruitStatus.OPEN)
                .isPublic(true)
                .build()).getId();

        String body = mockMvc.perform(get("/programs/{id}", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        assertThat(document.select("#apply-link")).isEmpty();
    }

    // P13-T41: 대표 이미지(thumbnail)는 목록/홈/HomePinnedContent 카드 전용 메타데이터로 역할이
    // 분리됐다 - 상세 본문에는 더 이상 자동으로 <img>가 삽입되지 않는다(CKEditor content가 상세
    // 이미지의 실제 표현 영역). thumbnail 필드/데이터 자체는 계속 저장되며(목록/홈에서 계속 사용),
    // 이 테스트는 상세 화면에서만 자동 렌더링이 사라졌음을 검증한다.
    @Test
    void detailDoesNotAutoRenderThumbnailImageOutsideContent() throws Exception {
        Long id = programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.COURSE)
                .title("대표 이미지 있는 프로그램")
                .content("<p>본문 내용</p>")
                .thumbnail("/api/files/1")
                .recruitStatus(RecruitStatus.OPEN)
                .isPublic(true)
                .build()).getId();

        String body = mockMvc.perform(get("/programs/{id}", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);

        assertThat(document.select("#program-detail-content > img")).isEmpty();
        assertThat(document.select(".ckeditor-content").text()).contains("본문 내용");
    }

    // 대표 이미지와 별개로, 본문(CKEditor content)에 삽입된 이미지는 정상적으로 그대로 렌더링돼야
    // 한다 - 상세 화면에는 본문 이미지 1개만 존재하고 thumbnail에 대한 별도 <img>가 추가되지 않는다.
    @Test
    void detailRendersOnlyContentImageWhenBothThumbnailAndContentImageExist() throws Exception {
        Long id = programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.COURSE)
                .title("본문 이미지 프로그램")
                .content("<figure class=\"image\"><img src=\"/api/files/2\"></figure>")
                .thumbnail("/api/files/1")
                .recruitStatus(RecruitStatus.OPEN)
                .isPublic(true)
                .build()).getId();

        String body = mockMvc.perform(get("/programs/{id}", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        Elements images = document.select("#program-detail-content img");

        assertThat(images).hasSize(1);
        assertThat(images.attr("src")).isEqualTo("/api/files/2");
    }

    @Test
    void listRendersThumbnailTitleAndExistingTypeStatusInfoInsideTheItemLinkKeepingUlLiAStructure() throws Exception {
        Long id = programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.COURSE)
                .title("목록 썸네일 확인용 프로그램")
                .content("내용")
                .thumbnail("/api/files/3")
                .recruitStatus(RecruitStatus.OPEN)
                .isPublic(true)
                .build()).getId();

        String body = mockMvc.perform(get("/programs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);

        // 기존 ul#program-list > li > a 구조가 그대로인지(요소를 제거하거나 카드형 grid로 재구성하지 않았는지).
        assertThat(document.select("ul#program-list.list-group")).isNotEmpty();
        assertThat(document.select("#program-list > li.list-group-item > a.program-list__link")).hasSize(1);

        assertThat(document.select("#program-list .program-list__thumb img").attr("src")).isEqualTo("/api/files/3");
        assertThat(document.select("#program-list .program-list__thumb img").attr("loading")).isEqualTo("lazy");
        assertThat(document.select("#program-list .program-list__title").text()).isEqualTo("목록 썸네일 확인용 프로그램");
        // P14-T4A: raw enum(COURSE/OPEN)이 아니라 한글 표시명(수강/모집중)으로 노출된다(domain enum/DB
        // 값 자체는 무변경 - presentation layer에서만 매핑). 아래 4개 테스트가 COURSE/SPECIAL, OPEN/CLOSED
        // 전체 조합을 검증한다.
        assertThat(document.select("#program-list .program-list__meta").text()).contains("수강", "모집중");
        assertThat(document.select("#program-list .program-list__meta").text()).doesNotContain("COURSE", "OPEN");
        assertThat(document.select("#program-list .program-list__link").attr("href")).isEqualTo("/programs/" + id);
    }

    // P14-T4A: ProgramType/RecruitStatus 각각의 나머지 값(SPECIAL/CLOSED)도 한글로 표시되는지 확인한다.
    // 두 enum 모두 COURSE/SPECIAL, OPEN/CLOSED 두 값만 존재하므로(구현 전 재확인) 이 조합으로 전체를 덮는다.
    @Test
    void listShowsKoreanLabelsForSpecialProgramTypeAndClosedRecruitStatusInsteadOfRawEnumNames() throws Exception {
        programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.SPECIAL)
                .title("마감된 특강")
                .content("내용")
                .recruitStatus(RecruitStatus.CLOSED)
                .isPublic(true)
                .build());

        String body = mockMvc.perform(get("/programs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        String metaText = document.select("#program-list .program-list__meta").text();

        assertThat(metaText).contains("특강", "모집마감");
        assertThat(metaText).doesNotContain("SPECIAL", "CLOSED");
    }

    // OPEN 상태 배지만 강조 클래스(.is-open)를 갖는지 확인한다(색만으로 상태를 전달하지 않도록 텍스트
    // "모집중"/"모집마감"은 항상 함께 존재함을 위 두 테스트가 이미 검증했다).
    @Test
    void listMarksOnlyOpenRecruitStatusBadgeWithIsOpenModifierClass() throws Exception {
        programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.COURSE)
                .title("모집중 프로그램")
                .content("내용")
                .recruitStatus(RecruitStatus.OPEN)
                .isPublic(true)
                .build());
        programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.COURSE)
                .title("마감 프로그램")
                .content("내용")
                .recruitStatus(RecruitStatus.CLOSED)
                .isPublic(true)
                .build());

        String body = mockMvc.perform(get("/programs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);

        assertThat(document.select(".program-list__status-badge.is-open")).hasSize(1);
        assertThat(document.select(".program-list__status-badge.is-open").text()).isEqualTo("모집중");
    }

    @Test
    void listShowsPlaceholderWhenThumbnailIsNull() throws Exception {
        programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.COURSE)
                .title("썸네일 null 프로그램")
                .content("내용")
                .recruitStatus(RecruitStatus.OPEN)
                .isPublic(true)
                .build());

        String body = mockMvc.perform(get("/programs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);

        assertThat(document.select("#program-list .program-list__thumb-placeholder")).isNotEmpty();
        assertThat(document.select("#program-list .program-list__thumb img")).isEmpty();
    }

    @Test
    void listShowsPlaceholderWhenThumbnailIsEmptyString() throws Exception {
        programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.COURSE)
                .title("썸네일 빈 문자열 프로그램")
                .content("내용")
                .thumbnail("")
                .recruitStatus(RecruitStatus.OPEN)
                .isPublic(true)
                .build());

        String body = mockMvc.perform(get("/programs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);

        assertThat(document.select("#program-list .program-list__thumb-placeholder")).isNotEmpty();
        assertThat(document.select("#program-list .program-list__thumb img")).isEmpty();
    }

    @Test
    void listShowsPlaceholderWhenThumbnailIsWhitespaceOnly() throws Exception {
        programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.COURSE)
                .title("썸네일 공백 문자열 프로그램")
                .content("내용")
                .thumbnail("   ")
                .recruitStatus(RecruitStatus.OPEN)
                .isPublic(true)
                .build());

        String body = mockMvc.perform(get("/programs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);

        assertThat(document.select("#program-list .program-list__thumb-placeholder")).isNotEmpty();
        assertThat(document.select("#program-list .program-list__thumb img")).isEmpty();
    }

    @Test
    void nextPageLinkPreservesProgramTypeAndKeywordOnFirstPage() throws Exception {
        seedTwoSummerCoursePrograms();

        String body = mockMvc.perform(get("/programs")
                        .param("programType", "COURSE")
                        .param("keyword", "summer")
                        .param("size", "1")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);

        assertThat(document.select("#prev-page")).isEmpty();
        String nextHref = document.select("#next-page").attr("href");
        assertThat(nextHref).contains("page=1", "size=1", "programType=COURSE", "keyword=summer");
    }

    @Test
    void prevPageLinkPreservesProgramTypeAndKeywordOnSecondPage() throws Exception {
        seedTwoSummerCoursePrograms();

        String body = mockMvc.perform(get("/programs")
                        .param("programType", "COURSE")
                        .param("keyword", "summer")
                        .param("size", "1")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);

        assertThat(document.select("#next-page")).isEmpty();
        String prevHref = document.select("#prev-page").attr("href");
        assertThat(prevHref).contains("page=0", "size=1", "programType=COURSE", "keyword=summer");
    }

    @Test
    void listUsesDefaultPageSizeOfTenForPublicView() throws Exception {
        seedNPrograms(12);

        String body = mockMvc.perform(get("/programs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        assertThat(document.select("#program-list > li.list-group-item")).hasSize(10);
        assertThat(document.select("#next-page")).isNotEmpty();
    }

    @Test
    void programTypeFilterMarksSelectedOptionActiveAndOthersNot() throws Exception {
        String body = mockMvc.perform(get("/programs").param("programType", "COURSE"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        Elements active = document.select("#program-type-filter .filter-nav__link.is-active");
        assertThat(active).hasSize(1);
        assertThat(active.text()).isEqualTo("수강");
    }

    @Test
    void programTypeFilterMarksAllActiveWhenProgramTypeIsNull() throws Exception {
        String body = mockMvc.perform(get("/programs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        Elements active = document.select("#program-type-filter .filter-nav__link.is-active");
        assertThat(active).hasSize(1);
        assertThat(active.text()).isEqualTo("전체");
    }

    @Test
    void paginationShowsUpToTenPageNumbersInFirstGroup() throws Exception {
        // size=1로 줄여 totalPages=15를 15건만으로 값싸게 재현한다(기본 size=10이면 141건이 필요).
        seedNPrograms(15);

        String body = mockMvc.perform(get("/programs").param("size", "1").param("page", "0"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        Elements numbers = document.select(".pagination-bar__number");
        assertThat(numbers).hasSize(10);
        assertThat(numbers.first().text()).isEqualTo("1");
        assertThat(numbers.last().text()).isEqualTo("10");
        assertThat(document.select(".pagination-bar__number.is-active").text()).isEqualTo("1");
    }

    @Test
    void paginationSwitchesToNextGroupAfterPageTen() throws Exception {
        seedNPrograms(15);

        String body = mockMvc.perform(get("/programs").param("size", "1").param("page", "10"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        Elements numbers = document.select(".pagination-bar__number");
        assertThat(numbers.first().text()).isEqualTo("11");
        assertThat(numbers.last().text()).isEqualTo("15");
        assertThat(document.select(".pagination-bar__number.is-active").text()).isEqualTo("11");
    }

    @Test
    void pageJumpNavigatesToRequestedOneBasedPage() throws Exception {
        seedNPrograms(15);

        String body = mockMvc.perform(get("/programs").param("size", "1").param("pageJump", "3"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        assertThat(document.select(".pagination-bar__number.is-active").text()).isEqualTo("3");
    }

    @Test
    void pageJumpClampsValueBeyondTotalPagesToLastValidPage() throws Exception {
        seedNPrograms(3);

        String body = mockMvc.perform(get("/programs").param("size", "1").param("pageJump", "999"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        assertThat(document.select(".pagination-bar__number.is-active").text()).isEqualTo("3");
    }

    @Test
    void pageJumpWithNonNumericValueIsIgnoredSafelyWithoutError() throws Exception {
        seedNPrograms(3);

        mockMvc.perform(get("/programs").param("pageJump", "abc"))
                .andExpect(status().isOk())
                .andExpect(view().name("home/program/list"));
    }

    @Test
    void pageJumpIsSafeWhenThereIsNoData() throws Exception {
        mockMvc.perform(get("/programs").param("pageJump", "5"))
                .andExpect(status().isOk())
                .andExpect(view().name("home/program/list"));
    }

    private void seedTwoSummerCoursePrograms() {
        for (int i = 0; i < 2; i++) {
            programRepository.saveAndFlush(Program.builder()
                    .programType(ProgramType.COURSE)
                    .title("summer program " + i)
                    .content("summer content")
                    .recruitStatus(RecruitStatus.OPEN)
                    .isPublic(true)
                    .build());
        }
    }

    private void seedNPrograms(int count) {
        List<Program> programs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            programs.add(Program.builder()
                    .programType(ProgramType.COURSE)
                    .title("program " + i)
                    .recruitStatus(RecruitStatus.OPEN)
                    .isPublic(true)
                    .build());
        }
        programRepository.saveAll(programs);
        programRepository.flush();
    }
}
