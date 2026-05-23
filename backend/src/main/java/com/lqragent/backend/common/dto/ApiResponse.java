package com.lqragent.backend.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 统一 API 响应包装。
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "统一响应包装")
public class ApiResponse<T> {

    @Schema(description = "状态码，200 表示成功")
    private final int code;

    @Schema(description = "提示信息")
    private final String message;

    @Schema(description = "响应数据")
    private final T data;

    private ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(200, "success", data);
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(200, "success", null);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
