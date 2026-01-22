package com.smallfish.zhiwei.service.chat;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent;
import com.smallfish.zhiwei.agent.tool.ClsLogQueryTools;
import com.smallfish.zhiwei.agent.tool.ClsTopicTools;
import com.smallfish.zhiwei.agent.tool.InternalDocsTools;
import com.smallfish.zhiwei.agent.tool.PrometheusQueryTools;
import com.smallfish.zhiwei.config.AiOpsPromptConfig;
import com.smallfish.zhiwei.dto.req.AlertWebhookDTO;
import com.smallfish.zhiwei.service.base.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.*;

/**
 * 生产级 AI Ops 自动运维服务 (Graph 模式版)
 * 架构：PER (Prometheus + CLS + Redis)
 * 模式：Graph Supervisor (Router -> [Planner, Executor])
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoOpsGraphService {

    private final NotificationService notificationService;
    private final StringRedisTemplate redisTemplate;
    private final DashScopeChatModel chatModel;
    private final AiOpsPromptConfig promptConfig;

    // 工具集注入
    private final PrometheusQueryTools prometheusTools;
    private final ClsLogQueryTools clsTools;
    private final ClsTopicTools clsTopicTools;
    private final InternalDocsTools internalDocsTools;

    private static final CompileConfig GRAPH_COMPILE_CONFIG = CompileConfig.builder()
            .recursionLimit(20)
            .build();
    private static final String STATE_KEY_MESSAGES = "messages";
    private static final String LOCK_KEY_PREFIX = "zhiwei:ops:graph:lock:";

    /**
     * 场景一：异步处理 Webhook 告警 (自动驾驶模式)
     * 特点：需要告警收敛 (Lock)，执行后通知
     */
    @Async("aiTaskExecutor")
    public void processAlertGraphAsync(AlertWebhookDTO.Alert alert) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);

        // 1. 提取元数据 (使用 Optional 避免空指针)
        String alertName = alert.getLabels().getOrDefault("alertname", "UnknownAlert");
        String instance = alert.getLabels().getOrDefault("instance", "UnknownInstance");
        String tenantId = alert.getLabels().getOrDefault("tenant_id", "default");

        // 2. Redis 告警收敛 (15分钟内同实例同告警只处理一次)
        String lockKey = String.format("%s%s:%s:%s", LOCK_KEY_PREFIX, tenantId, alertName, instance);
        // 如果已存在锁，直接返回（收敛）
        if (Boolean.FALSE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey, "RUNNING", Duration.ofMinutes(15)))) {
            log.info("[{}] 告警收敛生效，跳过: {}", traceId, lockKey);
            return;
        }

        log.info("[{}] 启动 Graph 编排 | 告警: {}", traceId, alertName);

        try {
            // 3. 构造 Graph (复用工厂方法)
            SupervisorAgent supervisor = buildGraphAgent();

            // 4. 构造输入上下文
            String inputContext = buildSafeAlertContext(alert, traceId);

            // 5. 执行 Graph
            Optional<OverAllState> result = supervisor.invoke(inputContext);

            // 6. 解析结果并通知
            String report = extractFinalReport(result.orElse(null));
            if (StringUtils.hasText(report)) {
                String title = "🤖 AI 诊断报告: " + alertName;
                String markdownBody = String.format("# %s\n\n> TraceID: %s\n\n%s", title, traceId, report);
                notificationService.sendMarkdown(title, markdownBody);
                log.info("[{}] 诊断成功", traceId);
            } else {
                log.warn("[{}] 诊断未生成有效内容", traceId);
            }

        } catch (Exception e) {
            log.error("[{}] Graph 执行异常", traceId, e);
            notificationService.sendMarkdown("诊断失败", "AI Graph 中断: " + e.getMessage());
            // 只有异常时才释放锁，允许重试；正常情况保持锁以进行收敛
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * 场景二：同步执行用户查询 (副驾驶模式)
     * 特点：不需要锁，直接返回结果
     */
    public Optional<OverAllState> executeAnalysis(String query) {
        try {
            log.info("Graph 开始执行用户查询: {}", query);

            // 1. 构造 Graph
            SupervisorAgent supervisor = buildGraphAgent();

            // 2. 构造 Context (这里建议用 PromptTemplate，暂时保持拼接)
            String inputContext = "用户查询: " + query + "\n\n请严格按照《告警分析报告》模板进行排查和输出。";

            // 3. 执行
            return supervisor.invoke(inputContext);

        } catch (Exception e) {
            log.error("Graph 执行失败", e);
            return Optional.empty();
        }
    }

    /**
     * 核心工厂方法：构建图结构
     * 解决代码重复问题
     */
    private SupervisorAgent buildGraphAgent() {
        // --- Node A: Planner ---
        ReactAgent plannerNode = ReactAgent.builder()
                .name("Planner")
                .description("负责分析现状，制定排查计划。")
                .model(chatModel)
                .systemPrompt(promptConfig.getPlannerPrompt())
                .build();

        // --- Node B: Executor ---
        ReactAgent executorNode = ReactAgent.builder()
                .name("Executor")
                .description("负责执行具体的查询任务。")
                .model(chatModel)
                .systemPrompt(promptConfig.getExecutorPrompt())
                // 注册所有工具
                .tools(ToolCallbacks.from(prometheusTools, clsTools, clsTopicTools, internalDocsTools))
                .build();

        // --- Router: Supervisor ---
        return SupervisorAgent.builder()
                .name("Supervisor")
                .model(chatModel)
                .subAgents(List.of(plannerNode, executorNode))
                .systemPrompt(promptConfig.getSupervisorPrompt())
                .compileConfig(GRAPH_COMPILE_CONFIG)
                .build();
    }

    /**
     * 统一结果解析器
     */
    public String extractFinalReport(OverAllState state) {
        if (state == null) return null;

        Optional<List> messagesOpt = state.value(STATE_KEY_MESSAGES, List.class);
        if (messagesOpt.isPresent()) {
            List<?> rawMessages = messagesOpt.get();
            // 倒序遍历，找到最后一条 AssistantMessage
            // 防止最后一条是 ToolMessage 导致的类型转换错误
            for (int i = rawMessages.size() - 1; i >= 0; i--) {
                Object msgObj = rawMessages.get(i);
                if (msgObj instanceof AssistantMessage am) {
                    String content = am.getText();
                    if (StringUtils.hasText(content)) {
                        return stripMarkdownTags(content);
                    }
                }
            }
        }
        return null;
    }

    private String buildSafeAlertContext(AlertWebhookDTO.Alert alert, String traceId) {
        // 1. 准备参数 Map
        Map<String, Object> vars = new HashMap<>();
        vars.put("traceId", traceId);
        vars.put("alertName", alert.getLabels().getOrDefault("alertname", "Unknown"));
        vars.put("instance", alert.getLabels().getOrDefault("instance", "Unknown"));
        vars.put("region", alert.getLabels().getOrDefault("region", "Unknown"));
        vars.put("severity", alert.getLabels().getOrDefault("severity", "warning"));
        vars.put("description", alert.getAnnotations().getOrDefault("description", "暂无描述"));

        // 2. 加载模板并渲染
        PromptTemplate template = new PromptTemplate(promptConfig.getAlertAnalysisResource());

        // render() 方法会执行变量替换，生成最终的字符串
        return template.render(vars);
    }

    private String stripMarkdownTags(String content) {
        if (content == null) return "";
        String result = content.trim();
        if (result.startsWith("```markdown")) result = result.substring(11);
        else if (result.startsWith("```json")) result = result.substring(7);
        else if (result.startsWith("```")) result = result.substring(3);

        if (result.endsWith("```")) result = result.substring(0, result.length() - 3);
        return result.trim();
    }
}