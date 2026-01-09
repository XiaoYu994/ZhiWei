package com.smallfish.zhiwei.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;


/*
*  向量数据量存储实体类
* */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BizKnowledge {
    public static final String FIELD_ID = "id";
    public static final String FIELD_CONTENT = "content";
    public static final String FIELD_VECTOR = "vector";
    public static final String FIELD_METADATA = "metadata";
    public static final String FIELD_SOURCE = "source";


    /*
    *  ID 通过文件名和文件分片索引构建出唯一的 UUID
    * */
    private String id;

    /*
    *  分片内容
    * */
    private String content;

    /*
    * 分片内容的向量值
    * */
    private List<Float> vector;

    /*
    metadata 是给程序逻辑和用户溯源用的
    *  元数据   _source: 统一路径标识
                _file_name: 原始文件名
                _extension: 后缀
                chunkIndex 分片索引
                totalChunks 分片序号
                title  标题 如果有的话
    * */
    private Map<String, Object> metadata; // 存储 JSON 字符串

    /*
    *  统一路径标识 用于高效删除
    * */
    private String source;
}
