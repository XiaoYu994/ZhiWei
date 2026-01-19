# 部署指南 (混合部署模式)

本指南适用于：
1. **基础设施 (Docker)**：Milvus 向量数据库 + 监控系统 (Prometheus/Alertmanager)。
2. **应用服务 (JAR)**：Java 后端直接运行在服务器宿主机。
3. **Redis**：使用服务器现有的 Redis。

## 1. 启动基础设施
确保 `docker-compose.yml` 在服务器上，运行：

```bash
docker-compose up -d
```

这将启动以下服务：
- **Milvus**: 端口 `19530` (数据库), `8000` (Attu 管理界面)
- **Prometheus**: 端口 `9090` (监控面板)
- **Alertmanager**: 端口 `9093` (报警管理)
- **Node Exporter**: 端口 `9100` (服务器监控)

## 2. 运行 Java 应用
将打包好的 `zhiwei-1.0-SNAPSHOT.jar` 上传到服务器，使用以下命令启动。
注意：我们通过 `-D` 参数指定连接本地的 Milvus 和 Redis。

```bash
# 假设 Redis 在本机 6379，密码为 xhy_redis
nohup java -jar \
  -Dspring.data.redis.host=localhost \
  -Dspring.data.redis.port=6379 \
  -Dspring.data.redis.password=xhy_redis \
  -Dmilvus.host=localhost \
  -Dmilvus.port=19530 \
  -DDASHSCOPE_API_KEY="你的阿里云Key" \
  -DTENCENT_CLS_AK="你的腾讯云AK" \
  -DTENCENT_CLS_SK="你的腾讯云SK" \
  -DPROMETHEUS_URL="http://localhost:9090" \
  -DWEBHOOK_URL="你的钉钉Webhook" \
  zhiwei-1.0-SNAPSHOT.jar > app.log 2>&1 &
```

## 3. 验证
- **应用日志**: `tail -f app.log`
- **Milvus 管理**: 访问 `http://服务器IP:8000`
- **Prometheus**: 访问 `http://服务器IP:9090`，查看 Targets 确保 `zhiwei-agent` 状态为 UP。
