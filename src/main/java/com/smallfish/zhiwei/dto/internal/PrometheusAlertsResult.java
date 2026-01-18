package com.smallfish.zhiwei.dto.internal;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Prometheus API 通用响应包装类
 * 用于接收如 /api/v1/alerts 接口返回的完整 JSON 结构
 */
@Data
public class PrometheusAlertsResult {
    /**
     * API 响应状态
     * "success": 请求成功
     * "error": 请求失败
     */
    private String status;

    /**
     * 实际的数据载荷
     * 当 status 为 "success" 时，包含具体的告警列表数据
     */
    private AlertsData data;

    /**
     * 错误信息
     * 当 status 为 "error" 时，此字段包含具体的错误描述
     */
    private String error;

    /**
     * 错误类型
     * 当 status 为 "error" 时，此字段标识错误的分类（如 "bad_data", "timeout" 等）
     */
    private String errorType;

    /**
     * 内部静态类，用于封装 data 字段的具体结构
     * Prometheus 的 data 字段下通常还有一个 alerts 数组
     */
    @Data
    public static class AlertsData {
        /**
         * 告警列表
         * 包含所有查询到的 PrometheusAlert 对象
         * 初始化为空列表防止空指针
         */
        private List<PrometheusAlert> alerts = new ArrayList<>();
    }
}
