package com.lqragent.backend.agents.qualityassessment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * 学术规范性检查器。
 * 检查：虚假引用、绝对化用语、不实陈述。
 */
@Slf4j
@Service
public class AcademicChecker {

    /** 假引用模式 */
    private static final Pattern FAKE_REF = Pattern.compile(
        "参考文献\\[\\d{3,}|引用于\\[\\d+\\]"
    );

    /** 绝对化用语 */
    private static final Pattern ABSOLUTE_WORDS = Pattern.compile(
        "(一定|绝对|肯定|永远|所有|任何|唯一|最好|最差|最佳|最坏|100%|百分之百)"
    );

    /** 疑似虚假引用证据 */
    private static final Pattern SUSPICIOUS_CITATION = Pattern.compile(
        "(据研究[^。]{0,10}$|有研究表明[^。]{0,10}$)"
    );

    /** 占位符/未完成标记 */
    private static final Pattern PLACEHOLDER = Pattern.compile(
        "(TODO|FIXME|待补充|待完善|占位|placeholder|\\.{3,}|……)"
    );

    /** 不安全代码模式（教学中不应出现的危险用法） */
    private static final Pattern UNSAFE_CODE = Pattern.compile(
        "(exec\\s*\\(|eval\\s*\\(|__import__\\s*\\(|os\\.system\\s*\\()"
    );

    public CheckResult check(String content) {
        if (content == null || content.isBlank()) return CheckResult.pass();

        if (FAKE_REF.matcher(content).find())
            return CheckResult.fail("内容包含虚假引用格式");
        if (ABSOLUTE_WORDS.matcher(content).find())
            return CheckResult.fail("包含绝对化用语（建议改为更严谨的表达）");
        if (SUSPICIOUS_CITATION.matcher(content).find())
            return CheckResult.fail("疑似虚假引用证据");
        if (PLACEHOLDER.matcher(content).find())
            return CheckResult.fail("内容包含占位符或未完成标记");
        if (UNSAFE_CODE.matcher(content).find())
            return CheckResult.fail("代码示例包含不安全的函数调用（exec/eval等）");
        return CheckResult.pass();
    }

    public record CheckResult(boolean passed, String reason) {
        public static CheckResult pass() { return new CheckResult(true, ""); }
        public static CheckResult fail(String reason) { return new CheckResult(false, reason); }
    }
}
