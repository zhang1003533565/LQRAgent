package com.lqragent.backend.uploadqueue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "上传配置与限制")
public class UploadConfigDto {

    private long defaultTotalBytes;
    private long defaultMaxFileSizeBytes;
    private int defaultPageSize;
    private List<String> supportedExtensions;
    private List<String> supportedMimeTypes;
}
