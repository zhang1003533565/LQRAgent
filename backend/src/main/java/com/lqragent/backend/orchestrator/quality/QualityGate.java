package com.lqragent.backend.orchestrator.quality;

import org.springframework.stereotype.Component;

import com.lqragent.backend.orchestrator.artifact.Artifact;
import com.lqragent.backend.orchestrator.artifact.ArtifactValidator;

import lombok.extern.slf4j.Slf4j;

/**
 * 阶段四新增：质量门禁
 * <p>
 * PipelineEngine 在每步执行后调用 check()，不通过触发重试
 * <p>
 * 当前版本只做轻量结构化校验，不调 LLM 深度检查（避免 Pipeline 整体延迟翻倍）
 * 后续可在此处集成 QualityAgent 做 LLM 语义质量评估
 */
@Slf4j
@Component
public class QualityGate {

    /**
     * 检查 Artifact 是否符合规格
     */
    public QualityReport check(Artifact artifact) {
        if (artifact == null) {
            return QualityReport.fail("Artifact 为空");
        }
        if (artifact.getKind() == null) {
            return QualityReport.fail("Artifact.kind 为空");
        }
        return switch (artifact.getKind()) {
            case IMAGE, VIDEO -> checkMedia(artifact);
            case QUIZ -> checkQuiz(artifact);
            case SUMMARY, TEXT -> checkText(artifact);
            case LEARNING_PATH -> checkLearningPath(artifact);
            default -> QualityReport.pass();
        };
    }

    private QualityReport checkMedia(Artifact a) {
        if (!ArtifactValidator.isMediaValid(a)) {
            return QualityReport.fail("媒体 URL 缺失或无效");
        }
        return QualityReport.pass().withConfidence(a.getConfidence());
    }

    private QualityReport checkQuiz(Artifact a) {
        if (!ArtifactValidator.isQuizValid(a)) {
            return QualityReport.fail("Quiz 缺少 questions 或为空");
        }
        return QualityReport.pass().withConfidence(a.getConfidence());
    }

    private QualityReport checkText(Artifact a) {
        if (a.getPayload() == null || a.getPayload().isEmpty()) {
            return QualityReport.fail("文本内容为空");
        }
        Object content = a.getPayload().get("content");
        if (content == null) {
            // 文本类 artifact 也接受 text 字段
            content = a.getPayload().get("text");
        }
        if (content == null || String.valueOf(content).length() < 10) {
            return QualityReport.fail("文本内容过短");
        }
        return QualityReport.pass();
    }

    private QualityReport checkLearningPath(Artifact a) {
        if (a.getPayload() == null) return QualityReport.fail("学习路径 payload 为空");
        Object nodes = a.getPayload().get("nodes");
        if (!(nodes instanceof java.util.List<?> list) || list.size() < 2) {
            return QualityReport.fail("学习路径节点数不足");
        }
        return QualityReport.pass();
    }
}
