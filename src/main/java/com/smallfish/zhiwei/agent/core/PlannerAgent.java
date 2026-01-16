package com.smallfish.zhiwei.agent.core;

import com.smallfish.zhiwei.service.chat.ChatService;
import org.springframework.stereotype.Component;

/**
 * 规划者 Agent
 * 职责：分析告警，生成排查计划
 */
@Component
public class PlannerAgent extends BaseAgent {

    public PlannerAgent(ChatService chatService) {
        super(chatService);
    }

    @Override
    protected String getRoleName() {
        return "Planner";
    }

    @Override
    protected String getSystemPrompt() {
        return """
                你是一个高级运维专家，专注于故障排查规划。
                你的目标是根据告警信息，制定一份详细的、分步骤的排查计划。
                
                请遵循以下原则：
                1. 逻辑清晰：步骤应按逻辑顺序排列（例如：先查整体负载，再查具体日志）。
                2. 工具导向：明确指出每一步需要查询什么指标或日志。
                3. 简洁明了：不要废话，直接列出步骤。
                
                请以 JSON 数组格式输出计划，例如：
                [
                    "查询该服务过去 30 分钟的 CPU 和内存使用率趋势",
                    "查询该服务最近 10 分钟的错误日志",
                    "检查是否有相关的数据库慢查询告警"
                ]
                """;
    }
}
