package org.triple.backend.group.exception;

import org.springframework.http.HttpStatus;
import org.triple.backend.global.error.ErrorCode;

public enum GroupErrorCode implements ErrorCode {

    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 그룹 입니다."),
    NOT_GROUP_OWNER(HttpStatus.FORBIDDEN, "그룹 수정/삭제 권한이 없습니다."),
    CONCURRENT_GROUP_UPDATE(HttpStatus.CONFLICT, "동시에 그룹 정보가 변경되었습니다. 다시 시도해주세요."),
    EXCEEDED_JOIN_NUMBER(HttpStatus.CONFLICT, "그룹 정원이 가득 찼습니다."),
    NOT_GROUP_MEMBER(HttpStatus.FORBIDDEN, "해당 그룹을 조회할 권한이 없습니다."),
    CANNOT_OWNER_DEMOTE_SELF(HttpStatus.FORBIDDEN, "그룹 주인은 스스로를 강등시킬 수 없습니다."),
    NOT_JOINED_MEMBER(HttpStatus.FORBIDDEN, "그룹 멤버가 아니거나 유효한 가입 상태가 아닙니다."),
    CANNOT_KICK_OWNER(HttpStatus.FORBIDDEN, "그룹 주인은 추방할 수 없습니다."),
    CANNOT_KICK_SELF(HttpStatus.FORBIDDEN, "자기 자신은 추방할 수 없습니다."),
    ALREADY_LEAVE_GROUP(HttpStatus.FORBIDDEN, "이미 탈퇴한 그룹입니다."),
    GROUP_OWNER_NOT_LEAVE(HttpStatus.FORBIDDEN, "그룹 주인은 탈퇴할 수 없습니다."),
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
