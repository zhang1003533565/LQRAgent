"""
LLM Generate Capability
========================

通用 LLM 文本生成 capability，替代 Java 端的 LlmContentGenerator。
通过 prompt_type 参数选择不同的 system prompt，调用 ai-server 的 LLM service 生成内容。

支持的 prompt_type：
- lesson: 讲义生成
- quiz: 练习题
- code_case: 代码示例
- mind_map: 思维导图
- extended_reading: 拓展阅读
- mermaid: 流程图
- factual_check: 事实校验
- weakness_analysis: 薄弱点分析
- profile_extract: 画像抽取
- path_sort: 路径排序
"""

from __future__ import annotations

import json
import logging
from typing import Any

from deeptutor.capabilities.request_contracts import get_capability_request_schema
from deeptutor.core.capability_protocol import BaseCapability, CapabilityManifest
from deeptutor.core.context import UnifiedContext
from deeptutor.core.stream_bus import StreamBus

logger = logging.getLogger(__name__)

SYSTEM_PROMPTS: dict[str, str] = {
    "lesson": (
        "你是一位 Python 编程课程的资深讲师。根据给定的知识点名称和描述，"
        "生成一份结构清晰的讲义。包含：学习目标、核心概念讲解、语法示例、"
        "注意事项、小结。使用 Markdown 格式，适当使用代码块。"
    ),
    "quiz": (
        "你是一位 Python 编程课程的出题老师。根据给定的知识点名称和描述，"
        "生成一套练习题。包含：1 道选择题（含4个选项）、1 道填空题、"
        "1 道编程题。使用 Markdown 格式。"
    ),
    "code_case": (
        "你是一位 Python 编程课程的讲师。根据给定的知识点名称和描述，"
        "生成一份可直接运行的代码示例。包含：用途说明、完整可运行代码、"
        "运行说明。使用 Markdown 格式，代码放在 ```python 代码块中。"
    ),
    "mind_map": (
        "你是一位 Python 编程课程的课程设计师。根据给定的知识点名称，"
        "生成一份 Markdown 列表格式的思维导图。以知识点名称为中心主题，"
        "列出 3-6 个子主题，每个子主题下列出 2-4 个具体要点。"
        "输出格式：\n- 中心主题\n  - 子主题1\n    - 要点1.1\n    - 要点1.2"
    ),
    "extended_reading": (
        "你是一位 Python 编程课程的教学顾问。根据给定的知识点名称，"
        "生成一份拓展阅读材料。包含：学习建议、推荐阅读方向、"
        "实践项目建议（2-3 个）、常见误区提醒。使用 Markdown 格式。"
    ),
    "mermaid": (
        "你是一位 Python 编程课程的讲师。根据用户的问题和回答，"
        "生成 Mermaid 流程图代码（graph TD 格式）。只输出 Mermaid 代码，不要解释。"
    ),
    "factual_check": (
        "你是一个内容质检员。检查以下教学内容是否有事实错误。"
        "如果发现明显错误，输出 \'FAIL: 原因\'；"
        "如果没有明显错误，输出 \'PASS\'。只输出一行。"
    ),
    "weakness_analysis": (
        "你是一个学习效果分析专家。根据学生的学习行为记录，分析薄弱知识点、学习效率、建议改进方向。"
        "输出格式：\n薄弱点：知识点A、知识点B\n学习效率：高/中/低\n建议：xxx"
    ),
    "profile_extract": (
        "你是一个学生画像分析助手。根据学生的对话记录，以 JSON 格式输出以下6个维度及知识点掌握增量：\n"
        "{\n"
        "  \"knowledge_base\": \"初学/基础/进阶/熟练\",\n"
        "  \"learning_goal\": \"目标描述\",\n"
        "  \"cognitive_style\": \"视觉型/听觉型/动手型/阅读型\",\n"
        "  \"weakness\": [\"易错点1\"],\n"
        "  \"learning_pace\": \"快/中/慢\",\n"
        "  \"interest\": \"兴趣方向\",\n"
        "  \"mastered_topics\": [\"学生明确说已掌握的知识点，如 for循环\"],\n"
        "  \"pending_topics\": [\"学生明确说未掌握/想学的知识点，如 装饰器\"]\n"
        "}\n只输出 JSON，不要解释。若对话提到「学过X但不懂Y」，必须把 X 放入 mastered_topics，Y 放入 pending_topics。"
    ),
    "path_sort": (
        "你是学习路径规划专家。根据学生画像和知识点列表，对路径做个性化排序。\n"
        "规则：\n"
        "1. 已掌握的知识点标记为 completed，放在最后或跳过\n"
        "2. 薄弱知识点优先安排（在对应前置之后尽快出现）\n"
        "3. 保持前置依赖关系不变（如果 A 是 B 的前置，A 必须在 B 之前）\n"
        "4. 根据学生水平调整难度递进节奏\n"
        "只输出排序后的 kp_id 列表（JSON 数组），不要解释。\n"
        "例如：[\"kp_intro\", \"kp_input_output\", \"kp_variables\"]"
    ),
}


