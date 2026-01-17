package com.smallfish.zhiwei.service.chat;

import cn.hutool.core.date.DateUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.smallfish.zhiwei.agent.core.ExecutorAgent;
import com.smallfish.zhiwei.agent.core.PlannerAgent;
import com.smallfish.zhiwei.agent.core.ReviewerAgent;
import com.smallfish.zhiwei.dto.req.AlertWebhookDTO;
import com.smallfish.zhiwei.service.base.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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
     * 核心排查逻辑 - 异步执行
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
     * 辅助方法：构建告警上下文文本
     */
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