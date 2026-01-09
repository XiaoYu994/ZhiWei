package com.smallfish.zhiwei.dto.req;

import lombok.*;

/*
* 文件响应实体
* */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileUploadReqDTO {

    /*
    *  文件名称
    * */
    private String fileName;
    /*
     *  文件路径
     * */
    private String filePath;
    /*
     *  文件大小
     * */
    private Long fileSize;
}
