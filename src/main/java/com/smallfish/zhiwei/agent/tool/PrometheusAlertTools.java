package com.smallfish.zhiwei.agent.tool;

import cn.hutool.core.date.DateUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.smallfish.zhiwei.dto.internal.PrometheusAlert;
import com.smallfish.zhiwei.dto.internal.PrometheusAlertsResult;
import com.smallfish.zhiwei.dto.resp.PrometheusAlertDTO;
import com.smallfish.zhiwei.dto.resp.PrometheusAlertsRespDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Prometheus 告警查询工具
 * 用于查询 Prometheus 的活动告警信息
 */
@Slf4j
@Component
public class PrometheusAlertTools implements AgentTools {

    @Value("${prometheus.endpoint}")
    private String prometheusEndpoint;

    @Value("${prometheus.timeout:10}")
    private int timeoutSeconds;

    @Value("${prometheus.mock-enabled:false}")
    private boolean mockEnabled;

    /**
     * 查询 Prometheus 活动告警
     * 该工具从 Prometheus 告警系统检索所有当前活动/触发的告警，包括标签、注释、状态和值
     */
    @Tool(description = "查询 Prometheus 告警系统中的活动告警。此工具会检索当前所有活动/触发的告警，包括其标签、注释、状态和值。当你需要检查当前有哪些告警正在触发、调查告警条件或监控告警状态时，请使用此工具。")
    public String queryPrometheusAlerts() {
        log.info("开始查询 Prometheus 活动告警, Mock模式: {}", mockEnabled);

        try {
            List<PrometheusAlertDTO> simplifiedAlerts;

            if (mockEnabled) {
                // Mock 模式：返回与文档关联的模拟告警数据
                simplifiedAlerts = buildMockAlerts();
                log.info("使用 Mock 数据，返回 {} 个模拟告警", simplifiedAlerts.size());
            } else {
                // 真实模式：调用 Prometheus Alerts API
                PrometheusAlertsResult result = fetchPrometheusAlerts();

                if (!"success".equals(result.getStatus())) {
                    return buildErrorResponse("Prometheus API 返回非成功状态: " + result.getStatus(), result.getError());
                }

                // 转换为简化格式，对于相同的 alertname，只保留第一个
                Set<String> seenAlertNames = new HashSet<>();
                simplifiedAlerts = new ArrayList<>();

                if (result.getData() != null && result.getData().getAlerts() != null) {
                    for (PrometheusAlert alert : result.getData().getAlerts()) {
                        String alertName = alert.getLabels().get("alertname");

                        // 如果这个 alertname 已经存在，跳过
                        if (seenAlertNames.contains(alertName)) {
                            continue;
                        }

                        // 标记为已见过
                        seenAlertNames.add(alertName);

                        PrometheusAlertDTO simplified = new PrometheusAlertDTO();
                        simplified.setAlertName(alertName);
                        simplified.setDescription(alert.getAnnotations().getOrDefault("description", ""));
                        simplified.setState(alert.getState());
                        simplified.setActiveAt(alert.getActiveAt());
                        simplified.setDuration(calculateDuration(alert.getActiveAt()));

                        simplifiedAlerts.add(simplified);
                    }
                }
            }

            // 构建成功响应
            PrometheusAlertsRespDTO output = new PrometheusAlertsRespDTO();
            output.setSuccess(true);
            output.setAlerts(simplifiedAlerts);
            output.setMessage(String.format("成功检索到 %d 个活动告警", simplifiedAlerts.size()));

            String jsonResult = JSONUtil.toJsonPrettyStr(output);
            log.info("Prometheus 告警查询完成: 找到 {} 个告警", simplifiedAlerts.size());

            return jsonResult;

        } catch (Exception e) {
            log.error("查询 Prometheus 告警失败", e);
            return buildErrorResponse("查询失败", e.getMessage());
        }
    }

