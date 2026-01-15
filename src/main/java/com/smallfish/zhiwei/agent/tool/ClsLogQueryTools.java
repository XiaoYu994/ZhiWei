package com.smallfish.zhiwei.agent.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smallfish.zhiwei.agent.manager.ClsClientManager;
import com.smallfish.zhiwei.dto.resp.ClsLogEntryDTO;
import com.smallfish.zhiwei.dto.resp.ClsSearchRespDTO;
import com.tencentcloudapi.cls.v20201016.ClsClient;
import com.tencentcloudapi.cls.v20201016.models.LogInfo;
import com.tencentcloudapi.cls.v20201016.models.SearchLogRequest;
import com.tencentcloudapi.cls.v20201016.models.SearchLogResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;


/*
 *  agent 查询 cls 日志工具
 * */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClsLogQueryTools implements AgentTools {

    // 使用管理器
    private final ClsClientManager clientManager;
    private final ObjectMapper objectMapper;

    @Value("${tencent.cls.default-region}")
    private String defaultRegion;


    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @Tool(description = "查询腾讯云 CLS 日志内容。需要 TopicId。")
    public ClsSearchRespDTO searchLogs(
            @ToolParam(description = "地域代码") String region,
            @ToolParam(description = "日志主题 ID (TopicId)") String topicId,
            @ToolParam(description = "查询语句") String query,
            @ToolParam(description = "时间范围(分钟)") Integer timeRangeMinutes) {

        // 1. 参数校验与预处理
        String targetRegion = (region == null || region.isBlank()) ? defaultRegion : region;
        String safeQuery = (query == null || query.isBlank()) ? "*" : query;
        int safeRange = (timeRangeMinutes == null || timeRangeMinutes <= 0) ? 60 : timeRangeMinutes;

        if (topicId == null || topicId.isBlank()) {
            return errorResponse("TopicId 不能为空");
        }

        log.info("AI 执行日志查询 | Region: {} | Topic: {} | Range: {}m | Query: {}",
                targetRegion, topicId, safeRange, safeQuery);

        try {
            // 2. 获取或创建 Client (带缓存)
            ClsClient client = clientManager.getClient(region);

            // 3. 构建请求
            SearchLogRequest req = new SearchLogRequest();
            req.setTopicId(topicId);
            req.setQuery(safeQuery);
            req.setLimit(20L); // 限制返回条数，防止 Token 超限
            req.setSort("desc"); // 最新的日志在前面

            // 时间处理
            long now = System.currentTimeMillis();
            req.setFrom(now - (safeRange * 60 * 1000L));
            req.setTo(now);

            // 4. 执行调用
            SearchLogResponse resp = client.SearchLog(req);

            // 5. 结果转换
            List<ClsLogEntryDTO> logList = parseLogs(resp);

            log.info("查询成功，命中 {} 条日志", logList.size());

            return ClsSearchRespDTO.builder()
                    .success(true)
                    .requestId(resp.getRequestId())
                    .logCount(logList.size())
                    .logs(logList)
                    .message("成功查询到 " + logList.size() + " 条日志")
                    .build();

        } catch (Throwable e) {
            log.error("CLS 查询失败 | Region: {}", targetRegion, e);
            return errorResponse("日志查询服务异常: " + e.getMessage());
        }
    }

    /**
     * 解析日志结果
     */
    private List<ClsLogEntryDTO> parseLogs(SearchLogResponse resp) {
        List<ClsLogEntryDTO> result = new ArrayList<>();
        if (resp.getResults() == null) {
            return result;
        }

        for (LogInfo info : resp.getResults()) {
            ClsLogEntryDTO entry = new ClsLogEntryDTO();
            // 格式化时间
            entry.setTime(formatTime(info.getTime()));

            // 解析 JSON 内容
            try {
                String jsonStr = info.getLogJson();
                if (jsonStr != null && !jsonStr.isBlank()) {
                    Map<String, Object> content = objectMapper.readValue(jsonStr, new TypeReference<>() {});
                    entry.setContent(content);
                } else {
                    entry.setContent(Collections.emptyMap());
                }
            } catch (Exception e) {
                // 如果解析失败，保留原始字符串，不要抛出异常中断整个列表
                entry.setContent(Map.of("raw_error", "JSON Parse Failed", "raw_data", info.getLogJson()));
            }
            result.add(entry);
        }
        return result;
    }

    private String formatTime(Long timestamp) {
        if (timestamp == null) return "N/A";
        // 兼容秒级和毫秒级时间戳
        long millis = timestamp < 10000000000L ? timestamp * 1000 : timestamp;
        return TIME_FORMATTER.format(Instant.ofEpochMilli(millis));
    }

    private ClsSearchRespDTO errorResponse(String msg) {
        return ClsSearchRespDTO.builder()
                .success(false)
                .logCount(0)
                .message(msg)
                .logs(Collections.emptyList())
                .build();
    }
}