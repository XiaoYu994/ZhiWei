package com.smallfish.zhiwei.dto.resp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Prometheus 告警信息 DTO
 */
@Data
public class PrometheusAlertDTO {
    @JsonProperty("alert_name")
    private String alertName;

    @JsonProperty("description")
    private String description;

    @JsonProperty("state")
    private String state;

    @JsonProperty("active_at")
    private String activeAt;

    @JsonProperty("duration")
    private String duration;
}
