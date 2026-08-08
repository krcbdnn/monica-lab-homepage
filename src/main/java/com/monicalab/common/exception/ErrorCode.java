package com.monicalab.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    ADMIN_NOT_FOUND(HttpStatus.NOT_FOUND, "관리자를 찾을 수 없습니다."),
    PAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "페이지를 찾을 수 없습니다."),
    PROGRAM_NOT_FOUND(HttpStatus.NOT_FOUND, "프로그램을 찾을 수 없습니다."),
    BOARD_NOT_FOUND(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다."),
    BANNER_NOT_FOUND(HttpStatus.NOT_FOUND, "배너를 찾을 수 없습니다."),
    POPUP_NOT_FOUND(HttpStatus.NOT_FOUND, "팝업을 찾을 수 없습니다."),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "이미 존재하는 로그인 아이디입니다."),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "허용되지 않은 파일 형식입니다."),
    FILE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "파일 용량이 허용된 크기를 초과했습니다."),
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 업로드 중 오류가 발생했습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
