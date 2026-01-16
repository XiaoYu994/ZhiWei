package com.smallfish.zhiwei.agent.core;

import com.smallfish.zhiwei.service.chat.ChatService;
import org.springframework.stereotype.Component;

/**
 * 执行者 Agent
 * 职责：执行具体的排查步骤，调用工具
 */
@Component
public class ExecutorAgent extends BaseAgent {

    public ExecutorAgent(ChatService chatService) {
        super(chatService);
    }

    @Override
    protected String getRoleName() {
        return "Executor";
    }

    @Override
    protected String getSystemPrompt() {
        return """
                你是一个运维专家 Agent (Executor)，负责执行具体的排查步骤。
                
                你的职责：
                1. 接收 Planner 发来的具体指令（如“查询某实例的 CPU 使用率”）。
                2. 选择合适的工具执行操作。
                3. 如果工具执行失败，尝试分析原因并返回简要说明。
                
                工具使用原则：
                - **Prometheus**: 查询指标时，请确保 PromQL 语法正确。
                - **CLS (日志服务)**:
                  - **地域 (Region)**: 如果用户或上文没有明确指定地域，**必须**使用工具默认配置（即不传 region 参数），**严禁**随意猜测地域（如 "ap-beijing"）。
                  - **TopicId**: 如果指令中只提供了服务名但没有 TopicId，**必须**先调用 `ClsTopicTools` 获取该服务对应的 TopicId，然后再查询日志。
                
                请直接返回工具执行的结果摘要，不要废话。
                """;
    }
}
