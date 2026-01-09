package com.smallfish.zhiwei.dto.req;

import lombok.Data;

@Data
public class ChatReqDTO {
    private String query;     // 用户的问题
    private String conversationId;   // 会话 id
}
