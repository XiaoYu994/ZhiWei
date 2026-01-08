package com.smallfish.zhiwei.service;

import cn.hutool.core.collection.CollectionUtil;
import com.smallfish.zhiwei.dto.req.ChatReq;
import com.smallfish.zhiwei.dto.resp.ChatResp;
import com.smallfish.zhiwei.entity.BizKnowledge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
 * 聊天服务
 * 封装 ReactAgent 对话的公共逻辑，包括模型创建、系统提示词构建、Agent 配置等
* */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final VectorSearchService vectorSearchService;
    private final ChatModel chatModel;

    @Value("${rag.limit}")
    private Long limit;
    // 定义提示词模板 (Prompt Engineering)
    // 关键点：明确告诉模型"只根据参考资料回答"，防止幻觉
    private static final String RAG_PROMPT_TEMPLATE = """
            你是一个专业的智能知识库助手。请根据以下提供的参考资料回答用户的问题。
            
            【注意事项】
            1. 如果参考资料中包含答案，请详细回答。
            2. 如果参考资料与问题无关，或者没有提供足够的信息，请直接回答：“抱歉，知识库中没有找到相关信息。”，不要编造答案。
            3. 回答时请保持客观、准确。
            
            【参考资料】
            {context}
            
            【用户问题】
            {question}
            """;

    public ChatResp chat(ChatReq req) {
        String question = req.getQuery();
        // 1. 如果不使用 RAG，直接问大模型 (用于闲聊或对比)
        if(!req.isUseRag()) {
            return ChatResp.builder()
                    .answer(chatModel.call(question))
                    .build();
        }
        // 2. 使用 RAG 先去 Milvus 查资料
        final List<VectorSearchService.SearchResult> results = vectorSearchService.search(question,limit);

        // 3. 处理搜索结果
        List<String> contextList = new ArrayList<>();
        List<String> sources = new  ArrayList<>();
        if(CollectionUtil.isEmpty(results)) {
            // 如果没查到，也可以选择直接让大模型发挥，或者返回固定话术
            return new ChatResp("知识库中暂无相关内容。", null);
        }

        for (VectorSearchService.SearchResult result : results) {
            // 拼接上下文：也可以把 title 或 source 加进去让模型知道出处
            contextList.add(result.getContent());
            // 收集来源文件，去重
            if (result.getSource() != null && !sources.contains(result.getSource())) {
                sources.add(result.getSource());
            }
        }

        // 4. 组装最终的 Prompt
        // 将 List<String> 转为换行分隔的字符串
        String contextStr = String.join("\n\n------\n\n", contextList);

        PromptTemplate promptTemplate = new PromptTemplate(RAG_PROMPT_TEMPLATE);
        Prompt prompt = promptTemplate.create(Map.of(
                "context", contextStr,
                "question", question
        ));
        // 5. 调用大模型
        log.info("发送给大模型的 Prompt 长度: {}", prompt.getContents().length());
        String aiAnswer = chatModel.call(prompt).getResult().getOutput().getText();

        // 6. 返回结果 + 来源
        return new ChatResp(aiAnswer, sources);
    }
}
