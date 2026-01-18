package com.smallfish.zhiwei.service.storage;

import com.smallfish.zhiwei.dto.req.FileUploadReqDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件存储服务接口
 * 策略模式：支持 Local, OSS, MinIO 等多种实现
 */
public interface FileStorageService {

    /**
     * 上传文件
     *
     * @param file 前端上传的文件
     * @return 文件信息
     */
    FileUploadReqDTO upload(MultipartFile file);

    /**
     * 列出所有文件
     *
     * @return 文件列表
     */
    List<FileUploadReqDTO> listFiles();

    /**
     * 读取文件内容
     *
     * @param path 文件路径
     * @return 文件内容字符串
     */
    String readFileContent(String path);

    /**
     *  删除文集
     * @param path 文件路径
     * @return 返回删除结果
     */
    boolean delete(String path);
}
