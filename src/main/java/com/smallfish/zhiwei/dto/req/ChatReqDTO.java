package com.smallfish.zhiwei.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/*
*  对话 agent 请求参数
* */
@Data
public class ChatReqDTO {

    /*
    *  用户的问题
    * */
    @NotBlank(message = "问题不能为空")
    private String query;

    /*
    *  会话 id
    * */
    private String conversationId;
}
