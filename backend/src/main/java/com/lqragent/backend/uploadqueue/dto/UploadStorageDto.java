package com.lqragent.backend.uploadqueue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "用户上传存储用量")
public class UploadStorageDto {

    private long usedBytes;
    private long totalBytes;
    private int fileCount;
    private long maxFileSizeBytes;
    private List<String> supportedMimeTypes;
}
