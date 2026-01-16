package com.smallfish.zhiwei.service.chat;

import cn.hutool.core.date.DateUtil;
import com.smallfish.zhiwei.dto.req.AlertWebhookDTO;
import com.smallfish.zhiwei.service.base.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoOpsService {
    private final ChatService chatService;
    private final NotificationService notificationService;

    private final StringRedisTemplate redisTemplate;
    // 常量定义
    private static final long LOCK_EXPIRE_MINUTES = 15;
    private static final String LOCK_KEY_PREFIX = "zhiwei:ops:lock:";

    /**
     * 核心排查逻辑 - 异步执行
     * 这里的 "aiTaskExecutor" 对应 AsyncConfig 里定义的 Bean 名字
     */
    @Async("aiTaskExecutor")
    public void processAlertAsync(AlertWebhookDTO.Alert alert) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);

        // 1. 提取元数据
        String alertName = alert.getLabels().getOrDefault("alertname", "未知告警");
        String instance = alert.getLabels().getOrDefault("instance", "未知实例");
        String tenantId = alert.getLabels().getOrDefault("tenant_id", "default");
        String severity = alert.getLabels().getOrDefault("severity", "warning");
        String description = alert.getAnnotations().getOrDefault("description", "无描述");
        // 2. 【R】Redis 告警收敛 (分布式原子锁)
        // 逻辑：同一租户、同一实例、同一告警，在 15 分钟内只处理一次
        String lockKey = String.format("%s%s:%s:%s", LOCK_KEY_PREFIX, tenantId, alertName, instance);

        // SET key value NX EX 900 (原子操作，无需 synchronized)
        Boolean isLocked = redisTemplate.opsForValue().setIfAbsent(lockKey, "PROCESSING", LOCK_EXPIRE_MINUTES, TimeUnit.MINUTES);

        if (Boolean.FALSE.equals(isLocked)) {
            log.info("[{}]告警收敛生效：{} 锁定中，跳过本次 AI 分析", traceId, lockKey);
            // 这里可以做一个 Redis 计数器 increment，记录被拦截的次数，用于周报统计
            return;
        }

        log.info("[{}] AI 介入排查 | 租户: {} | 告警: {}", traceId, tenantId, alertName);

        try {
            // 2. 构造 Prompt (提示词工程)
            // 使用 Markdown 格式增强可读性
            String prompt = String.format(
                    "【系统自动诊断请求】\n" +
                            "检测到 **%s** 级别告警，请立即排查！\n\n" +
                            "**告警快照**\n" +
                            "> 告警名称：%s\n" +
                            "> 故障实例：%s\n" +
                            "> 详细描述：%s\n\n" +
                            "**排查任务**\n" +
                            "1. 调用 `queryPrometheus` 查询该实例 **过去30分钟** 的关键指标（CPU、内存、HTTP错误率等）。\n" +
                            "2. 根据监控数据判断是否为误报。\n" +
                            "3. 输出一份简短的分析报告（包含数据证据）。",
                    severity, alertName, instance, description
            );

            // 3. 调用 AI (同步等待结果，但因为我们在异步线程里，所以不影响主流程)
            // 使用特定的前缀 "AUTO-" 方便后续在 ChatMemory 里区分自动会话和人工会话
            String conversationId = "AUTO-" + instance + "-" + DateUtil.today();
            String aiAnalysis = chatService.executeChat(prompt, conversationId);

            // 4. 推送通知
            String title = "AI 诊断报告: " + alertName;

            // 组装最终发给群里的内容
            String finalReport = "### " + title + "\n\n" +
                    "**时间**: " + DateUtil.now() + "\n" +
                    "**实例**: `" + instance + "`\n\n" +
                    "--- \n\n" +
                    aiAnalysis; // AI 的回复

            notificationService.sendMarkdown(title, finalReport);

            log.info("[{}] 诊断完成，通知已发送", traceId);

            // 关键：发生异常时释放锁，允许稍后重试（否则要等15分钟）
            redisTemplate.delete(lockKey);
        } catch (Exception e) {
            log.error("[{}] AI 诊断过程发生异常", traceId, e);
            notificationService.sendMarkdown("诊断失败", "AI 排查过程中发生系统异常: " + e.getMessage());
        }
    }
}
