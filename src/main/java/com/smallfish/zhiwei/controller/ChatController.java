package com.smallfish.zhiwei.controller;

import cn.hutool.core.util.StrUtil;
import com.smallfish.zhiwei.common.result.Result;
import com.smallfish.zhiwei.dto.req.ChatReq;
import com.smallfish.zhiwei.dto.resp.ChatResp;
import com.smallfish.zhiwei.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;


/*
* ai 对话服务控制器
* */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;

    @PostMapping("/send")
    public Result<ChatResp> sendMessage(@RequestBody ChatReq req) {
        if (StrUtil.hasBlank(req.getQuery())) {
            return Result.error(400, "问题不能为空");
        }
        // 2. 处理会话 ID (Conversation ID)
        // 如果前端没传会话ID，说明是新开启的对话，生成一个新的 UUID
        // 如果前端传了，就用前端传的，这样才能保持上下文记忆
        String conversationId = req.getConversationId();
        if (StrUtil.hasBlank(conversationId)) {
            conversationId = UUID.randomUUID().toString();
        }
        String answerContent = chatService.executeChat(req.getQuery(), conversationId);
        // 4. 封装响应对象
        ChatResp resp = new ChatResp();
        resp.setAnswer(answerContent);       // 设置回答内容
        resp.setConversationId(conversationId); // 将会话ID返还给前端，下次前端要带上这个ID
        return Result.success(resp);
    }
}
