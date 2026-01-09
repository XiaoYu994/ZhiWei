package com.smallfish.zhiwei.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 知识库索引任务结果 DTO
 * 用于反馈文件批量入库的处理情况、耗时及错误明细
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexingResultDTO {
    /*
    * 任务是否整体成功
    * */
    private boolean success;

    /*
    * 本次被扫描和索引的目标目录路径
    * */
    private String directoryPath;

    /*
    * 扫描到的文件总数
    * */
    private int totalFiles;

    /*
    *  成功解析并入库的文件数量
    * */
    private int successCount;

    /*
    *  处理失败的文件数量
    * */
    private int failCount;

    /*
    *  索引任务开始时间
    * */
    private LocalDateTime startTime;

    /*
    *  索引任务结束时间
    * */
    private LocalDateTime endTime;

    /*
    *  全局错误信息
    * */
    private String errorMessage;

    /*
    *  失败文件详情映射 (Key: 文件路径, Value: 具体的错误原因/异常栈)
    * */
    private Map<String, String> failedFiles = new HashMap<>();

    /*
    *  /**
     * 增加成功计数
     */
    public void incrementSuccessCount() { this.successCount++; }

    /**
     * 增加失败计数
     */
    public void incrementFailCount() { this.failCount++; }

    /**
     * 记录失败文件信息
     * @param filePath 失败的文件路径
     * @param error 错误原因
     */
    public void addFailedFile(String filePath, String error) {
        this.failedFiles.put(filePath, error);
    }

    /**
     * 计算任务总耗时 (毫秒)
     * Jackson 序列化时会自动生成 "durationMs" 字段给前端
     */
    public long getDurationMs() {
        if (startTime != null && endTime != null) {
            return Duration.between(startTime, endTime).toMillis();
        }
        return 0;
    }
}