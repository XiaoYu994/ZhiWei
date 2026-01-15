package com.smallfish.zhiwei.agent.tool;

import com.smallfish.zhiwei.dto.resp.ClsLogEntryDTO;
import com.smallfish.zhiwei.dto.resp.ClsSearchRespDTO;
import com.smallfish.zhiwei.dto.resp.ClsTopicDTO;
import com.smallfish.zhiwei.dto.resp.ClsTopicListRespDTO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
@Slf4j
@SpringBootTest // 启动 Spring 上下文
public class ClsIntegrationTest {

    @Autowired
    private ClsTopicTool topicTool;

    @Autowired
    private ClsLogQueryTool queryTool;

    private static final String TEST_REGION = "ap-chongqing";

    @Test
    public void testFullWorkflow() {
        // ==========================================
        // 第一步：测试获取主题列表 (模拟 AI 寻找 ID)
        // ==========================================
        log.info("正在获取主题列表...");
        ClsTopicListRespDTO topicResp = topicTool.getLogTopics(TEST_REGION);

        // 验证调用成功
        Assertions.assertTrue(topicResp.isSuccess(), "获取主题列表失败: " + topicResp.getMessage());
        log.info(" 获取成功，消息: {}", topicResp.getMessage());

        if (topicResp.getTopics() == null || topicResp.getTopics().isEmpty()) {
            log.warn("⚠ 该地域下没有日志主题，测试提前结束。");
            return;
        }

        // 打印一下找到的主题，方便肉眼确认
        topicResp.getTopics().forEach(t ->
                log.info("   - 服务名: {} | ID: {} | 日志集: {}", t.getTopicName(), t.getTopicId(), t.getLogsetId())
        );

        // ==========================================
        // 第二步：测试查询日志 (模拟 AI 查内容)
        // ==========================================
        // 自动取第一个 Topic 进行测试
        ClsTopicDTO targetTopic = topicResp.getTopics().get(0);
        String topicId = targetTopic.getTopicId();

        log.info("\n正在查询日志 | Topic: {} ({})", targetTopic.getTopicName(), topicId);

        ClsSearchRespDTO searchResp = queryTool.searchLogs(
                TEST_REGION,
                topicId,
                "*", // 查询所有日志
                60   // 最近 60 分钟
        );

        // 验证查询成功
        Assertions.assertTrue(searchResp.isSuccess(), "日志查询失败: " + searchResp.getMessage());
        log.info(" 查询成功 | RequestId: {}", searchResp.getRequestId());
        log.info(" 命中条数: {}", searchResp.getLogCount());

        // 检查具体日志内容
        if (searchResp.getLogs() != null && !searchResp.getLogs().isEmpty()) {
            ClsLogEntryDTO firstLog = searchResp.getLogs().get(0);
            log.info(" 最新一条日志时间: {}", firstLog.getTime());
            log.info(" 日志内容预览: {}", firstLog.getContent());
        } else {
            log.info("️ 最近 60 分钟内该服务没有产生日志。");
        }
    }
}