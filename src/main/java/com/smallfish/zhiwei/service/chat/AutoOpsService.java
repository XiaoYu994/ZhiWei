package com.smallfish.zhiwei.service.chat;

import cn.hutool.core.date.DateUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.smallfish.zhiwei.agent.core.ExecutorAgent;
import com.smallfish.zhiwei.agent.core.PlannerAgent;
import com.smallfish.zhiwei.agent.core.ReviewerAgent;
import com.smallfish.zhiwei.common.enums.ChatEventType;
import com.smallfish.zhiwei.dto.req.AlertWebhookDTO;
import com.smallfish.zhiwei.dto.resp.ChatRespDTO;
import com.smallfish.zhiwei.service.base.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 生产级 AI Ops 自动运维服务
 * 架构：PER (Prometheus + CLS/Logs + Redis)
 * 模式：Multi-Agent (Supervisor -> Planner -> Executor -> Reviewer)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoOpsService {

    private final NotificationService notificationService;
    private final StringRedisTemplate redisTemplate;

    // 注入 Agents
    private final PlannerAgent plannerAgent;
    private final ExecutorAgent executorAgent;
    private final ReviewerAgent reviewerAgent;

    // 常量定义
    private static final long LOCK_EXPIRE_MINUTES = 15;
    private static final String LOCK_KEY_PREFIX = "zhiwei:ops:lock:";

    /**
     * 核心排查逻辑 - 异步执行 (Webhook 调用)
     */
    @Async("aiTaskExecutor")
    public void processAlertAsync(AlertWebhookDTO.Alert alert) {
        // 0. 生成 TraceId (全链路追踪)
        String traceId = UUID.randomUUID().toString().substring(0, 8);

        // 1. 提取元数据 & 处理默认值
        String alertName = alert.getLabels().getOrDefault("alertname", "UnknownAlert");
        String tenantId = alert.getLabels().getOrDefault("tenant_id", "default");
        String instance = alert.getLabels().getOrDefault("instance", "UnknownInstance");
        String severity = alert.getLabels().getOrDefault("severity", "warning");
        String region = alert.getLabels().getOrDefault("region", "UnknownRegion");
        String description = alert.getAnnotations().getOrDefault("description", "暂无描述");

        // 2. 【R】Redis 告警收敛 (分布式原子锁)
        // 逻辑：同一租户、同一实例、同一告警，在 15 分钟内只处理一次
        String lockKey = String.format("%s%s:%s:%s", LOCK_KEY_PREFIX, tenantId, alertName, instance);

        // SET key value NX EX 900 (原子操作，无需 synchronized)
        Boolean isLocked = redisTemplate.opsForValue().setIfAbsent(lockKey, "PROCESSING", LOCK_EXPIRE_MINUTES, TimeUnit.MINUTES);

        if (Boolean.FALSE.equals(isLocked)) {
            log.info("[{}]告警收敛生效：{} 锁定中，跳过本次 AI 分析", traceId, lockKey);
            // 这里可以做一个 Redis 计数器 increment，记录被拦截的次数，用于周报统计
            return;
        }

        log.info("[{}] AI 介入排查 | 租户: {} | 告警: {}", traceId, tenantId, alertName);

        try {
            // 3. 构建基础上下文 (Context)
            String alertContext = buildAlertContext(alertName, instance, severity, description, region);
            String conversationId = String.format("AUTO-%s-%s-%s", tenantId, instance, DateUtil.today());

            // ==================== Phase 1: Plan (规划) ====================
            log.info("[{}] 进入 Plan 阶段...", traceId);
            // 提示词增强：强制要求返回 JSON 格式
            String planPrompt = alertContext + "\n\n请作为高级 SRE，列出排查此问题的 2-4 个具体步骤。请仅返回纯 JSON 字符串数组，例如：[\"查询CPU使用率\", \"检索最近5分钟错误日志\"]";
            String planJsonRaw = plannerAgent.chat(planPrompt, conversationId);
            log.info("[{}] Planner 输出: {}", traceId, planJsonRaw);

            // 安全解析 JSON，防止 Markdown 符号导致崩溃
            JSONArray steps = safeParseJsonArray(planJsonRaw);

            if (steps.isEmpty()) {
                throw new RuntimeException("Planner 返回了空步骤，无法继续");
            }

            // ==================== Phase 2: Execute (执行) ====================
            log.info("[{}] 进入 Execute 阶段，共 {} 个步骤...", traceId, steps.size());

            StringBuilder executionHistory = new StringBuilder();
            executionHistory.append("###  自动化排查记录\n\n");

            for (int i = 0; i < steps.size(); i++) {
                String currentStep = steps.getStr(i);
                log.info("[{}] 执行步骤 {}/{}: {}", traceId, i + 1, steps.size(), currentStep);

                // 将上下文 + 历史记录传给 Executor
                // 如果不传 history，Executor 根本不知道上一步查到了什么（比如不知道具体的 PID 是多少）
                String executionPrompt = String.format(
                        "【当前告警】\n%s\n\n【已执行的历史信息】\n%s\n\n【当前任务】\n请执行步骤：%s\n请调用相应工具获取数据，并简要总结发现。",
                        alertContext,
                        executionHistory, // 传入历史
                        currentStep
                );
                String result = executorAgent.chat(executionPrompt, conversationId);

                // 记录结果
                executionHistory.append(String.format("**步骤 %d**: %s\n", i + 1, currentStep));
                executionHistory.append(String.format("> **结果**: %s\n\n", result));
            }

            // ==================== Phase 3: Review (总结) ====================
            log.info("[{}] 进入 Review 阶段...", traceId);

            String reviewPrompt = String.format(
                    "【原始告警】\n%s\n\n【排查全过程】\n%s\n\n请根据上述证据，生成一份 Markdown 格式的最终诊断报告。包含：1. 根因分析 2. 处理建议 3. 风险评估。",
                    alertContext,
                    executionHistory
            );

            String finalReport = reviewerAgent.chat(reviewPrompt, conversationId);

            // 4. 推送通知
            String title = " AI 诊断报告: " + alertName;
            String notifyMessage = "### " + title + "\n" +
                    "**时间**: " + DateUtil.now() + "\n" +
                    "**实例**: `" + instance + "`\n" +
                    "**TraceID**: `" + traceId + "`\n\n" +
                    "--- \n\n" +
                    finalReport;

            notificationService.sendMarkdown(title, notifyMessage);
            log.info("[{}]  诊断完成，通知已发送", traceId);

        } catch (Exception e) {
            log.error("[{}]  AI 诊断异常", traceId, e);

            // 异常处理：发送失败通知
            notificationService.sendMarkdown("诊断失败", "AI 排查中断 (TraceID: " + traceId + ")\n原因: " + e.getMessage());

            // 关键：发生异常时释放锁，允许稍后重试（否则要等15分钟）
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * 流式排查入口 (供 AiOpsController 调用)
     */
    public Flux<ServerSentEvent<ChatRespDTO>> streamTroubleshooting(String query, String conversationId) {
        return Flux.create(sink -> {
            String traceId = UUID.randomUUID().toString().substring(0, 8);
            log.info("[{}] 开始流式排查: {}", traceId, query);

            try {
                // 发送初始消息
                sink.next(buildSSEResponse(conversationId, "收到排查请求，正在使用Multi-Agent模式进行分析..."));

                String alertContext = "用户查询: " + query;

                // Phase 1: Plan
                String planPrompt = alertContext + "\n\n请作为高级 SRE，列出排查此问题的 2-4 个具体步骤。请仅返回纯 JSON 字符串数组，例如：[\"查询CPU使用率\", \"检索最近5分钟错误日志\"]";
                String planJsonRaw = plannerAgent.chat(planPrompt, conversationId);
                JSONArray steps = safeParseJsonArray(planJsonRaw);

                if (steps.isEmpty()) {
                    sink.next(buildSSEResponse(conversationId, "无法生成排查计划，请提供更多信息。"));
                    sink.complete();
                    return;
                }
                // Phase 2: Execute
                StringBuilder executionHistory = new StringBuilder();
                executionHistory.append("###  自动化排查记录\n\n");

                for (int i = 0; i < steps.size(); i++) {
                    // 强制延迟 1 秒，测试流式效果 (防止后端处理太快导致前端渲染看起来像一次性)
                    try { Thread.sleep(1000); } catch (InterruptedException e) {}

                    String currentStep = steps.getStr(i);
                    sink.next(buildSSEResponse(conversationId, String.format("正在执行步骤 %d/%d: %s", i + 1, steps.size(), currentStep)));

                    String executionPrompt = String.format(
                            "【当前问题】\n%s\n\n【已执行的历史信息】\n%s\n\n【当前任务】\n请执行步骤：%s\n请调用相应工具获取数据，并简要总结发现。",
                            alertContext,
                            executionHistory,
                            currentStep
                    );

                    String result = executorAgent.chat(executionPrompt, conversationId);

                    executionHistory.append(String.format("**步骤 %d**: %s\n", i + 1, currentStep));
                    executionHistory.append(String.format("> **结果**: %s\n\n", result));

                    sink.next(buildSSEResponse(conversationId, String.format("步骤 %d 完成: %s\n\n", i + 1, result)));
                }

                // Phase 3: Review
                sink.next(buildSSEResponse(conversationId, "所有步骤执行完毕，正在生成最终报告..."));

                String reviewPrompt = String.format(
                        "【原始问题】\n%s\n\n【排查全过程】\n%s\n\n请根据上述证据，生成一份 Markdown 格式的最终诊断报告。包含：1. 根因分析 2. 处理建议 3. 风险评估。",
                        alertContext,
                        executionHistory
                );

                StringBuilder finalReportBuilder = new StringBuilder();

                // 使用 CountDownLatch 或 blockLast 来保持同步逻辑 (因为外层是同步的流)
                // 或者直接利用 Flux 桥接
                reviewerAgent.streamChat(reviewPrompt, conversationId)
                        .doOnNext(token -> {
                            // 收到一个字，就发一个 SSE 事件
                            finalReportBuilder.append(token);
                            ChatRespDTO chunk = ChatRespDTO.builder()
                                    .conversationId(conversationId)
                                    .answer(token) // 每次只发一个字
                                    .type(ChatEventType.CONTENT.getValue())
                                    .build();

                            sink.next(ServerSentEvent.builder(chunk).build());
                        })
                        .doOnError(e -> log.error("Reviewer 流式生成异常", e))
                        .blockLast(); // 阻塞直到流结束，保证代码按顺序往下走

                // 记录完整报告用于后续存档（如果需要）
                String finalReport = finalReportBuilder.toString();

                // Send DONE signal
                sink.next(ServerSentEvent.<ChatRespDTO>builder()
                        .event("message")
                        .data(ChatRespDTO.builder()
                                .conversationId(conversationId)
                                .answer("")
                                .type(ChatEventType.DONE.getValue())
                                .build())
                        .build());

                sink.complete();

            } catch (Exception e) {
                log.error("[{}] 模式流式排查异常", traceId, e);
                sink.next(buildSSEResponse(conversationId, "诊断过程出现错误: " + e.getMessage()));
                sink.next(ServerSentEvent.<ChatRespDTO>builder()
                        .event("message")
                        .data(ChatRespDTO.builder()
                                .conversationId(conversationId)
                                .answer("")
                                .type(ChatEventType.DONE.getValue())
                                .build())
                        .build());
                sink.complete();
            }
        });
    }

    private ServerSentEvent<ChatRespDTO> buildSSEResponse(String conversationId, String content) {
        ChatRespDTO resp = ChatRespDTO.builder()
                .conversationId(conversationId)
                .answer(content)
                .type(ChatEventType.CONTENT.getValue())
                .build();

        return ServerSentEvent.<ChatRespDTO>builder()
                .event("message")
                .data(resp)
                .build();
    }

    private String buildAlertContext(String name, String instance, String severity, String desc, String region) {
        return String.format(
                "**告警快照**\n" +
                        "> 告警名称：%s\n" +
                        "> 故障实例：%s\n" +
                        "> 所属地域：%s\n" +
                        "> 严重程度：%s\n" +
                        "> 详细描述：%s",
                name, instance, region, severity, desc
        );
    }

    /**
     * 辅助方法：安全的 JSON 数组解析器
     * 能处理 LLM 返回的 Markdown 代码块 (```json ... ```)
     */
    private JSONArray safeParseJsonArray(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return new JSONArray();
        }
        try {
            // 1. 尝试直接解析
            return JSONUtil.parseArray(rawText);
        } catch (Exception e) {
            // 2. 失败则尝试清洗 Markdown 标记
            String cleanText = rawText;
            // 移除 ```json 和 ``` 标记
            if (cleanText.contains("```")) {
                cleanText = cleanText.replaceAll("```json", "").replaceAll("```", "");
            }

            // 3. 寻找 [ ] 边界
            int start = cleanText.indexOf("[");
            int end = cleanText.lastIndexOf("]");

            if (start != -1 && end != -1 && end > start) {
                String jsonPart = cleanText.substring(start, end + 1);
                return JSONUtil.parseArray(jsonPart);
            }

            log.warn("无法从 Agent 输出中提取 JSON 数组: {}", rawText);
            return new JSONArray();
        }
    }
}