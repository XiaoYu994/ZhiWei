package com.smallfish.zhiwei.controller;

import com.smallfish.zhiwei.common.result.Result;
import com.smallfish.zhiwei.config.FileUploadConfig;
import com.smallfish.zhiwei.dto.req.FileUploadReqDTO;
import com.smallfish.zhiwei.service.ingestion.KnowledgeBaseFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/*
*  文件上传控制器
* */
@Slf4j
@RestController
@RequiredArgsConstructor
public class FileUploadController {

    private final FileUploadConfig fileUploadConfig;
    private final KnowledgeBaseFacade knowledgeBaseFacade;

    // 最大 100 MB
    private static final int FILE_SIZE_MAX = 100 * 1024 * 1024;


    @PostMapping(value = "/api/upload", consumes = "multipart/form-data")
    public ResponseEntity<Result<?>> upload(@RequestParam("file") MultipartFile file) {
        // 1. 基础校验
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Result.error(400, "文件不能为空"));
        }

        // 2. 安全的文件名获取 (防止路径遍历)
        String originalFilename = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        if (originalFilename.contains("..")) {
            return ResponseEntity.badRequest().body(Result.error(400, "非法的文件名"));
        }

        // 3. 扩展名校验
        String extension = StringUtils.getFilenameExtension(originalFilename);
        if (!fileUploadConfig.isAllowed(extension)) {
            return ResponseEntity.badRequest().body(Result.error(400, "不支持的文件格式"));
        }
        try {
            // 目录准备
            Path uploadDir = Paths.get(fileUploadConfig.getPath()).normalize();
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }
            // 使用原始文件名，而不是UUID，以便实现基于文件名的去重
            Path filePath = uploadDir.resolve(originalFilename).normalize();

            // 4. 原子性文件写入 (替代先删后写)
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            log.info("文件上传成功: {}", filePath);
            // 5. 调用异步门面
            //  注意文件大小限制，防止 OOM
            if (file.getSize() > FILE_SIZE_MAX ) {
                return ResponseEntity.badRequest().body(Result.error(400, "文件过大，当前限制 100MB"));
            }
            String content = Files.readString(filePath);
            // 这里可能抛出 TaskRejectedException (线程池满)
            try {
                knowledgeBaseFacade.importSingleDocAsync(originalFilename, content);
            } catch (Exception e) {
                log.error("任务提交失败", e);
                return ResponseEntity.status(503).body(Result.error(503, "任务提交失败"));
            }
            // 6. 立即返回响应，无需等待索引完成
            FileUploadReqDTO resData = new FileUploadReqDTO(
                    originalFilename,
                    filePath.toString(),
                    file.getSize()
            );

            return ResponseEntity.ok(Result.success(resData));
        } catch (IOException e) {
            // 这里既能捕获 Files.copy 的错误，也能捕获 Files.readString 的错误
            log.error("文件IO操作异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error(500, "文件处理失败: " + e.getMessage()));
        }


    }

}
