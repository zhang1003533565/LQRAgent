package com.lqragent.backend.agents.learnerprofile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "学习画像导出结果")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileExportDto {

    @Schema(description = "导出格式：markdown | pdf")
    private String format;

    @Schema(description = "Markdown 正文（format=markdown 时返回）")
    private String content;

    @Schema(description = "建议下载文件名")
    private String fileName;
}
