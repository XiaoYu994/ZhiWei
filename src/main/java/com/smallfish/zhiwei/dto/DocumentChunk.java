package com.smallfish.zhiwei.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
*  文档分片
* */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DocumentChunk {
    /**
     * 分片内容
     */
    private String content;

    /**
     * 分片在原文档中的起始位置
     */
    private int startIndex;

    /**
     * 分片在原文档中的结束位置
     */
    private int endIndex;

    /**
     * 分片序号（从0开始）
     */
    private int chunkIndex;

    /**
     * 分片标题或上下文信息
     */
    private String title;

    /*
    *  不输出完整的 content 内容
    * */
    @Override
    public String toString() {
        return "DocumentChunk{" +
                "chunkIndex=" + chunkIndex +
                ", title='" + title + '\'' +
                ", contentLength=" + (content != null ? content.length() : 0) +
                ", startIndex=" + startIndex +
                ", endIndex=" + endIndex +
                '}';
    }
}
