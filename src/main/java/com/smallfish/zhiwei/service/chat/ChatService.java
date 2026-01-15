package com.smallfish.zhiwei.service.chat;

import com.smallfish.zhiwei.agent.tool.AgentTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 ChatClient + Advisors
 自动获取历史记录
 自动 获取 token 限制
 自动 RAG 检索
 */
@Slf4j
@Service
public class ChatService {

    private final ChatClient chatClient;

    public ChatService(ChatClient.Builder builder,
                       ChatMemory chatMemory,
                        List<AgentTools>  agentTools,
                       @Value("classpath:prompts/system-prompt.st") Resource systemPrompt) {

        // 2. 构建 Client
        this.chatClient = builder
                // A. 挂载你的 Prompt 文件 (Spring 会自动替换 {history_block} 等逻辑)
                .defaultSystem(systemPrompt)
                // B. 挂载你的旧工具 (自动处理 React/Function Calling)
                .defaultTools(agentTools.toArray())
                .defaultAdvisors(
                        // 注册记忆
                        MessageChatMemoryAdvisor.builder(chatMemory)
                                .conversationId(MessageWindowChatMemory.DEFAULT_CONVERSATION_ID)
                                .build()
                )
                .build();
    }

    /**
     * 同步阻塞调用 (等待所有结果生成完一次性返回)
     */
    public String executeChat(String question, String conversationId) {
        return chatClient.prompt()
                .user(question)
                // 动态传递参数
                .advisors(a -> a
                        .param(MessageWindowChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }

    /**
     * 流式调用 (逐字返回)
     * 适用于 SSE (Server-Sent Events)
     */
    public Flux<String> streamChat(String question, String conversationId) {
        return chatClient.prompt()
                .user(question)
                .advisors(a -> a
                        // 传入会话 ID，Spring AI 会在流结束后自动将完整对话存入内存
                        .param(MessageWindowChatMemory.CONVERSATION_ID, conversationId))
                .stream() //  核心区别：使用 stream()
                .content(); // 返回 Flux<String>
    }
}
