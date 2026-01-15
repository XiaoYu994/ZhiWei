package com.smallfish.zhiwei.agent.manager;

import com.tencentcloudapi.cls.v20201016.ClsClient;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CLS 客户端连接管理器 (Factory & Pool 模式)。
 * <p>
 * 负责维护腾讯云 CLS 的长连接客户端。
 * 使用 {@link java.util.concurrent.ConcurrentHashMap} 缓存不同地域的 Client，
 * 避免每次请求重复建立 TCP 连接，显著提升高并发下的响应速度。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClsClientManager {

    private final Credential credential;

    @Value("${tencent.cls.default-region}")
    private String defaultRegion;

    // 连接池缓存
    private final Map<String, ClsClient> clientCache = new ConcurrentHashMap<>();

    /**
     * 获取指定地域的 CLS 客户端。
     * <p>
     * 如果缓存中存在，直接返回；否则创建新连接并存入缓存。
     * SDK v3 会自动根据 region 路由到正确的 Endpoint。
     * </p>
     *
     * @param region 地域代码 (如 "ap-guangzhou")
     * @return 初始化好并配置了超时时间的 {@link ClsClient}
     */
    public ClsClient getClient(String region) {
        // 处理默认地域逻辑
        String targetRegion = (region == null || region.isBlank()) ? defaultRegion : region;

        return clientCache.computeIfAbsent(targetRegion, r -> {
            log.info("CLS Manager 初始化新地域连接: {}", r);

            HttpProfile httpProfile = new HttpProfile();
//            httpProfile.setEndpoint(r + ".cls.tencentcloudapi.com");
            httpProfile.setConnTimeout(10); // 连接超时
            httpProfile.setReadTimeout(30); // 读取超时

            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);

            return new ClsClient(credential, r, clientProfile);
        });
    }

    public String getDefaultRegion() {
        return defaultRegion;
    }
}