package com.monicalab.board.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.monicalab.board.entity.BoardType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 아직 Controller가 없으므로(TASK.md P6-T1) Bean Validation을 직접 호출하는
 * 단위 테스트로 BoardRequest의 Validation 계약을 검증한다.
 */
class BoardRequestValidationTest {

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
    void blankTitleFailsValidation() {
        BoardRequest request = new BoardRequest(BoardType.NOTICE, " ", null, null, null, null, null);

        Set<ConstraintViolation<BoardRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("title"));
    }

    @Test
    void missingBoardTypeFailsValidation() {
        BoardRequest request = new BoardRequest(null, "제목", null, null, null, null, null);

        Set<ConstraintViolation<BoardRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("boardType"));
    }

    @Test
    void validRequestPassesValidation() {
        BoardRequest request = new BoardRequest(BoardType.GALLERY, "제목", "<p>내용</p>", null, null, true, null);

        Set<ConstraintViolation<BoardRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }
}
