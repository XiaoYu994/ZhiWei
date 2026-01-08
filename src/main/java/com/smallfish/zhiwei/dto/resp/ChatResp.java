package com.smallfish.zhiwei.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatResp {
    /*
    *  大模型的回答
    * */
    private String answer;

    /*
    *  引用了哪些文件
    * */
    private List<String> sources;

    /*
    *  会话 ID
    * */
    private String  conversationId;
}
