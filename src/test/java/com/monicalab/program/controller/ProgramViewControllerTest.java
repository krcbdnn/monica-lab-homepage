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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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

    @Test
    void detailAppliesLazyLoadingToThumbnailImage() throws Exception {
        Long id = programRepository.saveAndFlush(Program.builder()
                .programType(ProgramType.COURSE)
                .title("썸네일 있는 프로그램")
                .content("내용")
                .thumbnail("/api/files/1")
                .recruitStatus(RecruitStatus.OPEN)
                .isPublic(true)
                .build()).getId();

        String body = mockMvc.perform(get("/programs/{id}", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Document document = Jsoup.parse(body);
        assertThat(document.select("img").attr("src")).isEqualTo("/api/files/1");
        assertThat(document.select("img").attr("loading")).isEqualTo("lazy");
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
        assertThat(document.select("#program-list .program-list__meta").text()).contains("COURSE", "OPEN");
        assertThat(document.select("#program-list .program-list__link").attr("href")).isEqualTo("/programs/" + id);
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
}
