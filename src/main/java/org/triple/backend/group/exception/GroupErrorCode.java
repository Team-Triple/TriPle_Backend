package org.triple.backend.group.exception;

import org.springframework.http.HttpStatus;
import org.triple.backend.global.error.ErrorCode;

public enum GroupErrorCode implements ErrorCode {

    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 그룹 입니다."),
    NOT_GROUP_OWNER(HttpStatus.FORBIDDEN, "그룹 수정/삭제 권한이 없습니다."),
    CONCURRENT_GROUP_UPDATE(HttpStatus.CONFLICT, "동시에 그룹 정보가 변경되었습니다. 다시 시도해주세요."),
    INVALID_SEARCH_KEYWORD_LENGTH(HttpStatus.BAD_REQUEST, "검색어는 최대 20자까지 입력할 수 있습니다.");

    private HttpStatus status;
    private String message;

    GroupErrorCode(HttpStatus status, String message) {
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
