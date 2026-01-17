package com.smallfish.zhiwei.service.chat;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent;
import com.smallfish.zhiwei.agent.tool.ClsLogQueryTools;
import com.smallfish.zhiwei.agent.tool.ClsTopicTools;
import com.smallfish.zhiwei.agent.tool.PrometheusQueryTools;
import com.smallfish.zhiwei.config.AiOpsPromptConfig;
import com.smallfish.zhiwei.dto.req.AlertWebhookDTO;
import com.smallfish.zhiwei.service.base.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    private final PrometheusQueryTools prometheusTools;
    private final ClsLogQueryTools clsTools;
    private final ClsTopicTools clsTopicTools;

    // Graph 模式配置
    private static final String LOCK_KEY_PREFIX = "zhiwei:ops:graph:lock:";

    @Async("aiTaskExecutor")
    public void processAlertGraphAsync(AlertWebhookDTO.Alert alert) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);

        // 1. 元数据提取
        String alertName = alert.getLabels().getOrDefault("alertname", "UnknownAlert");
        String tenantId = alert.getLabels().getOrDefault("tenant_id", "default");
        String instance = alert.getLabels().getOrDefault("instance", "UnknownInstance");
        String severity = alert.getLabels().getOrDefault("severity", "warning");
        String region = alert.getLabels().getOrDefault("region", "UnknownRegion");
        String description = alert.getAnnotations().getOrDefault("description", "暂无描述");

        // 2. 【R】Redis 告警收敛 (PER 架构核心)
        String lockKey = String.format("%s%s:%s:%s", LOCK_KEY_PREFIX, tenantId, alertName, instance);
        Boolean isLocked = redisTemplate.opsForValue().setIfAbsent(lockKey, "RUNNING", Duration.ofMinutes(15));

        if (Boolean.FALSE.equals(isLocked)) {
            log.info("[{}]  Graph 模式告警收敛：{} 锁定中，跳过", traceId, lockKey);
            return;
        }

        log.info("[{}]  启动 Graph 编排 | 告警: {}", traceId, alertName);

        try {
            // 3. 构建 Graph 节点 (Agents)

            // --- Node A: Planner (大脑) ---
            // 职责：只负责思考，不给任何工具，或者只给查文档的工具
            ReactAgent plannerNode = ReactAgent.builder()
                    .name("Planner")
                    .description("负责分析现状，制定排查计划。")
                    .model(chatModel)
                    .systemPrompt(promptConfig.getPlannerPrompt())
                    .build();

            // --- Node B: Executor (手脚) ---
            // 职责：拥有重型工具，负责执行具体指令
            ReactAgent executorNode = ReactAgent.builder()
                    .name("Executor")
                    .description("负责执行具体的查询任务。")
                    .model(chatModel)
                    .systemPrompt(promptConfig.getExecutorPrompt())
                    .tools(ToolCallbacks.from(prometheusTools, clsTools,clsTopicTools))
                    .build();

            // --- Router: Supervisor (大管家) ---
            // 职责：决定下一个是谁，或者结束
            SupervisorAgent supervisor = SupervisorAgent.builder()
                    .name("Supervisor")
                    .model(chatModel)
                    .subAgents(List.of(plannerNode, executorNode))
                    .systemPrompt(promptConfig.getSupervisorPrompt())
                    .build();

            // 4. 构造初始输入
            String inputContext = buildAlertContext(alertName, instance, severity, description, region);

            // 5. 启动图流转 (Invoke)
            // 这行代码会触发：Supervisor -> Planner -> Executor -> ... -> Supervisor -> FINISH
            Optional<OverAllState> result = supervisor.invoke(inputContext);

            // 6. 处理最终结果
            if (result.isPresent()) {
                OverAllState state = result.get();

                // "messages" 是 Graph 框架默认存储对话历史的 key
                Optional<List> messagesOpt = state.value("messages", List.class);

                if (messagesOpt.isPresent()) {
                    List<?> rawMessages = messagesOpt.get();
                    if (!rawMessages.isEmpty()) {
                        // 获取最后一条消息
                        Object lastObj = rawMessages.get(rawMessages.size() - 1);

                        // 判断是否为 AssistantMessage (AI 的回复)
                        if (lastObj instanceof Message message) {
                            if (message instanceof AssistantMessage) {
                                String finalReport = message.getText();
                                String cleanReport = stripMarkdownTags(finalReport);

                                // 重新组装正文
                                String title = " AI 诊断报告: " + alertName;
                                String finalBody = String.format("# %s\n\n> TraceID: %s\n\n%s",
                                        title, traceId, cleanReport);
                                // 发送通知
                                notificationService.sendMarkdown(title, finalBody);
                            }
                        }
                    }
                }
                log.info("[{}]  Graph 诊断完成", traceId);
            } else {
                log.warn("[{}] Graph 执行未返回有效结果", traceId);
            }

        } catch (Exception e) {
            log.error("[{}]  Graph 执行异常", traceId, e);
            notificationService.sendMarkdown("诊断失败", "AI Graph 中断: " + e.getMessage());
            // 异常释放锁
            redisTemplate.delete(lockKey);
        }
    }

    private String buildAlertContext(String name, String instance, String severity, String desc, String region) {
        return String.format(
                "【系统告警触发】\n" +
                        "告警名称：%s\n" +
                        "故障实例：%s\n" +
                        "所属地域：%s\n" +
                        "严重程度：%s\n" +
                        "详细描述：%s\n\n" +
                        "请开始排查。",
                name, instance, region, severity, desc
        );
    }

    /**
     * 辅助工具：剥离 AI 输出的 Markdown 代码块包裹
     * 防止钉钉把整个报告渲染成一个灰色的代码块
     */
    private String stripMarkdownTags(String content) {
        if (content == null) return "";
        String result = content.trim();

        // 去掉开头的 ```markdown 或 ```
        if (result.startsWith("```markdown")) {
            result = result.substring(11);
        } else if (result.startsWith("```")) {
            result = result.substring(3);
        }

        // 去掉结尾的 ```
        if (result.endsWith("```")) {
            result = result.substring(0, result.length() - 3);
        }

        return result.trim();
    }
}