package com.monicalab.program.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.monicalab.program.entity.ProgramType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 아직 Controller가 없으므로(TASK.md P5-T1) Bean Validation을 직접 호출하는
 * 단위 테스트로 ProgramRequest의 Validation 계약을 검증한다.
 */
class ProgramRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void invalidGoogleFormUrlFailsValidation() {
        ProgramRequest request = new ProgramRequest(
                ProgramType.COURSE, "제목", null, null, null, "not-a-valid-url", null, null);

        Set<ConstraintViolation<ProgramRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("googleFormUrl"));
    }

    @Test
    void ftpSchemeGoogleFormUrlFailsValidation() {
        ProgramRequest request = new ProgramRequest(
                ProgramType.COURSE, "제목", null, null, null, "ftp://example.com/form", null, null);

        Set<ConstraintViolation<ProgramRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("googleFormUrl"));
    }

    @Test
    void validHttpsGoogleFormUrlPassesValidation() {
        ProgramRequest request = new ProgramRequest(
                ProgramType.COURSE, "제목", null, null, null, "https://forms.google.com/abc", null, null);

        Set<ConstraintViolation<ProgramRequest>> violations = validator.validate(request);

        assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("googleFormUrl"));
    }

    @Test
    void validHttpGoogleFormUrlPassesValidation() {
        ProgramRequest request = new ProgramRequest(
                ProgramType.COURSE, "제목", null, null, null, "http://forms.google.com/abc", null, null);

        Set<ConstraintViolation<ProgramRequest>> violations = validator.validate(request);

        assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("googleFormUrl"));
    }

    @Test
    void nullGoogleFormUrlPassesValidation() {
        ProgramRequest request = new ProgramRequest(
                ProgramType.COURSE, "제목", null, null, null, null, null, null);

        Set<ConstraintViolation<ProgramRequest>> violations = validator.validate(request);

        assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("googleFormUrl"));
    }

    @Test
    void emptyStringGoogleFormUrlPassesValidation() {
        ProgramRequest request = new ProgramRequest(
                ProgramType.COURSE, "제목", null, null, null, "", null, null);

        Set<ConstraintViolation<ProgramRequest>> violations = validator.validate(request);

        assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("googleFormUrl"));
    }

    @Test
    void blankTitleFailsValidation() {
        ProgramRequest request = new ProgramRequest(
                ProgramType.COURSE, " ", null, null, null, null, null, null);

        Set<ConstraintViolation<ProgramRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("title"));
    }

    @Test
    void missingProgramTypeFailsValidation() {
        ProgramRequest request = new ProgramRequest(
                null, "제목", null, null, null, null, null, null);

        Set<ConstraintViolation<ProgramRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("programType"));
    }
}
