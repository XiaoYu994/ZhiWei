package com.smallfish.zhiwei.agent.tool;

import com.smallfish.zhiwei.agent.manager.ClsClientManager;
import com.smallfish.zhiwei.dto.resp.ClsTopicDTO;
import com.smallfish.zhiwei.dto.resp.ClsTopicListRespDTO;
import com.tencentcloudapi.cls.v20201016.ClsClient;
import com.tencentcloudapi.cls.v20201016.models.DescribeTopicsRequest;
import com.tencentcloudapi.cls.v20201016.models.DescribeTopicsResponse;
import com.tencentcloudapi.cls.v20201016.models.TopicInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CLS 元数据查询工具。
 * <p>
 * 专门用于查询日志主题列表。
 * 在 Agentic Workflow (智能体工作流) 中，该工具通常作为<b>第一步</b>被调用，
 * 用于解决"用户只知道服务名，不知道 TopicId"的问题。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClsTopicTool implements AgentTools {

    private final ClsClientManager clientManager;

    /**
     * 获取指定地域下的所有日志主题列表。
     * <p>
     * 该方法会自动处理 API 分页逻辑，确保拉取全量数据，避免因服务过多导致漏查。
     * </p>
     *
     * @param region 地域代码 (如 "ap-beijing", "ap-shanghai")。
     * 如果为空，将使用 {@link ClsClientManager} 中配置的默认地域。
     * @return 包含所有 Topic 信息的列表对象。
     */
    @Tool(description = "获取指定地域下的日志主题列表。当只知道服务名但不知道 TopicId 时使用。")
    public ClsTopicListRespDTO getLogTopics(
            @ToolParam(description = "地域代码 (如 ap-beijing)，留空则使用默认配置") String region) {

        // 1. 确定最终地域，方便日志记录
        String targetRegion = StringUtils.hasText(region) ? region : clientManager.getDefaultRegion();
        log.info(" AI 请求获取主题列表 | Region: {}", targetRegion);

        List<ClsTopicDTO> allTopics = new ArrayList<>();

        try {
            ClsClient client = clientManager.getClient(targetRegion);

            // 2. 分页获取所有主题 (防止服务超过 50 个被截断)
            long limit = 50L;
            long offset = 0L;
            long totalCount = 0L;

            do {
                DescribeTopicsRequest req = new DescribeTopicsRequest();
                req.setLimit(limit);
                req.setOffset(offset);

                DescribeTopicsResponse resp = client.DescribeTopics(req);

                // 记录总数
                if (resp.getTotalCount() != null) {
                    totalCount = resp.getTotalCount();
                }

                if (resp.getTopics() != null) {
                    for (TopicInfo info : resp.getTopics()) {
                        allTopics.add(ClsTopicDTO.builder()
                                .topicId(info.getTopicId())
                                .topicName(info.getTopicName())
                                .logsetId(info.getLogsetId())
                                .description(info.getDescribes())
                                .build());
                    }
                }

                // 移动游标
                offset += limit;

            } while (offset < totalCount); // 如果已获取的数量小于总数，继续循环

            log.info("成功获取 {} 个日志主题 | Region: {}", allTopics.size(), targetRegion);

            return ClsTopicListRespDTO.builder()
                    .success(true)
                    .message("获取成功，共 " + allTopics.size() + " 个主题")
                    .topics(allTopics)
                    .build();

        } catch (Exception e) {
            log.error(" 获取主题列表失败 | Region: {}", targetRegion, e);
            return ClsTopicListRespDTO.builder()
                    .success(false)
                    .message("获取失败: " + e.getMessage())
                    .topics(Collections.emptyList())
                    .build();
        }
    }
}