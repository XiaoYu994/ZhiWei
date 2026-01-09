package com.smallfish.zhiwei.service.ingestion;

import com.smallfish.zhiwei.dto.resp.IndexingResultDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 门面模式：知识库门面
 * 职责：对外提供统一接口，对内调度各个子系统
 */
@Slf4j
@Service
@RequiredArgsConstructor // 自动生成构造函数注入
public class KnowledgeBaseFacade {
    private final LocalFileService fileService;
    private final VectorIngestionService ingestionService;

    @Value("${file.upload.path}")
    private String defaultPath;

    /**
     * 对外接口 1：扫描并索引目录
     */
    public IndexingResultDTO importFromDirectory(String directoryPath) {
        IndexingResultDTO result = new IndexingResultDTO();
        result.setStartTime(LocalDateTime.now());

        String targetPath = (directoryPath == null || directoryPath.isBlank()) ? defaultPath : directoryPath;

        try {
            // 1. 指挥子系统 A：扫描文件
            List<File> files = fileService.scanDirectory(targetPath);
            result.setTotalFiles(files.size());
            result.setDirectoryPath(targetPath);

            log.info("门面启动: 扫描目录 {}, 待处理文件 {}", targetPath, files.size());

            // 2. 调度循环
            for (File file : files) {
                try {
                    // 指挥子系统 A：读内容
                    String content = fileService.readFileContent(file);

                    // 指挥子系统 B：入库
                    ingestionService.ingest(file.getName(), content);

                    result.incrementSuccessCount();
                    log.info("入库 成功， {}", file.getName());
                } catch (Exception e) {
                    result.incrementFailCount();
                    result.addFailedFile(file.getName(), e.getMessage());
                    log.error("处理文件失败: {}", file.getName(), e);
                }
            }
            result.setSuccess(result.getFailCount() == 0);

        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            log.error("门面处理异常", e);
        } finally {
            result.setEndTime(LocalDateTime.now());
        }

        return result;
    }

    /**
     * 对外接口 2：处理单个上传文件内容
     * * 添加 @Async 注解，并指定线程池名称 "kbExecutor"
     * 这样 Controller 调用这个方法时，会立即返回，而实际逻辑在子线程跑
     */
    @Async("kbExecutor")
    public void importSingleDocAsync(String filename, String content) {
        log.info("开始后台异步处理: {}, 当前线程: {}", filename, Thread.currentThread().getName());

        try {
            // 直接调用子系统
            ingestionService.ingest(filename, content);
            log.info("异步处理完成: {}", filename);
        } catch (Exception e) {
            log.error("异步处理异常: {}", filename, e);
            // 生产环境中，这里应该把失败记录写入数据库的 "task_log" 表，方便后续重试
        }
    }

}
