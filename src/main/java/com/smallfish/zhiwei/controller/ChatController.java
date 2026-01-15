package com.smallfish.zhiwei.controller;

import cn.hutool.core.util.StrUtil;
import com.smallfish.zhiwei.common.enums.ChatEventType;
import com.smallfish.zhiwei.common.result.Result;
import com.smallfish.zhiwei.dto.req.ChatReqDTO;
import com.smallfish.zhiwei.dto.resp.ChatRespDTO;
import com.smallfish.zhiwei.service.chat.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.UUID;


/*
* ai 对话服务控制器
* */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;


    /*
    * 阻塞输出
    * */
    @PostMapping("/send")
    public Result<ChatRespDTO> sendMessage(@Validated @RequestBody ChatReqDTO req) {
        // 1. 处理会话 ID (Conversation ID)
        // 如果前端没传会话ID，说明是新开启的对话，生成一个新的 UUID
        // 如果前端传了，就用前端传的，这样才能保持上下文记忆
        String conversationId = req.getConversationId();
        if (StrUtil.hasBlank(conversationId)) {
            conversationId = UUID.randomUUID().toString();
        }
        String answerContent = chatService.executeChat(req.getQuery(), conversationId);

        ChatRespDTO resp = ChatRespDTO.builder()
                .answer(answerContent)
                .conversationId(conversationId)
                .type(ChatEventType.CONTENT.getValue())
                .build();
        return Result.success(resp);
    }

    /*
    *  流式输出
    * */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatRespDTO>> streamChat(@Validated @RequestBody ChatReqDTO req) {
        String conversationId = StrUtil.isBlank(req.getConversationId())
                ? UUID.randomUUID().toString()
                : req.getConversationId();

        return chatService.streamChat(req.getQuery(), conversationId);
    }
}