    /**
     * 构建 Mock 告警数据
     */
    private List<PrometheusAlertDTO> buildMockAlerts() {
        List<PrometheusAlertDTO> alerts = new ArrayList<>();
        Instant now = Instant.now();

        // 告警1: CPU使用率过高 - 持续约25分钟
        PrometheusAlertDTO cpuAlert = new PrometheusAlertDTO();
        cpuAlert.setAlertName("HighCPUUsage");
        cpuAlert.setDescription("服务 payment-service 的 CPU 使用率持续超过 80%，当前值为 92%。" +
                "实例: pod-payment-service-7d8f9c6b5-x2k4m，命名空间: production");
        cpuAlert.setState("firing");
        Instant cpuActiveAt = now.minus(25, ChronoUnit.MINUTES);
        cpuAlert.setActiveAt(cpuActiveAt.toString());
        cpuAlert.setDuration(calculateDuration(cpuActiveAt.toString()));
        alerts.add(cpuAlert);

        // 告警2: 内存使用率过高 - 持续约15分钟
        PrometheusAlertDTO memoryAlert = new PrometheusAlertDTO();
        memoryAlert.setAlertName("HighMemoryUsage");
        memoryAlert.setDescription("服务 order-service 的内存使用率持续超过 85%，当前值为 91%。" +
                "JVM堆内存使用: 3.8GB/4GB，可能存在内存泄漏风险。" +
                "实例: pod-order-service-5c7d8e9f1-m3n2p，命名空间: production");
        memoryAlert.setState("firing");
        Instant memoryActiveAt = now.minus(15, ChronoUnit.MINUTES);
        memoryAlert.setActiveAt(memoryActiveAt.toString());
        memoryAlert.setDuration(calculateDuration(memoryActiveAt.toString()));
        alerts.add(memoryAlert);

        // 告警3: 响应时间过长 - 持续约10分钟
        PrometheusAlertDTO slowAlert = new PrometheusAlertDTO();
        slowAlert.setAlertName("SlowResponse");
        slowAlert.setDescription("服务 user-service 的 P99 响应时间持续超过 3 秒，当前值为 4.2 秒。" +
                "受影响接口: /api/v1/users/profile, /api/v1/users/orders。" +
                "可能原因：数据库慢查询或下游服务延迟");
        slowAlert.setState("firing");
        Instant slowActiveAt = now.minus(10, ChronoUnit.MINUTES);
        slowAlert.setActiveAt(slowActiveAt.toString());
        slowAlert.setDuration(calculateDuration(slowActiveAt.toString()));
        alerts.add(slowAlert);

        return alerts;
    }

    /**
     * 从 Prometheus API 获取告警数据
     */
    private PrometheusAlertsResult fetchPrometheusAlerts() {
        String apiUrl = prometheusEndpoint + "/api/v1/alerts";
        log.debug("请求 Prometheus API: {}", apiUrl);

        try (HttpResponse response = HttpRequest.get(apiUrl)
                .timeout(timeoutSeconds * 1000)
                .execute()) {

            if (!response.isOk()) {
                throw new RuntimeException("HTTP 请求失败: " + response.getStatus());
            }

            String responseBody = response.body();
            return JSONUtil.toBean(responseBody, PrometheusAlertsResult.class);
        }
    }

    /**
     * 计算从 activeAt 到现在的持续时间
     */
    private String calculateDuration(String activeAtStr) {
        try {
            // Prometheus 返回的时间格式通常是 ISO8601
            Instant activeAt = DateUtil.parse(activeAtStr).toInstant();
            Duration duration = Duration.between(activeAt, Instant.now());

            long hours = duration.toHours();
            long minutes = duration.toMinutes() % 60;
            long seconds = duration.getSeconds() % 60;

            if (hours > 0) {
                return String.format("%dh%dm%ds", hours, minutes, seconds);
            } else if (minutes > 0) {
                return String.format("%dm%ds", minutes, seconds);
            } else {
                return String.format("%ds", seconds);
            }
        } catch (Exception e) {
            log.warn("解析时间失败: {}", activeAtStr);
            return "unknown";
        }
    }

    /**
     * 构建错误响应
     */
    private String buildErrorResponse(String message, String error) {
        PrometheusAlertsRespDTO output = new PrometheusAlertsRespDTO();
        output.setSuccess(false);
        output.setMessage(message);
        output.setError(error);
        return JSONUtil.toJsonPrettyStr(output);
    }
}
