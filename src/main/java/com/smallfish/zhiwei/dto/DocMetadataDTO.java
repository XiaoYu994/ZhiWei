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

    /*
    *  原始文件名
    * */
    @SerializedName("_file_name")
    private String fileName;

    /*
    *  后缀
    * */
    @SerializedName("_extension")
    private String extension;

    /*
    *  分片序号
    * */
    private Integer chunkIndex;

    /*
    *  总分片大小
    * */
    private Integer totalChunks;

    /*
    *  标题
    * */
    private String title;
}
