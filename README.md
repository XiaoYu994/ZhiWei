# 智维 (ZhiWei) - 智能运维与问答助手

> 基于 Spring Boot 3 + Spring AI Alibaba 构建的新一代智能运维 Agent 系统。

## 📖 项目简介

**智维 (ZhiWei)** 是一个集成了 **RAG (检索增强生成)** 与 **AI Ops (智能运维)** 的综合性平台。它利用大语言模型（Qwen）的推理能力，结合企业私有知识库（Milvus）和实时监控数据（Prometheus），为运维团队提供自动化的故障诊断、知识检索和决策支持。

## ✨ 核心功能

*   **🧠 智能知识库 (RAG)**
    *   支持多种格式文档上传与向量化存储。
    *   基于语义的高精度检索，解决大模型幻觉问题。
    *   支持文档分片、混合检索（关键词+向量）。

*   **🛡️ 自动化运维 (AI Ops)**
    *   **智能诊断**: 自动分析报警信息，结合知识库生成排查思路。
    *   **故障图谱**: 自动生成故障排查流程图 (Mermaid/Graphviz)。
    *   **日志分析**: 集成腾讯云 CLS，自动检索相关错误日志。

*   **📊 全栈监控体系**
    *   内置 Prometheus + Alertmanager + Node Exporter。
    *   支持自定义告警规则与 Webhook 通知（钉钉/飞书）。
    *   应用级指标监控 (Spring Boot Actuator)。

*   **💬 现代化交互**
    *   支持 SSE (Server-Sent Events) 流式对话。
    *   Markdown 格式富文本回复。

## 🛠️ 技术栈

| 类别 | 技术组件 | 说明 |
| :--- | :--- | :--- |
| **开发语言** | Java 21 | 拥抱最新 LTS 版本特性 |
| **核心框架** | Spring Boot 3.2.0 | 现代化微服务基座 |
| **AI 框架** | Spring AI Alibaba | 接入通义千问 (Qwen) 等国产大模型 |
| **向量数据库** | Milvus 2.x | 高性能分布式向量检索 |
| **缓存/消息** | Redis 7.0 | 会话记忆与热点数据缓存 |
| **监控告警** | Prometheus + Alertmanager | 云原生监控标准 |
| **日志服务** | Tencent Cloud CLS | 云端日志存储与分析 |
| **工具库** | Hutool, Lombok, Gson | 提高开发效率 |

## 🚀 快速开始

### 1. 环境要求
*   **JDK**: 21+
*   **Docker**: 20.10+
*   **Maven**: 3.8+

### 2. 配置文件
项目核心配置位于 `src/main/resources/application.yaml`。推荐使用环境变量覆盖敏感信息：

```bash
export DASHSCOPE_API_KEY="sk-xxxxxxxx"      # 阿里云百炼 API Key
export TENCENT_CLS_AK="AKIDxxxxxxxx"        # 腾讯云 SecretId
export TENCENT_CLS_SK="xxxxxxxxxxxx"        # 腾讯云 SecretKey
export PROMETHEUS_URL="http://localhost:9090"
export WEBHOOK_URL="https://oapi.dingtalk.com/..."
```

### 3. 启动部署

本项目支持 **全栈 Docker 部署** 和 **混合部署** 模式。详细指南请查阅 [DEPLOY.md](DEPLOY.md)。

**简易启动 (Docker Compose):**

```bash
# 1. 启动所有服务 (App + Milvus + Prometheus)
docker-compose up -d

# 2. 查看日志
docker logs -f zhiwei-app
```

访问地址：
*   **Web 服务**: `http://localhost:9901`
*   **Milvus 管理 (Attu)**: `http://localhost:8000`
*   **Prometheus**: `http://localhost:9090`

## 📂 目录结构

```text
zhiwei
├── src/main/java/com/smallfish/zhiwei
│   ├── client          # 第三方客户端配置 (Milvus 等)
│   ├── config          # Spring 配置类
│   ├── controller      # Web 接口层 (AI Ops, Chat)
│   ├── service
│   │   ├── chat        # 对话逻辑与 Agent 实现
│   │   ├── ingestion   # 知识库导入服务
│   │   ├── retrieval   # 向量检索服务
│   │   └── storage     # 文件存储服务
│   └── model           # 数据模型
├── src/main/resources
│   ├── prometheus      # Prometheus 监控配置
│   ├── alertmanager    # Alertmanager 告警配置
│   └── static          # 前端静态资源
├── docker-compose.yml  # 容器编排文件
├── Dockerfile          # 应用镜像构建文件
└── DEPLOY.md           # 详细部署文档
```

## 🤝 贡献与支持
欢迎提交 Issue 和 Pull Request。如有问题，请联系开发团队。
