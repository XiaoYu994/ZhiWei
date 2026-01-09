package com.smallfish.zhiwei.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatRespDTO {
    /*
    *  大模型的回答
    * */
    private String answer;


    /*
    *  会话 ID
    * */
    private String  conversationId;
}
