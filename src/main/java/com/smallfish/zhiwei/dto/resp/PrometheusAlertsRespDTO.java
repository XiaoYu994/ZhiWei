package com.smallfish.zhiwei.dto.resp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Prometheus 告警查询响应 DTO
 */
@Data
public class PrometheusAlertsRespDTO {
    @JsonProperty("success")
    private boolean success;

    @JsonProperty("alerts")
    private List<PrometheusAlertDTO> alerts;

    @JsonProperty("message")
    private String message;

    @JsonProperty("error")
    private String error;
}
