package com.smallfish.zhiwei.controller;

import cn.hutool.core.collection.CollUtil;
import com.smallfish.zhiwei.common.result.Result;
import com.smallfish.zhiwei.dto.req.AlertWebhookDTO;
import com.smallfish.zhiwei.service.chat.AutoOpsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
/**
 * 自动运维入口：接收 Prometheus 告警 -> 触发 AI 诊断 -> 发送报告
 */
@Slf4j
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class AlertWebhookController {

    private final AutoOpsService autoOpsService;

    /**
     * 接收 Prometheus Alertmanager 的 Webhook
     */
    @PostMapping("/prometheus")
    public Result<String> receiveAlert(@RequestBody AlertWebhookDTO webhook) {
        if (!"firing".equals(webhook.getStatus()) || CollUtil.isEmpty(webhook.getAlerts())) {
            return Result.success("Skipped");
        }

        // 遍历告警，丢给异步线程池处理
        for (AlertWebhookDTO.Alert alert : webhook.getAlerts()) {
            autoOpsService.processAlertAsync(alert);
        }

        return Result.success("Accepted");
    }
}