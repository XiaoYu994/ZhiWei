package com.smallfish.zhiwei.common.enums;

import lombok.Getter;

/**
 * 聊天消息事件类型
 */
@Getter
public enum ChatEventType {

    /**
     * 普通内容 (打字机效果)
     */
    CONTENT("content"),

    /**
     * 工具调用日志 (例如：正在查询监控...)
     */
    TOOL_LOG("tool_log"),

    /*
    心跳
    * */
    KEEP_ALIVE("keep_alive"),

    /**
     * 错误信息
     */
    ERROR("error"),

    /**
     * 结束标记 (前端收到后关闭连接)
     */
    DONE("done");

    private final String value;

    ChatEventType(String value) {
        this.value = value;
    }
}
