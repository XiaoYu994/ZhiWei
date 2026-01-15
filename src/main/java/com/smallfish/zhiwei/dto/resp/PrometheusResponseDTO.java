package com.smallfish.zhiwei.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/*
*  Prometheus 返回实体
* */
@Data
@Builder
public class PrometheusResponseDTO {
    private String status;
    private DataResult data;

    @Data
    @Builder
    public static class DataResult {
        private String resultType;
        private List<MetricResult> result;
    }

    @Data
    @Builder
    public static class MetricResult {
        // 具体的指标标签，如 {instance="192.168.1.1", job="node"}
        private Object metric;
        // 数据点列表：[ [时间戳, "数值"], [时间戳, "数值"] ]
        private List<List<Object>> values;
    }
}
