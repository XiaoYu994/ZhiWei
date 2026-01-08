package com.smallfish.zhiwei.dto;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/*
*  Metadata 元数据实体
* */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DocMetadataDTO {

    // 使用 @SerializedName 指定存入 Milvus 的 JSON 字段名
    @SerializedName("_source")
    private String source;

    @SerializedName("_file_name")
    private String fileName;

    @SerializedName("_extension")
    private String extension;

    private Integer chunkIndex;

    private Integer totalChunks;

    private String title;
}
