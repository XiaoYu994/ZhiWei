package com.smallfish.zhiwei.service.retrieval;

import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankModel;
import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankOptions;
import com.alibaba.cloud.ai.model.RerankRequest;
import com.alibaba.cloud.ai.model.RerankResponse;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class RerankService {
    //  直接注入 Spring AI 自动配置好的 Rerank 模型
    @Resource
    private DashScopeRerankModel dashScopeRerankModel;

    /**
     * 重排方法 (Spring AI 风格)
     *
     * @param query 用户问题
     * @param documents 待排序的文档内容 (String 列表)
     * @param topN 返回前 N 个
     * @return 排序后的文档列表 (注意：这里返回的是 Document 对象列表，你需要根据需要转回 String 或自定义对象)
     */
    public List<Document> rerank(String query, List<Document> documents, int topN) {
        if (documents == null || documents.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            // 1. 构建请求 (注意：传入的是 document 对象列表)
            RerankRequest request = new RerankRequest(query, documents,
                    DashScopeRerankOptions.builder()
                            .model("qwen3-rerank")
                            .topN(topN)
                            .build());

            // 2. 调用模型
            // Spring AI 的 DashScopeRerankModel 内部机制是：
            // 它会根据 API 返回的 index，去原来的 documents 列表里把对应的 Document 对象取出来
            // 所以，原来的 ID 会被完美保留下来。
            RerankResponse response = dashScopeRerankModel.call(request);


            // 4. 处理结果
            // Spring AI 的 RerankResponse 会返回带有分数的 Document
            // Spring AI 的 DocumentWithScore 包含了 score，我们需要把它解出来
            return response.getResults().stream()
                    .map(docWithScore -> {
                        Document doc = docWithScore.getOutput();
                        // 将分数塞入 metadata，方便上层筛选
                        // 注意：DashScope 返回的分数通常是 0.0 ~ 1.0 之间
                        doc.getMetadata().put("score", docWithScore.getScore());
                        return doc;
                    })
                    .toList();

        } catch (Exception e) {
            log.error("Rerank 服务异常，降级处理", e);
            // 降级策略：直接返回原列表的前 N 个
            return documents.stream().limit(topN).toList();
        }
    }
}
