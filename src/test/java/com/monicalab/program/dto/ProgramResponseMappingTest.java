package com.monicalab.program.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.monicalab.program.entity.Program;
import com.monicalab.program.entity.ProgramType;
import com.monicalab.program.entity.RecruitStatus;
import org.junit.jupiter.api.Test;

/**
 * ERD.md/API.md 계약(programType, title, content, thumbnail, attachment,
 * googleFormUrl, recruitStatus, isPublic 필드)이 Entity/DTO 매핑에 그대로 반영되는지
 * 확인하는 정적/단위 테스트(TASK.md P5-T1 DoD 기준).
 */
class ProgramResponseMappingTest {

    @Test
    void fromMapsEveryEntityFieldToTheResponse() {
        Program program = Program.builder()
                .programType(ProgramType.COURSE)
                .title("여름 방학 특강")
                .content("<p>내용</p>")
                .thumbnail("/api/files/1")
                .attachment("/api/files/2")
                .googleFormUrl("https://forms.google.com/abc")
                .recruitStatus(RecruitStatus.OPEN)
                .isPublic(true)
                .build();

        ProgramResponse response = ProgramResponse.from(program);

        // id/createdAt/updatedAt은 BaseEntity/JPA Auditing이 영속화 시점에 채우므로
        // 영속화 이전 상태에서는 null이 그대로 매핑되는 것이 정상이다(영속화 후 검증은
        // Repository 통합 테스트에서 수행).
        assertThat(response.id()).isNull();
        assertThat(response.programType()).isEqualTo(ProgramType.COURSE);
        assertThat(response.title()).isEqualTo("여름 방학 특강");
        assertThat(response.content()).isEqualTo("<p>내용</p>");
        assertThat(response.thumbnail()).isEqualTo("/api/files/1");
        assertThat(response.attachment()).isEqualTo("/api/files/2");
        assertThat(response.googleFormUrl()).isEqualTo("https://forms.google.com/abc");
        assertThat(response.recruitStatus()).isEqualTo(RecruitStatus.OPEN);
        assertThat(response.isPublic()).isTrue();
    }
}
