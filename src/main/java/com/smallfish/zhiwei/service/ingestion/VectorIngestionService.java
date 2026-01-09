package com.smallfish.zhiwei.service.ingestion;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.google.gson.Gson;
import com.smallfish.zhiwei.common.constant.MilvusConstants;
import com.smallfish.zhiwei.dto.model.DocMetadataDTO;
import com.smallfish.zhiwei.dto.model.DocumentChunkDTO;
import com.smallfish.zhiwei.model.BizKnowledge;
import com.smallfish.zhiwei.service.base.EmbeddingService;
import com.smallfish.zhiwei.utils.MilvusEntityConverter;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.MutationResult;
import io.milvus.param.R;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private final Gson gson = new Gson(); // 复用 Gson 对象

    // 批处理大小 通义千问 只支持 10 段文本
    private static final int BATCHSIZE = 10;


    /**
     *  处理单个文档的核心流程
     * @param filename 文件名称
     * @param content 文件内容
     */
    public void ingest(String filename, String content) {
        // 统一处理格式 上传的文件 filename 只是文件名，本地扫描是全路径
        String sourcePath = filename.replace(File.separator, "/");
        // 1. 先删除该文件的旧数据！(这是原项目的关键逻辑)
        deleteExistingData(sourcePath);
        log.info("开始处理文档: {}, 长度: {}", filename, content.length());

        // 2. 切片
        final List<DocumentChunkDTO> chunks = chunkService.chunkDocument(content, filename);
        if (CollectionUtil.isEmpty(chunks)) {
            log.warn("文档切片为空，跳过入库");
            return;
        }

        // 3. 批量入库
        processBatch(sourcePath, filename, chunks);


        log.debug("文档入库完成: {}", sourcePath);
    }

    private void processBatch(String sourcePath, String originalFilename, List<DocumentChunkDTO> chunks) {

        int totalChunks = chunks.size();
        for (int i = 0; i < totalChunks; i+= BATCHSIZE) {
            int end = Math.min(i + BATCHSIZE, totalChunks);
            List<DocumentChunkDTO> subList = chunks.subList(i, end);
            try {
                // 1. 提取当前批次的文本
                List<String> chunkTexts = subList.stream()
                        .map(DocumentChunkDTO::getContent)
                        .toList();
                // 2. 向量化
                final List<List<Float>> vectors = embeddingService.generateEmbedding(chunkTexts);

                // 3. 构建当前批次的数据
                List<BizKnowledge> entities = new ArrayList<>();
                for (int j = 0; j < subList.size(); j++) {

                    DocumentChunkDTO chunk = subList.get(j);
                    // ID 生成逻辑
                    String uniqueKey = sourcePath + "_" + chunk.getChunkIndex();
                    String id = UUID.nameUUIDFromBytes(uniqueKey.getBytes(StandardCharsets.UTF_8)).toString();

                    // 构建 元数据
                    DocMetadataDTO metaDto = buildMetadataDTO(originalFilename, chunk, totalChunks);
                    // json 插入的过程需要  JsonObject 但是 DTO 层用 Map ，分层解耦
                    Map<String, Object> metaMap = BeanUtil.beanToMap(metaDto);
                    BizKnowledge entity = BizKnowledge.builder()
                            .id(id)
                            .content(chunk.getContent())
                            .vector(vectors.get(j))
                            .metadata(metaMap)
                            .source(sourcePath)
                            .build();
                    entities.add(entity);
                }
                // 4. 插入这一小批
                insertBatch(entities);
            } catch (Exception e) {
                log.error("批次处理失败 [{} - {}]: {}", i, end, e.getMessage());
                // 这里可以选择 throw 继续抛出，或者记录到错误表
                throw new RuntimeException("向量处理失败", e);
            }
        }

    }

    /**
     * 构建元数据
     */
    private DocMetadataDTO buildMetadataDTO(String originalFilename, DocumentChunkDTO chunk, int totalChunks) {
        // 文件扩展名解析
        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalFilename.substring(dotIndex);
        }

        // 处理 title：如果是空字符串，强制转为 null，这样 Gson 就会忽略它
        String validTitle = (chunk.getTitle() != null && !chunk.getTitle().isEmpty())
                ? chunk.getTitle()
                : null;
        return DocMetadataDTO.builder()
                .fileName(originalFilename)
                .extension(extension)
                .chunkIndex(chunk.getChunkIndex())
                .totalChunks(totalChunks)
                .title(validTitle)
                .build();
    }

    /**
     * 批量插入 milvus
     * @param entities 向量数据实体类列表
     */
    private void insertBatch(List<BizKnowledge> entities) {
        final InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                .withFields(MilvusEntityConverter.toInsertFields(entities))
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
    private void deleteExistingData(String sourcePath) {
        try {
            milvusClient.loadCollection(LoadCollectionParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .build());
            // (标量索引过滤，快)
            String expr = String.format("%s == \"%s\"", BizKnowledge.FIELD_SOURCE, sourcePath);
            DeleteParam deleteParam = DeleteParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .withExpr(expr)
                    .build();

            final MutationResult response = milvusClient.delete(deleteParam).getData();
            log.info("已清理文件旧数据: {}, 影响行数: {}", sourcePath, response.getDeleteCnt());
        } catch (Exception e) {
            log.warn("清理旧数据失败 (可能是首次上传): {}", e.getMessage());
        }
    }
}
