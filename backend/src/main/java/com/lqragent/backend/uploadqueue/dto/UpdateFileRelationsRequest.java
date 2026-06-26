package com.lqragent.backend.uploadqueue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "更新上传文件关联关系")
public class UpdateFileRelationsRequest {

    private String learningPathId;
    private List<String> knowledgePointIds;
    private List<String> tags;
}
