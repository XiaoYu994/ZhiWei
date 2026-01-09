package com.smallfish.zhiwei.agent.tool;

import com.google.gson.Gson;
import com.smallfish.zhiwei.dto.resp.SearchResultDTO;
import com.smallfish.zhiwei.service.retrieval.RetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 内部文档查询工具
 * */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternalDocsTools implements AgentTools {
    private final RetrievalService retrievalService;
    //  @Tool 机制非常智能。如果你的方法返回的是一个 Java 对象
    // （如 List<SearchResult>），框架会自动帮你把它序列化成 JSON 字符串喂给大模型
    // 但是 SearchResult 中有参数 是 Gson 对象所以需要手动序列化
    private final Gson gson = new Gson();

    /**
     * 优化后的中文描述
     * 注意：直接返回 List<SearchResult>，Spring AI 会自动转 JSON 给大模型
     */
    @Tool(description = """
            使用此工具可以搜索内部文档和知识库以获取相关信息。
            它执行 RAG（检索增强生成）以查找相似文档并提取处理步骤。
            当你需要了解公司文档中存储的内部流程、最佳实践或逐步指南时，这非常有用。
            """)
    public String queryInternalDocs(
            @ToolParam(description = "用于检索文档的关键词或问题摘要") String query) {

        try {
            log.info("Agent 正在调用 RAG 检索服务 问题: {}", query);

            // 召回 - 重排 - 精选
            List<SearchResultDTO> results = retrievalService.retrieve(query);

            if (results == null || results.isEmpty()) {
                log.warn("RAG 服务未返回任何有效文档");
                // 返回空列表即可，模型会看到 "[]"
                return "[]";
            }

            return gson.toJson(results);

        } catch (Exception e) {
            log.error("[工具错误] queryInternalDocs 执行失败", e);
            // 发生异常时，为了不让程序崩掉，可以返回一个包含错误信息的特殊对象
            return String.format("{\"error\": \"查询失败: %s\"}", e.getMessage());
        }
    }
}
