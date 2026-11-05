package com.batong.global.response;

import com.batong.global.error.ErrorCode;

public record ApiResponse<T>(boolean success, T data, Error error) {

    public record Error(String code, String message) {
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> fail(ErrorCode code) {
        return new ApiResponse<>(false, null, new Error(code.name(), code.getMessage()));
    }
}
