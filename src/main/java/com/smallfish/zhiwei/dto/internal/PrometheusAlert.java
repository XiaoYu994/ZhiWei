package com.smallfish.zhiwei.dto.internal;

import lombok.Data;

import java.util.Map;

/**
 * Prometheus 单个告警详情实体类
 * 对应 Prometheus API 返回结果中 alerts 列表里的每一项
 */
@Data
public class PrometheusAlert {
    /**
     * 告警标签集合
     * 包含告警的标识信息，如 alertname, instance, job, severity 等
     * 例如: {"alertname": "HighCPU", "instance": "localhost:8080"}
     */
    private Map<String, String> labels;

    /**
     * 告警注解集合
     * 包含告警的描述性信息，如 summary, description, runbook_url 等
     * 通常用于展示给人看的详细说明
     */
    private Map<String, String> annotations;

    /**
     * 告警当前状态
     * 常见值:
     * - "firing": 正在触发中
     * - "pending": 处于等待期（未满足持续时间）
     * - "inactive": 未触发（通常 API 默认不返回 inactive 的告警，除非特定查询）
     */
    private String state;

    /**
     * 告警触发时间
     * 格式通常为 ISO 8601 时间戳，例如: "2026-01-18T12:00:00Z"
     */
    private String activeAt;

    /**
     * 告警计算时的具体数值
     * 例如: "1.234e+02" 或 "85.5%"，表示触发告警时的指标值
     */
    private String value;
}
