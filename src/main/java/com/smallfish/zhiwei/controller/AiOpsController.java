package com.smallfish.zhiwei.controller;

import com.smallfish.zhiwei.dto.req.ChatReqDTO;
import com.smallfish.zhiwei.dto.resp.ChatRespDTO;
import com.smallfish.zhiwei.service.chat.AutoOpsGraphService;
import com.smallfish.zhiwei.service.chat.AutoOpsService;
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

@RestController
@RequestMapping("/api/ai_ops")
@RequiredArgsConstructor
public class AiOpsController {

    // graph 模式
    private final AutoOpsGraphService autoOpsGraphService;
    // Multi-Agent 模式
    private final AutoOpsService autoOpsService;

    /*
    *  ai ops 接口
    * */
    @PostMapping(value = "/troubleshoot", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatRespDTO>> troubleshoot(@Validated @RequestBody ChatReqDTO req) {
        String conversationId = req.getConversationId();
        if (conversationId == null || conversationId.isEmpty()) {
            conversationId = UUID.randomUUID().toString();
        }
        return autoOpsGraphService.streamTroubleshooting(req.getQuery(), conversationId);
    }
}
