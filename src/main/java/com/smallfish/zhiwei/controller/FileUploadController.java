package com.smallfish.zhiwei.controller;

import com.smallfish.zhiwei.common.result.Result;
import com.smallfish.zhiwei.dto.req.FileUploadReqDTO;
import com.smallfish.zhiwei.service.ingestion.KnowledgeBaseFacade;
import com.smallfish.zhiwei.service.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/*
*  文件上传控制器
* */
@Slf4j
@RestController
@RequiredArgsConstructor
public class FileUploadController {

    private final FileStorageService fileStorageService;
    private final KnowledgeBaseFacade knowledgeBaseFacade;

    @PostMapping(value = "/api/upload", consumes = "multipart/form-data")
    public ResponseEntity<Result<?>> upload(@RequestParam("file") MultipartFile file) {
        try {
            // 1. 委托给存储服务处理文件
            FileUploadReqDTO fileInfo = fileStorageService.upload(file);

            // 2. 读取内容用于知识库导入
            String content = fileStorageService.readFileContent(fileInfo.getFilePath());

            // 3. 调用异步门面导入知识库
            try {
                knowledgeBaseFacade.importSingleDocAsync(fileInfo.getFileName(), content);
            } catch (Throwable e) {
                log.error("任务提交失败", e);
                return ResponseEntity.status(503).body(Result.error(503, "任务提交失败"));
            }

            // 4. 返回结果
            return ResponseEntity.ok(Result.success(fileInfo));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Result.error(400, e.getMessage()));
        } catch (Exception e) {
            log.error("文件上传处理异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error(500, "文件处理失败: " + e.getMessage()));
        }
    }

    @GetMapping("/api/files")
    public ResponseEntity<Result<List<FileUploadReqDTO>>> listFiles() {
        try {
            List<FileUploadReqDTO> files = fileStorageService.listFiles();
            return ResponseEntity.ok(Result.success(files));
        } catch (Exception e) {
            log.error("获取文件列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error(500, "获取文件列表失败: " + e.getMessage()));
        }
    }
}
