package com.smallfish.zhiwei.dto.req;

import lombok.Data;

@Data
public class ChatReq {
    private String query;     // 用户的问题
    private boolean useRag;   // 开关：是否开启知识库搜索（方便对比测试）
}
