package com.smallfish.zhiwei.controller;

import com.smallfish.zhiwei.common.enums.ChatEventType;
import com.smallfish.zhiwei.dto.req.ChatReqDTO;
import com.smallfish.zhiwei.dto.resp.ChatRespDTO;
import com.smallfish.zhiwei.service.chat.AutoOpsGraphService;
import com.smallfish.zhiwei.service.chat.AutoOpsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.UUID;

@Slf4j
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
        // 1. 先获取原始值
        String rawId = req.getConversationId();

        // 2. 定义最终变量（这个变量初始化后不会再变，符合 Lambda 要求）
        final String conversationId = (rawId == null || rawId.isEmpty())
                ? UUID.randomUUID().toString()
                : rawId;
        // 1. 发送开场白 (立即发送)
        Flux<ServerSentEvent<ChatRespDTO>> startFlux = Flux.just(
                buildSSE(conversationId, " 收到请求，正在启动多智能体协作引擎...\n\n(分析过程约需 15-30 秒，请耐心等待)")
        );

        // 2. 异步执行 Graph 并生成报告流
        Flux<ServerSentEvent<ChatRespDTO>> processFlux = Flux.defer(() -> {
            // 在这里调用 Service 的同步方法，但这整个块是在 boundedElastic 线程池跑的
            return Flux.just(req.getQuery())
                    .map(q -> {
                        // A. 执行耗时的 Graph 分析
                        log.info("开始执行 Graph 分析...");
                        return autoOpsGraphService.executeAnalysis(q);
                    })
                    .flatMap(stateOpt -> {
                        if (stateOpt.isEmpty()) {
                            return Flux.just(buildSSE(conversationId, "\n 分析失败，Agent 未返回结果。"));
                        }

                        // B. 提取最终报告
                        String report = autoOpsGraphService.extractFinalReport(stateOpt.get());

                        // C. 将长文本切分为字符块，模拟打字机效果
                        // 比如每 20ms 发送 5 个字符
                        return splitTextToFlux(report)
                                .map(chunk -> buildSSE(conversationId, chunk));
                    });
        }).subscribeOn(Schedulers.boundedElastic()); // 关键：把重活扔给 Elastic 线程池

        // 3. 结束信号
        Flux<ServerSentEvent<ChatRespDTO>> endFlux = Flux.just(
                ServerSentEvent.builder(ChatRespDTO.builder().type(ChatEventType.DONE.getValue()).build()).build()
        );

        return Flux.concat(startFlux, processFlux, endFlux);
    }
    // 辅助：构建 SSE 对象
    private ServerSentEvent<ChatRespDTO> buildSSE(String id, String text) {
        return ServerSentEvent.builder(
                ChatRespDTO.builder()
                        .conversationId(id)
                        .answer(text)
                        .type(ChatEventType.CONTENT.getValue())
                        .build()
        ).build();
    }

    // 辅助：将长字符串切分成流
    private Flux<String> splitTextToFlux(String text) {
        if (text == null || text.isEmpty()) return Flux.empty();

        // 简单切分：按字符切分，或者按固定长度切分
        int chunkSize = 10; // 每次发10个字
        int length = text.length();

        return Flux.range(0, (length + chunkSize - 1) / chunkSize)
                .map(i -> {
                    int start = i * chunkSize;
                    int end = Math.min(length, start + chunkSize);
                    return text.substring(start, end);
                })
                .delayElements(Duration.ofMillis(50)); // ⚡️ 增加一点延迟，制造“正在生成”的视觉感
    }
}
