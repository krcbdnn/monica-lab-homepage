package com.monicalab.common.exception;

import com.monicalab.board.controller.BoardViewController;
import com.monicalab.home.controller.HomeController;
import com.monicalab.page.controller.PageViewController;
import com.monicalab.program.controller.ProgramViewController;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;

// P15-T5: 공개 View Controller 4개(HeaderMenuControllerAdvice/ThemeControllerAdvice와 같은 범위)에서 발생한
// 예외만 독립 HTML 오류 페이지(templates/error/4xx.html, 5xx.html)로 응답한다. 관리자/REST Controller는
// 대상이 아니므로 /api/** JSON과 관리자 상세 404(JSON)는 GlobalExceptionHandler가 그대로 처리한다.
// Spring은 적용 가능한 advice를 @Order 순서로 보고 매핑이 있는 첫 advice를 쓰는데, @Order가 없는
// GlobalExceptionHandler는 최저 우선순위라 이 advice가 먼저 선택되도록 명시한다.
// 예외 처리 경로에서는 Controller advice의 @ModelAttribute(메뉴/테마 DB 조회)가 실행되지 않고, 템플릿도
// 그 model에 의존하지 않으므로 DB 장애로 인한 500도 렌더링된다. model에는 status만 넣는다(예외 메시지/
// 경로/ErrorCode 비노출). 로그는 GlobalExceptionHandler(P15-T1)와 같은 형식/레벨을 따른다.
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice(assignableTypes = {
        HomeController.class,
        PageViewController.class,
        ProgramViewController.class,
        BoardViewController.class
})
public class PublicViewExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ModelAndView handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        HttpStatus status = errorCode.getHttpStatus();
        if (status.is5xxServerError()) {
            log.error("CustomException: code={}, status={}", errorCode, status.value(), e);
        } else {
            log.warn("CustomException: code={}, status={}", errorCode, status.value());
        }
        return errorPage(status);
    }

    // 경로 변수 변환 실패(예: /pages/NOPE, /boards/abc)는 존재할 수 없는 리소스 주소이므로 404,
    // query 값 변환 실패(예: ?boardType=NOPE)는 잘못된 요청 400이다.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ModelAndView handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("Bad request: {}", e.getMessage());
        if (e.getParameter().hasParameterAnnotation(PathVariable.class)) {
            return errorPage(HttpStatus.NOT_FOUND);
        }
        return errorPage(HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({
            BindException.class,
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class
    })
    public ModelAndView handleBadRequest(Exception e) {
        log.warn("Bad request: {}", e.getMessage());
        return errorPage(HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleException(Exception e) {
        log.error("Unhandled exception", e);
        return errorPage(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // GlobalExceptionHandler의 /api/ 외 미매핑 경로 404도 같은 오류 페이지를 쓰도록 package-private으로 공유한다.
    static ModelAndView errorPage(HttpStatus status) {
        String viewName = status.is5xxServerError() ? "error/5xx" : "error/4xx";
        return new ModelAndView(viewName, Map.of("status", status.value()), status);
    }
}
