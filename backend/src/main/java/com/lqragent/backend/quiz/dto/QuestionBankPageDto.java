package com.lqragent.backend.quiz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "题目分页结果")
public class QuestionBankPageDto {

    @Schema(description = "列表数据")
    private List<QuestionBankListItemDto> items;

    @Schema(description = "当前页，从1开始")
    private int page;

    @Schema(description = "每页条数")
    private int size;

    @Schema(description = "总条数")
    private long total;

    @Schema(description = "总页数")
    private int totalPages;
}
