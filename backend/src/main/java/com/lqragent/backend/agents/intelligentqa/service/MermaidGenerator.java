package com.lqragent.backend.agents.intelligentqa.service;

import com.lqragent.backend.core.llm.LlmContentGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Mermaid 流程图生成器。
 * 根据用户问题和 LLM 回答，生成 Mermaid 流程图代码。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MermaidGenerator {

    private final LlmContentGenerator llmGenerator;

    /**
     * 生成 Mermaid 流程图代码。
     * @param question  用户问题
     * @param answer    LLM 回答文本
     * @return Mermaid 代码（graph TD 格式），失败返回 null
     */
    public String generate(String question, String answer) {
        String input = "问题：" + question + "\n回答：" + answer;
        if (input.length() > 3000) input = input.substring(0, 3000);

        String result = llmGenerator.generate("mermaid", "流程图生成", input);
        if (result != null) {
            // 清理冗余标记
            result = result.replace("```mermaid", "").replace("```", "").trim();
            log.info("[MermaidGenerator] 生成成功, len={}", result.length());
        } else {
            log.warn("[MermaidGenerator] 生成失败（LLM 返回空）");
        }
        return result;
    }
}
