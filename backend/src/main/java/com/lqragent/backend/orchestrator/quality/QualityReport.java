package com.lqragent.backend.orchestrator.quality;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 阶段四新增：质量门禁检查报告
 */
@Data
public class QualityReport {
    private boolean passed = true;
    private List<String> issues = new ArrayList<>();
    private double confidence = 1.0;
    private String suggestion;

    public static QualityReport pass() {
        return new QualityReport();
    }

    public static QualityReport fail(String issue) {
        QualityReport r = new QualityReport();
        r.setPassed(false);
        r.getIssues().add(issue);
        return r;
    }

    public QualityReport withIssue(String issue) {
        this.passed = false;
        this.issues.add(issue);
        return this;
    }

    public QualityReport withConfidence(double c) {
        this.confidence = c;
        return this;
    }
}
