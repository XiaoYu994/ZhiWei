package com.smallfish.zhiwei.service.retrieval;

import com.smallfish.zhiwei.dto.resp.SearchResultDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/*
*  召回重排过程 使用 DashScope 对召回的文档进行打分
* */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalService {
    private final VectorSearchService vectorSearchService;
    private final RerankService rerankService;

    @Value("${rag.recall.top-k}")
    private   Long recallTopK;
    @Value("${rag.rerank.top-k}")
    private   int rerankTopK;
    @Value("${rag.rerank.threshold}")
    private double rerankThreshold;

    /**
     * 执行完整的检索流程
     * @param query 用户提问
     * @return 排序后且包含元数据的文档列表
     */
    public List<SearchResultDTO> retrieve(String query) {
        return retrieve(query, null);
    }

    /**
     * 执行完整的检索流程 (带过滤)
     * @param query 用户提问
     * @param filterExpr 过滤表达式 (Milvus DSL)
     * @return 排序后且包含元数据的文档列表
     */
    public List<SearchResultDTO> retrieve(String query, String filterExpr) {
        // 1. Recall (向量召回) - 快速获取候选集
        List<SearchResultDTO> recallResults = vectorSearchService.search(query, recallTopK, filterExpr);

        if (recallResults == null || recallResults.isEmpty()) {
            log.info("向量库未召回到任何数据，直接返回空");
            return new ArrayList<>();
        }
        // 2. 建立 ID 映射 (Map<ID, Object>)
        // Key 是 SearchResult 的 ID (唯一)，Value 是 SearchResult 对象本身
        Map<String, SearchResultDTO> idMap = recallResults.stream()
                .collect(Collectors.toMap(SearchResultDTO::getId, Function.identity()));

        // 3. 转换为 Document 对象，并保留 ID
        // -------------------------------------------------------------
        List<Document> documentsForRerank = recallResults.stream()
                .map(result -> {
                    // 创建 Spring AI Document
                    // 【关键一步】把原始对象的 ID 赋值给 Document 的 ID
                    return Document.builder()
                            .id(result.getId())
                            .text(result.getContent())
                            .build();
                })
                .toList();

        // 4. Rerank (重排) - 传入 Document 列表
        List<Document> rerankedDocs = rerankService.rerank(query, documentsForRerank, rerankTopK);
        // 5. 结果还原
        List<SearchResultDTO> finalResults = new ArrayList<>();

        // 6. 阈值过滤
        for (Document doc : rerankedDocs) {
            Double score = (Double) doc.getMetadata().get("score");
            // 阈值过滤
            if (score != null && score < rerankThreshold) {
                continue;
            }

            // 7. 通过 id 召回原始对象
            SearchResultDTO originalObj = idMap.get(doc.getId());

            if (originalObj != null) {
                if (score != null) {
                    originalObj.setScore(score.floatValue());
                }
                finalResults.add(originalObj);
            } else {
                // 理论上不可能进这里，除非 Rerank 篡改了 ID
                log.warn("ID 映射失败: 无法找到 ID={} 的原始对象", doc.getId());
            }
        }
        return finalResults;
    }
}
