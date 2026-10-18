package com.webblock.auth.exception;

import java.time.LocalDateTime;

/**
 * The JSON body every error returns. Example:
 * { "code": "OTP_INVALID", "message": "Wrong code. 4 tries left.", "timestamp": "..." }
 */
public record ErrorResponse(String code, String message, LocalDateTime timestamp) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, LocalDateTime.now());
    }
}
