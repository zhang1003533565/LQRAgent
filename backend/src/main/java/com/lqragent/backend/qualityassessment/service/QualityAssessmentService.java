package com.lqragent.backend.qualityassessment.service;

import com.lqragent.backend.resourcefacade.entity.ResourceItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 资源质量评估服务。
 * 校验生成内容的完整性，失败时返回降级建议。
 */
@Slf4j
@Service
public class QualityAssessmentService {

    /**
     * 评估资源质量。
     * @return true=合格 false=不合格
     */
    public boolean assess(ResourceItem item) {
        if (item == null) {
            log.warn("[QualityAssessment] 资源为空，不合格");
            return false;
        }

        // 1. 内容非空
        if (item.getContent() == null || item.getContent().isBlank()) {
            log.warn("[QualityAssessment] 内容为空: id={}, kpId={}", item.getId(), item.getKpId());
            return false;
        }

        // 2. 长度检查
        if (item.getContent().length() < 10) {
            log.warn("[QualityAssessment] 内容过短: id={}, len={}", item.getId(), item.getContent().length());
            return false;
        }

        // 3. 标题检查
        if (item.getTitle() == null || item.getTitle().isBlank()) {
            log.warn("[QualityAssessment] 标题为空: id={}", item.getId());
            return false;
        }

        // 4. 图片资源检查URL
        if ("ILLUSTRATION".equals(item.getResourceType()) || "VIDEO_CLIP".equals(item.getResourceType())) {
            if (item.getMediaUrl() == null || item.getMediaUrl().isBlank()) {
                log.warn("[QualityAssessment] 媒体URL为空: id={}, type={}", item.getId(), item.getResourceType());
                return false;
            }
        }

        return true;
    }

    /**
     * 生成降级内容。
     */
    public ResourceItem buildFallback(ResourceItem original, String reason) {
        original.setContent("【内容生成质量不合格 - " + reason + "】请稍后重试或联系管理员。");
        log.info("[QualityAssessment] 降级替换: id={}, reason={}", original.getId(), reason);
        return original;
    }
}