def _build_user_prompt(prompt_type: str, params: dict[str, Any]) -> str:
    """根据 prompt_type 和参数构建 user prompt。"""
    title = params.get("title", "")
    description = params.get("description", title)
    content = params.get("content", "")
    input_text = params.get("input", "")
    behavior_data = params.get("behavior_data", "")
    dialog_summary = params.get("dialog_summary", "")
    kp_ids = params.get("kp_ids", [])
    profile_hint = params.get("profile_hint", "")

    if prompt_type == "lesson":
        return f"知识点名称：{title}\n知识点描述：{description}\n\n请生成一份完整的 Markdown 格式讲义。"
    elif prompt_type == "quiz":
        return f"知识点名称：{title}\n知识点描述：{description}\n\n请生成一套练习题（选择+填空+编程）。"
    elif prompt_type == "code_case":
        return f"知识点名称：{title}\n知识点描述：{description}\n\n请生成一份可运行的代码示例。"
    elif prompt_type == "mind_map":
        return f"知识点名称：{title}\n\n请以该名称为中心主题，生成 Markdown 列表格式的思维导图。"
    elif prompt_type == "extended_reading":
        return f"知识点名称：{title}\n知识点描述：{description}\n\n请生成拓展阅读材料。"
    elif prompt_type == "mermaid":
        return input_text
    elif prompt_type == "factual_check":
        return f"标题：{title}\n内容：{content}"
    elif prompt_type == "weakness_analysis":
        return behavior_data
    elif prompt_type == "profile_extract":
        return dialog_summary
    elif prompt_type == "path_sort":
        return (
            f"当前路径知识点列表（按拓扑序）：\n{kp_ids}\n\n"
            f"学生画像：\n{profile_hint or '无画像数据'}\n\n"
            "请对路径做个性化排序，输出排序后的 kp_id JSON 数组。"
        )
    else:
        return params.get("user_prompt", content or title)


class LlmGenerateCapability(BaseCapability):
    """通用 LLM 文本生成 capability。"""

    manifest = CapabilityManifest(
        name="llm_generate",
        description="通用 LLM 文本生成，支持 10 种 prompt 类型（讲义/题目/代码/思维导图/拓展阅读/流程图/质检/薄弱点/画像/路径排序）。",
        stages=["generating"],
        tools_used=[],
        cli_aliases=["llm"],
        request_schema=get_capability_request_schema("llm_generate"),
    )

    async def run(self, context: UnifiedContext, stream: StreamBus) -> None:
        from deeptutor.services.llm import complete, get_llm_config, get_token_limit_kwargs

        config = context.config_overrides
        prompt_type = str(config.get("prompt_type", "") or context.metadata.get("prompt_type", ""))
        if not prompt_type:
            prompt_type = context.user_message.strip() or "lesson"

        system_prompt = SYSTEM_PROMPTS.get(prompt_type)
        if system_prompt is None:
            await stream.error(
                f"Unknown prompt_type: {prompt_type}. Supported: {list(SYSTEM_PROMPTS.keys())}",
                source="llm_generate",
            )
            return

        params = dict(config.get("params", {}) or {})
        # Also allow params at top level of config
        for key in ("title", "description", "content", "input", "behavior_data",
                     "dialog_summary", "kp_ids", "profile_hint", "user_prompt"):
            if key in config and key not in params:
                params[key] = config[key]

        user_prompt = _build_user_prompt(prompt_type, params)

        llm_config = get_llm_config()
        completion_kwargs: dict[str, Any] = {}
        if getattr(llm_config, "model", None):
            completion_kwargs.update(get_token_limit_kwargs(llm_config.model, 4096))

        async with stream.stage("generating", source="llm_generate"):
            try:
                result = await complete(
                    prompt=user_prompt,
                    system_prompt=system_prompt,
                    model=llm_config.model,
                    api_key=llm_config.api_key,
                    base_url=llm_config.base_url,
                    api_version=getattr(llm_config, "api_version", None),
                    binding=getattr(llm_config, "binding", None),
                    **completion_kwargs,
                )
                if result and result.strip():
                    await stream.content(result, source="llm_generate", stage="generating")
                else:
                    await stream.error("LLM returned empty result", source="llm_generate")
            except Exception as exc:
                logger.error("llm_generate failed: %s", exc, exc_info=True)
                await stream.error(f"LLM call failed: {exc}", source="llm_generate")
