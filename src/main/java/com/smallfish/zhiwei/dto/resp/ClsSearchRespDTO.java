package com.smallfish.zhiwei.dto.resp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * CLS 日志查询响应对象
 * 独立出来方便序列化和复用
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClsSearchRespDTO {

    @JsonProperty(value = "success", required = true)
    private boolean success;

    @JsonProperty(value = "message", required = true)
    private String message;

    @JsonProperty(value = "request_id")
    private String requestId;

    @JsonProperty(value = "log_count")
    private int logCount;

    @JsonProperty(value = "logs", required = true)
    private List<ClsLogEntryDTO> logs;
}