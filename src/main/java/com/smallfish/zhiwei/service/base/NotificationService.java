package com.smallfish.zhiwei.service.base;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


/*
*  发送通知服务层
* */
@Slf4j
@Service
public class NotificationService {

    // notification.webhook-url: https://oapi.dingtalk.com/robot/send?access_token=xxxx
    @Value("${notification.webhook-url}")
    private String webhookUrl;

    /**
     * 发送 Markdown 消息 (推荐)
     * @param title 消息标题 (会在弹窗中显示)
     * @param content Markdown 格式的正文
     */
    public void sendMarkdown(String title, String content) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.warn(" 未配置告警机器人 Webhook，跳过发送");
            return;
        }

        try {
            // 1. 构建钉钉 Markdown 消息体
            // 文档: https://open.dingtalk.com/document/orgapp/custom-robot-access
            JSONObject markdown = JSONUtil.createObj()
                    .set("title", title)
                    .set("text", content);

            JSONObject message = JSONUtil.createObj()
                    .set("msgtype", "markdown")
                    .set("markdown", markdown);

            try (HttpResponse response = HttpRequest.post(webhookUrl)
                    .body(message.toString())
                    .timeout(5000)
                    .execute()) {

                // 1. 检查状态码 (防御性编程，防止钉钉挂了我们还以为成功了)
                if (!response.isOk()) {
                    log.error("告警通知发送失败，状态码: {}, 响应: {}",
                            response.getStatus(), response.body());
                    return;
                }

                // 2. 获取结果 (此时调用 body 是安全的)
                String result = response.body();
                log.info("告警通知已发送 | 响应: {}", result);
            }
        } catch (Exception e) {
            log.error("发送告警通知失败", e);
        }
    }

    // 如果你是飞书 (Feishu)，请使用下面的结构替换上面的 message 构建逻辑：
    /*
    JSONObject contentObj = JSONUtil.createObj()
            .set("title", title)
            .set("content", JSONUtil.createArray().add(JSONUtil.createArray().add(
                    JSONUtil.createObj().set("tag", "text").set("text", content)
            )));
    JSONObject message = JSONUtil.createObj()
            .set("msg_type", "post")
            .set("content", JSONUtil.createObj().set("post", JSONUtil.createObj().set("zh_cn", contentObj)));
    */
}