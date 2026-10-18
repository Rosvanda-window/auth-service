package com.webblock.auth.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * One error type for the whole service.
 * status  -> the HTTP status to return (400, 401, 404, 409 ...)
 * code    -> a short machine-readable code the frontend can check (OTP_EXPIRED ...)
 * message -> a human-readable text
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException unauthorized(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
    }

    public static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
