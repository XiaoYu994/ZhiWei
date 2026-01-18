package com.smallfish.zhiwei.service.retrieval;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.smallfish.zhiwei.common.constant.MilvusConstants;
import com.smallfish.zhiwei.dto.resp.SearchResultDTO;
import com.smallfish.zhiwei.model.BizKnowledge;
import com.smallfish.zhiwei.service.base.EmbeddingService;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
    private final Gson gson = new Gson();
    public List<SearchResultDTO> search(String query,Long limit) {
        log.info("开始搜索相似文档, 查询: {}, limit: {}", query, limit);

        try {
            // 1. 生成向量
            final List<Float> queryVector = embeddingService.generateEmbedding(query);

            // 2. 构建搜索参数 通向量通过了标准化，使用内积等同于余弦相似度。适合语义搜索。
            final SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .withMetricType(MetricType.IP)
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

            List<SearchResultDTO> results = new ArrayList<>();
            // 预定义 Map 类型，避免循环中重复创建
            Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
            for (int i = 0; i < scores.size(); i++) {
                // 安全获取各字段，防止越界
                String contentStr = (contents != null && i < contents.size()) ? String.valueOf(contents.get(i)) : "";
                String sourceStr = (sources != null && i < sources.size()) ? String.valueOf(sources.get(i)) : "";
                // 设置元数据
                if (metadatas != null && i < metadatas.size()) {
                    Object metaObj = metadatas.get(i);
                    Map<String, Object> metaMap = null;
                    if (metaObj instanceof JsonObject) {
                        // 场景 A: SDK 返回了 Gson 对象 (最常见)
                        metaMap = gson.fromJson((JsonObject) metaObj, mapType);
                    } else if (metaObj instanceof String) {
                        // 场景 B: SDK 返回了 JSON 字符串 (兼容旧版或特殊配置)
                        metaMap = gson.fromJson((String) metaObj, mapType);
                    } else if (metaObj instanceof Map) {
                        // 场景 C: SDK 已经转成了 Map (少见，但防御一下)
                        metaMap = (Map<String, Object>) metaObj;
                    }
                    results.add(SearchResultDTO.builder()
                            .id(scores.get(i).getStrID())
                            .score(scores.get(i).getScore())
                            .content(contentStr)
                            .source(sourceStr)
                            .metadata(metaMap)
                            .build());
                }
            }
            log.info("搜索完成, 找到 {} 个相似文档", results.size());
            return results;
        } catch (Throwable e) {
            log.error("搜索相似文档失败", e);
            // 返回空列表
            return Collections.emptyList();
        }
    }

}
