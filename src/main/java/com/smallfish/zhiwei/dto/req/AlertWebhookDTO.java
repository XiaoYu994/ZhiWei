package com.smallfish.zhiwei.dto.req;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AlertWebhookDTO {
    /*
     *  状态 "firing" 或 "resolved"
     * */
    private String status;
    private List<Alert> alerts;

    @Data
    public static class Alert {
        private String status;
        /*
         *  关键信息：alertname, instance, job
         * */
        private Map<String, String> labels;

        /*
         *   描述信息：summary, description
         * */
        private Map<String, String> annotations;
        private String startsAt;
    }
}
