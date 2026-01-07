package com.smallfish.zhiwei.service.sub;

import cn.hutool.core.collection.CollectionUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.smallfish.zhiwei.common.constant.MilvusConstants;
import com.smallfish.zhiwei.dto.DocumentChunk;
import com.smallfish.zhiwei.service.DocumentChunkService;
import com.smallfish.zhiwei.service.EmbeddingService;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.MutationResult;
import io.milvus.param.R;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 子系统：向量入库服务
 * 职责：只负责核心逻辑（删除旧数据 -> 切片 -> 向量化 -> 入库）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorIngestionService {

    private final DocumentChunkService chunkService;
    private final EmbeddingService embeddingService;
    private final MilvusServiceClient milvusClient;
    private final ObjectMapper objectMapper;

    // 批处理大小 通义千问 只支持 10 段文本
    private static final int BATCHSIZE = 10;


    /**
     *  处理单个文档的核心流程
     * @param filename 文件名称
     * @param content 文件内容
     */
    public void ingest(String filename, String content) {
        // 1. 先删除该文件的旧数据！(这是原项目的关键逻辑)
        deleteExistingData(filename);
        log.info("开始处理文档: {}, 长度: {}", filename, content.length());

        // 2. 切片
        final List<DocumentChunk> chunks = chunkService.chunkDocument(content, filename);
        if (CollectionUtil.isEmpty(chunks)) {
            log.warn("文档切片为空，跳过入库");
            return;
        }

        // 3. 批量入库
        for (int i = 0; i < chunks.size(); i += BATCHSIZE) {
            int end = Math.min(i + BATCHSIZE, chunks.size());
            List<DocumentChunk> batchChunks = chunks.subList(i, end);
            processBatch(filename, batchChunks);
        }

        log.debug("文档入库完成: {}", filename);
    }

    private void processBatch(String filename, List<DocumentChunk> batchChunks) {
        // 1. 提取当前批次的文本
        List<String> chunkTexts = batchChunks.stream()
                .map(DocumentChunk::getContent)
                .toList();
        // 2. 向量化
        final List<List<Float>> vectors = embeddingService.generateEmbedding(chunkTexts);

        // 3. 构建当前批次的数据
        List<String> ids = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        List<String> metadatas = new ArrayList<>(); // 这里假设 Milvus schema 中 metadata 是 VarChar 或者 SDK 支持 String 形式的 JSON

        for (int j = 0; j < batchChunks.size(); j++) {
            DocumentChunk chunk = batchChunks.get(j);

            // ID 生成逻辑
            String uniqueKey = filename + "_" + chunk.getChunkIndex();
            String id = UUID.nameUUIDFromBytes(uniqueKey.getBytes(StandardCharsets.UTF_8)).toString();

            // 构建 Metadata JSON
            Map<String,Object> metaMap = new HashMap<>();
            metaMap.put("filename",filename);
            metaMap.put("chunkIndex",chunk.getChunkIndex());
            metaMap.put("title",chunk.getTitle());

            try {
                metadatas.add(objectMapper.writeValueAsString(metaMap));
            } catch (JsonProcessingException e) {
                log.error("Metadata JSON 序列化失败", e);
                metadatas.add("{}");
            }

            ids.add(id);
            contents.add(chunk.getContent());
        }

        // 4. 插入这一小批
        insertBatch(ids, contents, vectors, metadatas);

    }


    /**
     *  批量插入 milvus
     * @param ids      id
     * @param contents 内容
     * @param vectors 向量值
     * @param metadatas 元数据
     */
    private void insertBatch(List<String> ids, List<String> contents, List<List<Float>> vectors, List<String> metadatas) {
        List<JsonObject> metadataObjects = new ArrayList<>();
        Gson gson = new Gson();
        for (String jsonStr : metadatas) {
            // 把字符串转成 Gson 的 JsonObject 对象
            metadataObjects.add(gson.fromJson(jsonStr, JsonObject.class));
        }
        final InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                .withFields(List.of(
                        new InsertParam.Field("id", ids),
                        new InsertParam.Field("content", contents),
                        new InsertParam.Field("vector", vectors),
                        new InsertParam.Field("metadata", metadataObjects) // 注意：这里 Milvus SDK 可能需要 List<JsonObject> 或 List<String>，视版本而定
                ))
                .build();
        final R<MutationResult> response = milvusClient.insert(insertParam);

        if(response.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Milvus 批量插入失败: " + response.getMessage());
        }

        log.info("成功插入 {} 条向量数据", response.getData().getInsertCnt());

    }

    /**
     * 删除指定文件的所有旧数据
     */
    private void deleteExistingData(String filename) {
        try {
            // 构建删除表达式：metadata["filename"] == "xxx"
            // 注意：这里要和你的 metadata 字段名保持一致
            String expr = String.format("metadata[\"filename\"] == \"%s\"", filename);

            DeleteParam deleteParam = DeleteParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .withExpr(expr)
                    .build();

            milvusClient.delete(deleteParam);
            log.info("已清理文件旧数据: {}", filename);
        } catch (Exception e) {
            log.warn("清理旧数据失败 (可能是首次上传): {}", e.getMessage());
        }
    }
}
