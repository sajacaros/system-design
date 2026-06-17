package com.minidrive.common;

import org.springframework.http.HttpStatus;

/**
 * Contract error codes (api-contract.md §공통 규칙 / per-endpoint).
 */
public enum ErrorCode {
    // Common
    VALIDATION(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT(HttpStatus.CONFLICT),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),

    // Auth
    EMAIL_TAKEN(HttpStatus.CONFLICT),
    BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    INVALID_REFRESH(HttpStatus.UNAUTHORIZED),

    // Folder / File
    NAME_CONFLICT(HttpStatus.CONFLICT),
    CYCLIC_MOVE(HttpStatus.BAD_REQUEST),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND),
    FOLDER_NOT_FOUND(HttpStatus.NOT_FOUND),

    // Share (public endpoint)
    INVALID_LINK(HttpStatus.NOT_FOUND),
    EXPIRED(HttpStatus.GONE),
    DISABLED(HttpStatus.GONE);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
