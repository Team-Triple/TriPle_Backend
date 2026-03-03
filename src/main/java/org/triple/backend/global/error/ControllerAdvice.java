package org.triple.backend.global.error;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.triple.backend.global.error.dto.ErrorResponse;

import java.util.Objects;

@Slf4j
@RestControllerAdvice
public class ControllerAdvice {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("현 errorCode = {}, 메시지 = {}", errorCode, e.getMessage());

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(new ErrorResponse(errorCode.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("잘못된 요청 값: {}", e.getMessage());
        return ResponseEntity
                .badRequest()
                .body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBindException(BindException e) {
        String message = extractFirstBindingMessage(e);
        log.warn("요청 바인딩/검증 실패: {}", message);
        return ResponseEntity
                .badRequest()
                .body(new ErrorResponse(message));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidationException(HandlerMethodValidationException e) {
        String message = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream())
                .map(error -> Objects.toString(error.getDefaultMessage(), null))
                .filter(msg -> msg != null && !msg.isBlank())
                .findFirst()
                .orElse("요청 값이 올바르지 않습니다.");
        log.warn("메서드 파라미터 검증 실패: {}", message);
        return ResponseEntity
                .badRequest()
                .body(new ErrorResponse(message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .filter(msg -> msg != null && !msg.isBlank())
                .findFirst()
                .orElse("요청 값이 올바르지 않습니다.");
        log.warn("제약 조건 검증 실패: {}", message);
        return ResponseEntity
                .badRequest()
                .body(new ErrorResponse(message));
    }

    private String extractFirstBindingMessage(BindException e) {
        return e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(msg -> msg != null && !msg.isBlank())
                .findFirst()
                .orElse("요청 값이 올바르지 않습니다.");
    }

    @ExceptionHandler({
            CannotAcquireLockException.class,
            PessimisticLockingFailureException.class
    })
    public ResponseEntity<ErrorResponse> handleLockException(Exception e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("요청이 동시에 처리 중입니다. 잠시 후 다시 시도해주세요."));
    }
}
