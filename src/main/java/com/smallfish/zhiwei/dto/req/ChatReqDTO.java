package com.smallfish.zhiwei.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatReqDTO {

    @NotBlank(message = "问题不能为空")
    private String query;     // 用户的问题
    private String conversationId;   // 会话 id
}
