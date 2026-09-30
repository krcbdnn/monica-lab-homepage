package com.monicalab.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// P15-T6: 로그인한 관리자 본인의 비밀번호 변경 요청. 대상 계정 id는 받지 않는다(인증 principal만 사용).
// 새 비밀번호는 공백을 제외한 ASCII 출력 문자(0x21~0x7E)만 허용한다 - 최대 64자가 항상 64byte 이하가 되어
// BCrypt 입력 한도(72byte, 초과 시 BCryptPasswordEncoder.encode가 IllegalArgumentException)를 구조적으로
// 넘지 않는다. 영문/숫자/특수문자(ASCII 기호) 중 2종 이상을 포함해야 한다.
public record AdminPasswordChangeRequest(
        @NotBlank String currentPassword,
        @NotBlank
        @Size(min = 8, max = 64, message = "새 비밀번호는 8자 이상 64자 이하여야 합니다.")
        @Pattern(regexp = NEW_PASSWORD_PATTERN,
                message = "새 비밀번호는 공백·한글 등 비ASCII 문자 없이 영문/숫자/특수문자 중 2종 이상을 포함해야 합니다.")
        String newPassword) {

    private static final String LETTER = "(?=.*[A-Za-z])";
    private static final String DIGIT = "(?=.*[0-9])";
    private static final String SYMBOL = "(?=.*[!-/:-@\\[-`{-~])";

    public static final String NEW_PASSWORD_PATTERN = "^(?:" + LETTER + DIGIT + "|" + LETTER + SYMBOL + "|"
            + DIGIT + SYMBOL + ")[!-~]+$";
}
