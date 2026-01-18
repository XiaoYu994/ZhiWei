package com.smallfish.zhiwei.agent.core;

import com.smallfish.zhiwei.service.chat.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * Agent 基类
 */
@Slf4j
@RequiredArgsConstructor
public abstract class BaseAgent {

    protected final ChatService chatService;

    /**
     * 获取 Agent 的角色名称
     */
    protected abstract String getRoleName();

    /**
     * 获取 Agent 的系统提示词 (System Prompt)
     */
    protected abstract String getSystemPrompt();

    /**
     * 执行 Agent 任务 同步执行
     * @param input 用户输入或上下文
     * @param conversationId 会话ID
     * @return Agent 的响应
     */
    public String chat(String input, String conversationId) {
        log.info("[{}] Agent 收到请求: {}", getRoleName(), input);
        String response = chatService.executeChat(input, conversationId, getSystemPrompt());
        log.info("[{}] Agent 响应完成", getRoleName());
        return response;
    }

    /**
     * 流式执行
     * 用于 ReviewerAgent 生成报告时展示打字机效果
     */
    public Flux<String> streamChat(String input, String conversationId) {
        log.info("[{}] Agent 开始流式请求: {}", getRoleName(), input);
        return chatService.streamChatContent(input, conversationId, getSystemPrompt())
                .doOnComplete(() -> log.info("[{}] Agent 流式响应结束", getRoleName()))
                .doOnError(e -> log.error("[{}] Agent 流式响应异常", getRoleName(), e));
    }
}
