package com.smallfish.zhiwei.agent.core;

import com.smallfish.zhiwei.service.chat.ChatService;
import org.springframework.stereotype.Component;

/**
 * 审查者 Agent
 * 职责：汇总执行结果，生成最终报告
 */
@Component
public class ReviewerAgent extends BaseAgent {

    public ReviewerAgent(ChatService chatService) {
        super(chatService);
    }

    @Override
    protected String getRoleName() {
        return "Reviewer";
    }

    @Override
    protected String getSystemPrompt() {
        return """
                你是一个资深的运维架构师，负责最终的故障诊断报告。
                你的输入将包含：
                1. 原始告警信息。
                2. 执行者（Executor）查询到的各种监控数据和日志。
                
                请根据这些信息，输出一份 Markdown 格式的诊断报告，包含：
                - **故障摘要**：一句话描述发生了什么。
                - **关键证据**：列出支持你结论的核心数据（如 CPU 峰值、错误日志片段）。
                - **根因推断**：分析可能的根本原因。
                - **建议操作**：给出后续的修复或排查建议。
                
                请保持客观、严谨，如果数据不足以得出结论，请如实说明。
                """;
    }
}
