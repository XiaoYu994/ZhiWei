package com.smallfish.zhiwei.agent.tool;

import cn.hutool.core.io.IORuntimeException;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.smallfish.zhiwei.dto.resp.PrometheusResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Prometheus 监控指标查询工具
 * 核心能力：执行 PromQL 查询，支持趋势分析。
 */
@Slf4j
@Component
public class PrometheusQueryTools implements AgentTools{

    @Value("${prometheus.endpoint}")
    private String prometheusEndpoint;

    @Value("${prometheus.timeout:10}") // 增加默认值防止配置缺失报错
    private Integer timeoutSeconds;

    // 新增：Mock 开关，方便本地测试
    @Value("${prometheus.mock:false}")
    private boolean mockEnabled;

    // 修复：补全缺失的时间格式化常量
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());

    @Tool(description = "查询 Prometheus 监控数据。用于获取 CPU、内存、QPS 等指标的历史趋势。")
    public String queryPrometheus(
            @ToolParam(description = "PromQL 查询语句，例如 'up' 或 'sum(rate(http_requests_total[5m]))'") String query,
            @ToolParam(description = "查询的时间范围（分钟），默认为 30 分钟", required = false) Integer range
    ) {
        // 参数校验与默认值处理
        if (range == null || range <= 0) {
            range = 30;
        }

        // 1. Mock 逻辑介入
        if (mockEnabled) {
            log.info("Prometheus Mock 模式已开启，返回模拟数据: {}", query);
            return mockPrometheusResponse(query, range);
        }

        // 2. 计算时间窗口
        long now = Instant.now().getEpochSecond();
        long start = now - (range * 60L);

        // 3. 智能计算 step (步长)
        // 目标：限制返回的数据点数量在 20 个左右，避免 Token 爆炸
        // 例如：查 60 分钟，step = 60*60 / 20 = 180秒 (3分钟一个点)
        long step = Math.max(15, (range * 60) / 20);

        // 4. 构建 URL
        String url = String.format("%s/api/v1/query_range", prometheusEndpoint);
        Map<String, Object> params = new HashMap<>();
        params.put("query", query);
        params.put("start", start);
        params.put("end", now);
        params.put("step", step);

        log.info("执行 PromQL: {}, 范围: {}m, Step: {}s", query, range, step);

        // 5. 发起 HTTP GET 请求
        try (HttpResponse response = HttpRequest.get(url)
                .form(params)
                .timeout(timeoutSeconds * 1000) // 秒转毫秒
                .execute()) {

            // 先判断 HTTP 状态码 (防御性编程)
            if (!response.isOk()) {
                log.warn("Prometheus 返回非 200 状态码: {}", response.getStatus());
                return "查询失败: Prometheus 服务端返回错误 (Status: " + response.getStatus() + ")";
            }

            // 获取响应体
            String jsonResp = response.body();

            // 解析结果
            PrometheusResponseDTO respObj = JSONUtil.toBean(jsonResp, PrometheusResponseDTO.class);

            if (!"success".equals(respObj.getStatus())) {
                return "查询失败: Prometheus API 返回错误状态";
            }

            if (respObj.getData() == null || respObj.getData().getResult().isEmpty()) {
                return "查询成功，但在该时间段内未找到数据 (Empty Result)。请检查 PromQL 标签是否正确。";
            }

            return formatForAI(respObj, range);

        } catch (IORuntimeException e) {
            log.error("Prometheus 请求超时或网络异常", e);
            return "查询失败: 请求超时 (" + timeoutSeconds + "秒) 或网络不可达 - " + prometheusEndpoint;
        } catch (Throwable e) {
            log.error("Prometheus 解析异常", e);
            return "查询执行出错: " + e.getMessage();
        }
    }

    /**
     * 模拟 Prometheus 响应
     */
    private String mockPrometheusResponse(String query, int range) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("查询成功 (Mock 数据, 过去 %d 分钟趋势):\n", range));
        sb.append(String.format("- 实例/标签: {\"instance\":\"mock-instance-01\", \"job\":\"mock-job\", \"query\":\"%s\"}\n", query));
        sb.append("  数据点(时间:数值): [");

        // 生成 10 个模拟数据点
        long now = Instant.now().getEpochSecond();
        long step = (range * 60L) / 10;
        List<String> points = new ArrayList<>();
        Random random = new Random();

        // 基础值：根据 query 内容猜测
        double baseValue = 50.0;
        if (query.contains("cpu")) baseValue = 80.0; // 模拟高 CPU
        if (query.contains("memory")) baseValue = 70.0;
        if (query.contains("error")) baseValue = 0.5;

        for (int i = 0; i < 10; i++) {
            long timestamp = now - ((9 - i) * step);
            String timeStr = TIME_FORMATTER.format(Instant.ofEpochSecond(timestamp));

            // 波动
            double value = baseValue + (random.nextDouble() * 10 - 5);
            if (value < 0) value = 0;

            // 格式化
            points.add(String.format("%s:%.2f", timeStr, value));
        }

        sb.append(String.join(", ", points));
        sb.append("]\n");
        sb.append("(注：这是模拟数据)");
        return sb.toString();
    }

    /**
     * 将复杂的 JSON 简化为 AI 易读的文本摘要
     */
    private String formatForAI(PrometheusResponseDTO resp, int range) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("查询成功 (过去 %d 分钟趋势):\n", range));

        for (PrometheusResponseDTO.MetricResult item : resp.getData().getResult()) {
            // 提取 metric 标签
            String tags = JSONUtil.toJsonStr(item.getMetric());
            sb.append(String.format("- 实例/标签: %s\n", tags));
            sb.append("  数据点(时间:数值): ["); // 提示 AI 格式是 时间:数值

            List<String> simplePoints = item.getValues().stream().map(v -> {
                // 1. 处理时间戳 (Prometheus 返回的是秒，可能是浮点数形式，转为 long)
                // 使用 BigDecimal 防止精度丢失或格式错误
                long timestampSeconds = new BigDecimal(v.get(0).toString()).longValue();

                // 2. 格式化时间 (秒 -> 毫秒 -> HH:mm)
                String timeStr = TIME_FORMATTER.format(Instant.ofEpochSecond(timestampSeconds));

                // 3. 处理数值 (保留2位小数)
                String value = v.get(1).toString();
                if (value.contains(".")) {
                    try {
                        double d = Double.parseDouble(value);
                        value = String.format("%.2f", d);
                    } catch (NumberFormatException ignored) {}
                }

                // 4. 返回 "时间:数值" 格式 (例如 14:05:95.5)
                // 这种格式对 AI 来说既省 Token 又清晰
                return timeStr + ":" + value;
            }).collect(Collectors.toList());

            // 拼接
            sb.append(String.join(", ", simplePoints));
            sb.append("]\n");
        }
        sb.append("(注：数据格式为 HH:mm:数值，已按趋势降采样)");
        return sb.toString();
    }
}