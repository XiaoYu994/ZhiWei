package com.smallfish.zhiwei.dto.resp; // 放在 resp 包下

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

/**
 * 向量检索结果 DTO
 * 用于在 Service 层之间传递检索数据，解耦 Milvus SDK
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultDTO {

    /** 文档唯一 ID */
    private String id;

    /** 文档分片内容 */
    private String content;

    /** 相似度分数 (Milvus L2/Cosine Distance) */
    private Float score;

    /** 来源标识 (如文件路径) */
    private String source;

    /** * 动态元数据 (title, page, author 等)
     * 使用 Map<String, Object> 确保 Jackson 序列化时格式完美
     */
    private Map<String, Object> metadata;
}