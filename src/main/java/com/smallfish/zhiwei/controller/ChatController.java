package com.smallfish.zhiwei.controller;

import com.smallfish.zhiwei.common.result.Result;
import com.smallfish.zhiwei.dto.req.ChatReq;
import com.smallfish.zhiwei.dto.resp.ChatResp;
import com.smallfish.zhiwei.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


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
        if (req.getQuery() == null || req.getQuery().trim().isEmpty()) {
            return Result.error(400, "问题不能为空");
        }
        ChatResp resp = chatService.chat(req);
        return Result.success(resp);
    }
}
