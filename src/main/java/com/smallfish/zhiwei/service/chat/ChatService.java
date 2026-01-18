package com.smallfish.zhiwei.service.chat;

import com.smallfish.zhiwei.agent.tool.AgentTools;
import com.smallfish.zhiwei.common.enums.ChatEventType;
import com.smallfish.zhiwei.dto.resp.ChatRespDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
     * 同步阻塞调用 (支持自定义 System Prompt)
     */
    public String executeChat(String question, String conversationId, String systemPrompt) {
        return chatClient.prompt()
                .system(systemPrompt) // 覆盖默认 System Prompt
                .user(question)
                .advisors(a -> a
                        .param(MessageWindowChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }

    /**
     * 流式调用 (逐字返回)
     * 适用于 SSE (Server-Sent Events)
     */
    /**
     * 返回 Flux<ServerSentEvent> 以便前端区分是 "内容" 还是 "元数据"
     */
    public Flux<ServerSentEvent<ChatRespDTO>> streamChat(String question, String conversationId) {

        // 1. 核心对话流 (Type = CONTENT)
        Flux<ServerSentEvent<ChatRespDTO>> contentStream = chatClient.prompt()
                .user(question)
                .advisors(a -> a.param(MessageWindowChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .chatResponse()
                .filter(response -> response.getResult() != null && response.getResult().getOutput().getText() != null)
                .map(chatResponse -> {
                    String content = chatResponse.getResult().getOutput().getText();

                    // 构建 CONTENT 类型的 DTO
                    ChatRespDTO resp = ChatRespDTO.builder()
                            .conversationId(conversationId)
                            .answer(content)
                            .type(ChatEventType.CONTENT.getValue())
                            .build();

                    return ServerSentEvent.<ChatRespDTO>builder()
                            .event("message")
                            .data(resp)
                            .build();
                });

        // 2. 结束信号流 (Type = DONE)
        // 这是一个只包含 1 个元素的流，用于在最后时刻发送
        Mono<ServerSentEvent<ChatRespDTO>> doneSignal = Mono.just(
                ServerSentEvent.<ChatRespDTO>builder()
                        .event("message")
                        .data(ChatRespDTO.builder()
                                .conversationId(conversationId)
                                .answer("") // 结束时内容为空
                                .type(ChatEventType.DONE.getValue())
                                .build())
                        .build()
        );

        // 3. 错误处理流 (Type = ERROR)
        // 如果中间发生异常，吞掉异常并发送一条 Error 类型的消息给前端
        Flux<ServerSentEvent<ChatRespDTO>> finalStream = contentStream
                .concatWith(doneSignal) // 把 DONE 信号拼接到流的末尾
                .onErrorResume(e -> {
                    log.error("流式对话异常", e);
                    ChatRespDTO errorResp = ChatRespDTO.builder()
                            .conversationId(conversationId)
                            .answer("系统异常: " + e.getMessage())
                            .type(ChatEventType.ERROR.getValue()) //  设置类型
                            .build();

                    return Mono.just(ServerSentEvent.<ChatRespDTO>builder()
                            .event("message")
                            .data(errorResp)
                            .build());
                });

        return finalStream;
    }
    /**
     * 层流式接口 (供 BaseAgent 内部使用)
     * 只返回纯文本内容，不封装 SSE，方便上层业务自由处理
     */
    public Flux<String> streamChatContent(String question, String conversationId, String systemPrompt) {
        return chatClient.prompt()
                .system(systemPrompt) // 1. 注入 Agent 的人设
                .user(question)
                .advisors(a -> a.param(MessageWindowChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content(); // 2. 直接返回内容流 (Flux<String>)
    }
}
