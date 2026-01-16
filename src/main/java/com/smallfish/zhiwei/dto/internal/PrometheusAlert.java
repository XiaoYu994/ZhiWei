package com.smallfish.zhiwei.dto.internal;

import lombok.Data;

import java.util.Map;

/**
 * Prometheus 原始告警对象 (内部使用)
 */
@Data
public class PrometheusAlert {
    private Map<String, String> labels;
    private Map<String, String> annotations;
    private String state;
    private String activeAt;
    private String value;
}
