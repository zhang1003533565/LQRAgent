package com.lqragent.backend.mediageneration.service;

import com.lqragent.backend.knowledgegraph.entity.KnowledgePoint;
import com.lqragent.backend.knowledgegraph.service.KnowledgeGraphService;
import com.lqragent.backend.mediageneration.dto.MediaResult;
import com.lqragent.backend.resourcefacade.entity.ResourceItem;
import com.lqragent.backend.resourcefacade.repository.ResourceItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 媒体生成服务。
 * P1 实现：mock 生成（返回占位URL），调用方调 ResourceFacadeService 写入资源。
 * 后续 P1→P4 替换为真实 AI 生图/生视频 API。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaGenerationService {

    private final ResourceItemRepository resourceRepo;
    private final KnowledgeGraphService kgService;

    /** 媒体文件本地存储目录 */
    private static final String MEDIA_DIR = "media/generated";

    /**
     * 为知识点生成媒体（生图）。
     * P1: 返回占位图，不做真实 API 调用。
     *
     * @param kpId  知识点ID
     * @param prompt 生成提示词（可选）
     * @return 媒体生成结果
     */
    @Transactional
    public MediaResult generate(String kpId, String prompt) {
        KnowledgePoint kp = kgService.getByKpId(kpId)
                .orElseThrow(() -> new IllegalArgumentException("知识点不存在: " + kpId));

        log.info("[MediaGeneration] generate: kp={}, prompt={}", kpId, prompt);

        // P1: 保存占位资源
        String placeholderUrl = "/api/media/placeholder_" + UUID.randomUUID().toString().substring(0, 8) + ".png";

        ResourceItem item = ResourceItem.builder()
                .kpId(kpId)
                .resourceType(ResourceItem.TYPE_ILLUSTRATION)
                .title(kp.getTitle() + " — 示意图")
                .content("<!-- AI 生成示意图 -->\n\n" + kp.getTitle() + " 概念示意图")
                .mediaUrl(placeholderUrl)
                .mediaMime("image/png")
                .generationPrompt(prompt != null ? prompt : kp.getTitle() + " 概念示意图")
                .build();
        item = resourceRepo.save(item);

        log.info("[MediaGeneration] 已生成: resourceId={}, url={}", item.getId(), placeholderUrl);

        return MediaResult.builder()
                .resourceId(item.getId())
                .kpId(kpId)
                .mediaUrl(placeholderUrl)
                .mediaMime("image/png")
                .prompt(item.getGenerationPrompt())
                .newlyCreated(true)
                .build();
    }

    /**
     * 获取媒体文件本地路径。
     * P1: 仅返回占位路径；真实实现会在 P3/P4 写入实际文件。
     */
    public Path getMediaPath(Long resourceId) {
        return resourceRepo.findById(resourceId)
                .map(item -> {
                    if (item.getMediaUrl() != null && !item.getMediaUrl().startsWith("PLACEHOLDER")) {
                        return Paths.get(MEDIA_DIR, String.valueOf(resourceId) + ".png");
                    }
                    return null;
                })
                .orElse(null);
    }
}
