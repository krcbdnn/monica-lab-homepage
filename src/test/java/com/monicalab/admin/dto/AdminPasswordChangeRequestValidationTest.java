package com.monicalab.admin.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// P15-T6: 새 비밀번호 정책(8~64자, 공백 없는 ASCII 출력 문자만, 영문/숫자/특수문자 중 2종 이상)을 Bean Validation
// 단위로 고정한다. 비ASCII를 입력 단계에서 거부하므로 BCrypt 72byte 한도(encode 예외 → 500)에 도달하지 않는다.
class AdminPasswordChangeRequestValidationTest {

    private static final String CURRENT = "current-password-1";

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

    @ParameterizedTest
    @ValueSource(strings = {
            "abcd1234",       // 영문 + 숫자, 8자(최소)
            "abcd!@#$",       // 영문 + 특수문자
            "1234!@#$",       // 숫자 + 특수문자
            "Ab1!Ab1!",       // 3종
            "a1[]\\^_`{|}~",  // 경계 ASCII 기호([\]^_`{|}~)도 특수문자로 인정
            "ZZZZZZZ0"        // 대문자 + 숫자
    })
    void acceptsPasswordsMatchingPolicy(String newPassword) {
        assertThat(newPasswordViolations(newPassword)).isEmpty();
    }

    @Test
    void acceptsExactly64AsciiCharacters() {
        String password = "a1".repeat(32);

        assertThat(password).hasSize(64);
        assertThat(password.getBytes(StandardCharsets.UTF_8)).hasSize(64);
        assertThat(newPasswordViolations(password)).isEmpty();
    }

    @Test
    void rejectsSevenCharacters() {
        assertThat(newPasswordViolations("abc1234")).isNotEmpty();
    }

    @Test
    void rejects65Characters() {
        assertThat(newPasswordViolations("a1".repeat(32) + "b")).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abcdefgh",       // 영문만
            "ABCDEFGHIJ",     // 대문자만
            "12345678",       // 숫자만
            "!@#$%^&*"        // 특수문자만
    })
    void rejectsSingleCharacterClass(String newPassword) {
        assertThat(newPasswordViolations(newPassword)).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abcd 1234",      // 공백
            "abcd\t1234",     // 탭
            "abcd1234\n",     // 줄바꿈
            "비밀번호abc123",    // 한글
            "abcd1234é",      // 라틴 확장(비ASCII)
            "abcd1234 ", // non-breaking space
            "ａｂｃｄ１２３４",     // 전각 영문/숫자
            "abcd1234😀"      // emoji(서로게이트 쌍)
    })
    void rejectsWhitespaceAndNonAscii(String newPassword) {
        assertThat(newPasswordViolations(newPassword)).isNotEmpty();
    }

    // 64자 이하라도 비ASCII면 UTF-8 72byte를 넘을 수 있다(한글 32자 = 96byte). 정책이 입력 단계에서 거부해야 한다.
    @Test
    void rejectsNonAsciiPasswordThatWouldExceedBcrypt72Bytes() {
        String password = "가나다라마바사아자차카타파하1!".repeat(2);

        assertThat(password.length()).isLessThanOrEqualTo(64);
        assertThat(password.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(72);
        assertThat(newPasswordViolations(password)).isNotEmpty();
    }

    @Test
    void rejectsBlankCurrentPassword() {
        Set<ConstraintViolation<AdminPasswordChangeRequest>> violations =
                validator.validate(new AdminPasswordChangeRequest(" ", "abcd1234"));

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("currentPassword"));
    }

    @Test
    void rejectsMissingNewPassword() {
        assertThat(newPasswordViolations(null)).isNotEmpty();
    }

    private Set<ConstraintViolation<AdminPasswordChangeRequest>> newPasswordViolations(String newPassword) {
        Set<ConstraintViolation<AdminPasswordChangeRequest>> violations =
                validator.validate(new AdminPasswordChangeRequest(CURRENT, newPassword));
        assertThat(violations).allMatch(v -> v.getPropertyPath().toString().equals("newPassword"));
        return violations;
    }
}
