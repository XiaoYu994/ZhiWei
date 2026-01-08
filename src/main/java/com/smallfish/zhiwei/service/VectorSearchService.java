package com.smallfish.zhiwei.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.smallfish.zhiwei.common.constant.MilvusConstants;
import com.smallfish.zhiwei.entity.BizKnowledge;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 向量搜索服务
 * 负责从 Milvus 中搜索相似向量
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchService {

    private final MilvusServiceClient milvusClient;
    private final EmbeddingService embeddingService;

    public List<SearchResult> search(String query,Long limit) {
        log.info("开始搜索相似文档, 查询: {}, limit: {}", query, limit);

        try {
            // 1. 生成向量
            final List<Float> queryVector = embeddingService.generateEmbedding(query);

            // 2. 构建搜索参数
            final SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .withMetricType(MetricType.L2)
                    .withLimit(limit)
                    .withFloatVectors(Collections.singletonList(queryVector))
                    .withVectorFieldName(BizKnowledge.FIELD_VECTOR)
                    .withOutFields(List.of(
                            BizKnowledge.FIELD_ID,
                            BizKnowledge.FIELD_CONTENT,
                            BizKnowledge.FIELD_METADATA,
                            BizKnowledge.FIELD_SOURCE
                    ))
                    .withParams("{\"nprobe\":10}") // 在最相似的 10 个桶中找
                    .build();
            // 3. 执行搜索
            final R<SearchResults> response = milvusClient.search(searchParam);

            if (response.getStatus() != R.Status.Success.getCode()) {
                throw new RuntimeException("向量搜索失败: " + response.getMessage());
            }

            // 4. 解析结果
            final SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());
            final List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);
            // 获取字段数据列表 (注意：这里返回的是整列数据)
            List<?> contents = wrapper.getFieldData(BizKnowledge.FIELD_CONTENT, 0);
            List<?> metadatas = wrapper.getFieldData(BizKnowledge.FIELD_METADATA, 0);
            List<?> sources = wrapper.getFieldData(BizKnowledge.FIELD_SOURCE, 0);

            List<SearchResult> results = new ArrayList<>();

            for (int i = 0; i < scores.size(); i++) {
                SearchResultsWrapper.IDScore score = scores.get(i);
                SearchResult result = new SearchResult();

                // 设置 ID 和分数
                result.setId(score.getStrID());
                result.setScore(score.getScore());

                // 设置内容 (通过下标 i 获取对应行的数据)
                if (contents != null && i < contents.size()) {
                    result.setContent(contents.get(i).toString());
                }

                if (sources != null && i < sources.size()) {
                    result.setSource(sources.get(i).toString());
                }

                // 设置元数据
                if (metadatas != null && i < metadatas.size()) {
                    final Object metaObj = metadatas.get(i);
                    if (metaObj instanceof JsonObject) {
                        result.setMetadata((JsonObject) metaObj);
                    } else {
                        // 防御性编程
                        result.setMetadata(JsonParser.parseString(metaObj.toString()).getAsJsonObject());
                    }

                }

                results.add(result);
            }
            log.info("搜索完成, 找到 {} 个相似文档", results.size());
            return results;
        } catch (Exception e) {
            log.error("搜索相似文档失败", e);
            throw new RuntimeException("搜索失败: " + e.getMessage(), e);
        }
    }


    /*
    *  搜索结果类
    * */
    @Data
    public static class SearchResult {
        private String id;
        private String content;
        private float score;
        private JsonObject metadata;
        private String source;
    }
}
