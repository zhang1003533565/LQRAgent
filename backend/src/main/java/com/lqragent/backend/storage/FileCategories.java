package com.lqragent.backend.storage;

/**
 * 文件分类工具 — 按扩展名分类到七牛云目录。
 * UploadQueueController 和 AdminController 共用。
 */
public final class FileCategories {

    private FileCategories() {}

    /**
     * 按文件扩展名分类到七牛云目录。
     */
    public static String categorize(String ext) {
        return switch (ext) {
            case "pdf" -> "documents/pdf";
            case "doc", "docx" -> "documents/word";
            case "ppt", "pptx" -> "documents/ppt";
            case "xls", "xlsx" -> "documents/excel";
            case "md", "txt", "rst" -> "documents/text";
            case "py", "java", "kt", "js", "ts", "go", "rs", "c", "cpp", "h" -> "code";
            case "png", "jpg", "jpeg", "gif", "webp", "svg" -> "images";
            case "mp4", "avi", "mov" -> "media/video";
            case "mp3", "wav", "ogg" -> "media/audio";
            case "json", "yaml", "yml", "toml", "xml", "csv" -> "data";
            default -> "documents/other";
        };
    }

    /**
     * 从文件名提取扩展名（小写，不含点）。
     */
    public static String extractExt(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "";
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }
}
