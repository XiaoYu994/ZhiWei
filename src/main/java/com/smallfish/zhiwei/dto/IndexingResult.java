package com.smallfish.zhiwei.dto;

import lombok.Data;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
public class IndexingResult {
    private boolean success;
    private String directoryPath;
    private int totalFiles;
    private int successCount;
    private int failCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String errorMessage;
    private Map<String, String> failedFiles = new HashMap<>();

    public void incrementSuccessCount() { this.successCount++; }
    public void incrementFailCount() { this.failCount++; }
    public void addFailedFile(String filePath, String error) {
        this.failedFiles.put(filePath, error);
    }
    public long getDurationMs() {
        if (startTime != null && endTime != null) {
            return Duration.between(startTime, endTime).toMillis();
        }
        return 0;
    }
}