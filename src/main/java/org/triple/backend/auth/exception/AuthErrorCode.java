package org.triple.backend.auth.exception;

import org.springframework.http.HttpStatus;
import org.triple.backend.global.error.ErrorCode;

public enum AuthErrorCode implements ErrorCode {

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증정보가 없거나 만료되었습니다."),
    FAILED_ISSUE_KAKAO_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "카카오 인증 토큰 발급을 실패했습니다."),
    FAILED_FIND_KAKAO_USER_INFO(HttpStatus.UNAUTHORIZED, "카카오 사용자 정보 조회를 실패했습니다."),
    UNSUPPORTED_OAUTH_PROVIDER(HttpStatus.UNAUTHORIZED, "지원하지 않는 프로바이더 입니다."),
    INVALID_CSRF_TOKEN(HttpStatus.FORBIDDEN, "CSRF 토큰이 유효하지 않습니다.");

    private HttpStatus status;
    private String message;

    AuthErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }

    @Override
    public String getMessage() {
        return message;
    }
}