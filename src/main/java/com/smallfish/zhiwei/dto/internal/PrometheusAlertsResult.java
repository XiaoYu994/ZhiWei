package com.smallfish.zhiwei.dto.internal;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Prometheus API 响应结果 (内部使用)
 */
@Data
public class PrometheusAlertsResult {
    private String status;
    private AlertsData data;
    private String error;
    private String errorType;

    @Data
    public static class AlertsData {
        private List<PrometheusAlert> alerts = new ArrayList<>();
    }
}
