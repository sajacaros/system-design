package com.minidrive.common;

import java.time.Instant;

/**
 * Contract error body: { code, message, timestamp }.
 */
public record ErrorResponse(String code, String message, String timestamp) {
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, Instant.now().toString());
    }
}
