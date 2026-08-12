package com.team3.gudit.global.exception;

import com.team3.gudit.domain.goods.dto.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> businessExceptionHandler(
            BusinessException e,
            HttpServletRequest request
    ) {
        String api = extractApi(request);
        String location = extractLocation(e);

        log.warn(
                "[비즈니스 예외] code={}, status={}, api={}, location={}, message={}",
                e.getCode(),
                e.getStatus().value(),
                api,
                location,
                e.getMessage()
        );

        ErrorResponse response = createResponse(
                e.getStatus(),
                e.getCode(),
                e.getMessage(),
                api,
                location
        );

        return ResponseEntity
                .status(e.getStatus())
                .body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e,
            HttpServletRequest request
    ) {
        String api = extractApi(request);
        String location = extractLocation(e);

        String errorMessage = e.getBindingResult()
                .getAllErrors()
                .get(0)
                .getDefaultMessage();

        log.warn("[Validation 예외] api={}, location={}, message={}", api, location, errorMessage);

        ErrorResponse response = createResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_INPUT_VALUE",
                errorMessage,
                api,
                location
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception e,
            HttpServletRequest request
    ) {
        String api = extractApi(request);
        String location = extractLocation(e);

        log.error("[서버 시스템 에러] api={}, location={}", api, location, e);

        ErrorResponse response = createResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "서버 내부 오류가 발생했습니다.",
                api,
                location
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }

    private ErrorResponse createResponse(
            HttpStatus status,
            String code,
            String message,
            String api,
            String location
    ) {
        return ErrorResponse.of(
                status.value(),
                code,
                message,
                api,
                location,
                LocalDateTime.now()
        );
    }

    private String extractApi(HttpServletRequest request) {
        String queryString = request.getQueryString();

        String uri = queryString == null
                ? request.getRequestURI()
                : "%s?%s".formatted(request.getRequestURI(), queryString);

        return "%s %s".formatted(
                request.getMethod(),
                uri
        );
    }

    private String extractLocation(Throwable throwable) {
        StackTraceElement[] stackTrace = throwable.getStackTrace();

        if (stackTrace.length == 0) {
            return "Unknown";
        }

        StackTraceElement element = stackTrace[0];

        return "%s.%s():%d".formatted(
                element.getClassName()
                        .substring(element.getClassName().lastIndexOf('.') + 1),
                element.getMethodName(),
                element.getLineNumber()
        );
    }
}
