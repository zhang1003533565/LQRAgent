package com.lqragent.backend.uploadqueue.support;

import java.util.List;

public final class UploadDefaults {

    public static final long DEFAULT_TOTAL_BYTES = 5L * 1024 * 1024 * 1024;
    public static final long DEFAULT_MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024;
    public static final int DEFAULT_PAGE_SIZE = 8;

    public static final List<String> SUPPORTED_EXTENSIONS = List.of(
            "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "md", "txt", "rst",
            "py", "java", "kt", "js", "ts", "go", "rs", "c", "cpp", "h",
            "png", "jpg", "jpeg", "gif", "webp", "svg",
            "mp4", "avi", "mov", "mp3", "wav", "ogg",
            "json", "yaml", "yml", "toml", "xml", "csv"
    );

    public static final List<String> SUPPORTED_MIME_HINTS = List.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "text/markdown",
            "image/*",
            "audio/*",
            "video/*"
    );

    private UploadDefaults() {}
}
