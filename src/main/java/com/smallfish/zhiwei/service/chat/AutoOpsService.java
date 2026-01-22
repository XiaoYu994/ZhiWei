package com.smallfish.zhiwei.service.chat;

import cn.hutool.core.date.DateUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.smallfish.zhiwei.agent.core.ExecutorAgent;
import com.smallfish.zhiwei.agent.core.PlannerAgent;
import com.smallfish.zhiwei.agent.core.ReviewerAgent;
import com.smallfish.zhiwei.common.enums.ChatEventType;
import com.smallfish.zhiwei.config.AiOpsPromptConfig;
import com.smallfish.zhiwei.dto.req.AlertWebhookDTO;
import com.smallfish.zhiwei.dto.resp.ChatRespDTO;
import com.smallfish.zhiwei.service.base.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 生产级 AI Ops 自动运维服务
 * 特性：全链路 PromptTemplate、Reactor 线程安全、Redis 分布式锁、统一工作流
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoOpsService {

    private final NotificationService notificationService;
    private final StringRedisTemplate redisTemplate;

    // Agents
    private final PlannerAgent plannerAgent;
    private final ExecutorAgent executorAgent;
    private final ReviewerAgent reviewerAgent;

    // ================== 资源文件注入 (彻底解耦 Prompt) ==================
    private final AiOpsPromptConfig promptConfig;


    // 常量
    private static final String LOCK_KEY_PREFIX = "zhiwei:ops:lock:";

    /**
     * 场景一：Webhook 异步告警处理 (后台任务)
     */
    @Async("aiTaskExecutor")
    public void processAlertAsync(AlertWebhookDTO.Alert alert) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        String alertName = alert.getLabels().getOrDefault("alertname", "Unknown");
        String instance = alert.getLabels().getOrDefault("instance", "Unknown");
        String tenantId = alert.getLabels().getOrDefault("tenant_id", "default");

        // 1. Redis 锁：告警收敛 (15分钟内同实例同告警只处理一次)
        String lockKey = String.format("%s%s:%s:%s", LOCK_KEY_PREFIX, tenantId, alertName, instance);
        Boolean isLocked = redisTemplate.opsForValue().setIfAbsent(lockKey, "PROCESSING", 15, TimeUnit.MINUTES);

        if (Boolean.FALSE.equals(isLocked)) {
            log.info("[{}] 告警收敛跳过: {}", traceId, lockKey);
            return;
        }

        try {
            // 2. 准备数据上下文 (Map)
            Map<String, Object> dataVars = new HashMap<>();
            dataVars.put("alertName", alertName);
            dataVars.put("instance", instance);
            dataVars.put("severity", alert.getLabels().getOrDefault("severity", "warning"));
            dataVars.put("description", alert.getAnnotations().getOrDefault("description", "无"));
            dataVars.put("region", alert.getLabels().getOrDefault("region", "Unknown"));

            // 3. 渲染“背景信息”字符串 (此时还是数据，不是指令)
            PromptTemplate fmtTemplate = new PromptTemplate(promptConfig.getFormatAlertContext());
            String contextString = fmtTemplate.render(dataVars);

            String conversationId = String.format("AUTO-%s-%s", traceId, DateUtil.today());

            // 4. 执行通用工作流
            runDiagnosticWorkflow(
                    traceId,
                    conversationId,
                    contextString,
                    // 过程回调：只打日志
                    (msg) -> log.info("[{}] {}", traceId, msg),
                    // 结果回调：发送钉钉/企微通知
                    (report) -> {
                        String title = "🤖 AI 诊断报告: " + alertName;
                        String body = String.format("### %s\n**TraceID**: `%s`\n\n%s", title, traceId, report);
                        notificationService.sendMarkdown(title, body);
                    }
            );

        } catch (Exception e) {
            log.error("[{}] 诊断异常", traceId, e);
            notificationService.sendMarkdown("诊断失败", "AI 中断: " + e.getMessage());
            redisTemplate.delete(lockKey); // 异常释放锁，允许重试
        }
    }

    /**
     * 场景二：流式 SSE 排查 (用户主动询问)
     */
    public Flux<ServerSentEvent<ChatRespDTO>> streamTroubleshooting(String query, String conversationId) {
        return Flux.create((FluxSink<ServerSentEvent<ChatRespDTO>> sink )-> {
            String traceId = UUID.randomUUID().toString().substring(0, 8);

            try {
                sendSse(sink, conversationId, "🔍 收到请求，正在启动 Multi-Agent 分析引擎...");

                // 1. 准备用户查询上下文
                Map<String, Object> vars = new HashMap<>();
                vars.put("query", query);
                PromptTemplate fmtTemplate = new PromptTemplate(promptConfig.getFormatQueryContext());
                String contextString = fmtTemplate.render(vars);

                // 2. 执行通用工作流
                runDiagnosticWorkflow(
                        traceId,
                        conversationId,
                        contextString,
                        // 过程回调：推送到前端
                        (msg) -> sendSse(sink, conversationId, msg + "\n"),
                        // 结果回调：这里传 null，因为我们在 workflow 内部会针对 review 阶段做特殊处理，
                        // 或者你也可以在这里统一发，但为了流式体验，workflow 内部控制更好。
                        null
                );

                sendDone(sink, conversationId);
                sink.complete();

            } catch (Exception e) {
                log.error("[{}] 流式排查异常", traceId, e);
                sendSse(sink, conversationId, "❌ 发生错误: " + e.getMessage());
                sendDone(sink, conversationId);
                sink.complete();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // =========================================================================
    // 🔥 核心通用工作流 (Template Method Pattern)
    // =========================================================================
    private void runDiagnosticWorkflow(String traceId, String conversationId,
                                       String safeContextString,
                                       Consumer<String> stepCallback,
                                       Consumer<String> finalReportCallback) {

        // --- Phase 1: Planner (规划) ---
        // 使用 PromptTemplate 加载指令
        PromptTemplate planTpl = new PromptTemplate(promptConfig.getPhasePlan());
        planTpl.add("context", safeContextString); // 注入背景

        String planJson = plannerAgent.chat(planTpl.render(), conversationId);
        JSONArray steps = safeParseJsonArray(planJson);

        if (steps.isEmpty()) throw new RuntimeException("Planner 未生成有效步骤");
        stepCallback.accept("📋 规划完成，共 " + steps.size() + " 个步骤。");

        // --- Phase 2: Execute (循环执行) ---
        StringBuilder historyBuffer = new StringBuilder(); // 纯文本历史，用于 Template 替换

        for (int i = 0; i < steps.size(); i++) {
            String step = steps.getStr(i);
            stepCallback.accept(String.format("👉 正在执行步骤 %d/%d: %s", i + 1, steps.size(), step));

            // 使用 PromptTemplate 加载执行指令
            PromptTemplate execTpl = new PromptTemplate(promptConfig.getPhaseExecute());
            execTpl.add("context", safeContextString);
            execTpl.add("history", historyBuffer.toString().isEmpty() ? "暂无历史" : historyBuffer.toString());
            execTpl.add("currentStep", step);

            String result = executorAgent.chat(execTpl.render(), conversationId);

            // 更新历史
            String record = String.format("**步骤 %d**: %s\n> 结果: %s\n\n", i + 1, step, result);
            historyBuffer.append(record);

            stepCallback.accept("✅ 结果: " + result);
        }

        // --- Phase 3: Review (总结) ---
        stepCallback.accept("📝 所有步骤执行完毕，正在生成最终报告...");

        PromptTemplate reviewTpl = new PromptTemplate(promptConfig.getPhaseReview());
        reviewTpl.add("context", safeContextString);
        reviewTpl.add("history", historyBuffer.toString());

        String finalPrompt = reviewTpl.render();

        // 区分逻辑：如果是后台任务，需要回调发通知；如果是流式，直接返回
        String report = reviewerAgent.chat(finalPrompt, conversationId);

        // 如果有最终回调（比如发钉钉），则调用
        if (finalReportCallback != null) {
            finalReportCallback.accept(report);
        } else {
            // 如果是流式，也通过 stepCallback 把报告推给前端
            stepCallback.accept(report);
        }
    }

    // --- 辅助方法 ---

    private void sendSse(reactor.core.publisher.FluxSink<ServerSentEvent<ChatRespDTO>> sink, String convId, String text) {
        sink.next(ServerSentEvent.<ChatRespDTO>builder()
                .event("message")
                .data(ChatRespDTO.builder().conversationId(convId).answer(text).type(ChatEventType.CONTENT.getValue()).build())
                .build());
    }

    private void sendDone(reactor.core.publisher.FluxSink<ServerSentEvent<ChatRespDTO>> sink, String convId) {
        sink.next(ServerSentEvent.<ChatRespDTO>builder()
                .event("message")
                .data(ChatRespDTO.builder().conversationId(convId).answer("").type(ChatEventType.DONE.getValue()).build())
                .build());
    }

    private JSONArray safeParseJsonArray(String text) {
        if (text == null) return new JSONArray();
        try {
            String clean = text.replaceAll("```json", "").replaceAll("```", "").trim();
            int s = clean.indexOf("[");
            int e = clean.lastIndexOf("]");
            if (s >= 0 && e > s) return JSONUtil.parseArray(clean.substring(s, e + 1));
        } catch (Exception ignored) {}
        return new JSONArray();
    }
}