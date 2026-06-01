# bifro-test-suite

[English](./README.md)

bifro-test-suite 是一个分布式 MQTT 压测平台，用于在多节点环境下执行大规模连接、发布、订阅以及混合负载测试。

## 功能特性

- 支持 MQTT 3.1.1 和 MQTT 5.0
- 支持连接、发布订阅、仅发布、仅订阅和单消息等测试场景
- 支持多 Worker 节点分布式执行，并跟踪节点状态与心跳
- 支持 Broker 分组和任务分组管理
- 支持无认证、用户名密码、BYOC、IoT Core 和 mTLS 证书认证
- 提供运行时指标、任务报告、吞吐量、延迟、连接成功率和客户端级统计
- 提供 React 管理界面，并支持国际化

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 后端 | Java 17, Spring Boot 3.5.14 |
| 响应式运行时 | Vert.x 5.0.12 |
| 集群协调 | Hazelcast 5.3.5 |
| 数据库 | MongoDB Reactive |
| 前端 | React, Vite, TypeScript, Ant Design |

## 仓库结构

```text
bifro-test-suite/
├── bifro-test-bed/              # Spring Boot 应用
├── bifro-test-fe/               # React 管理界面
├── test-suite-framework/        # Pipeline 与状态机框架
├── test-suite-common/           # 公共枚举、阶段、事件和指标
├── test-suite-mqtt-client/      # MQTT 客户端封装与认证策略
├── test-suite-mqtt/             # MQTT 任务实现
├── test-suite-worker-api/       # Worker API 契约
├── test-suite-worker/           # Worker 运行时与 Pipeline 阶段
├── test-suite-certificates/     # 证书管理
├── test-suite-task-management/  # 任务元数据、报告和 API
├── test-suite-cluster-management/ # 集群节点管理
├── test-suite-resource-management/ # Broker、分组、画像和证书 API
├── test-suite-security/         # 认证与用户管理
└── test-suite-audit/            # 审计日志
```

## 构建

### 前置条件

- JDK 17+
- Maven 3.8+
- Node.js 18+
- pnpm
- MongoDB 4.4+

### 后端

```bash
mvn clean install
```

如果只构建后端模块并跳过前端构建：

```bash
mvn -U clean install -DskipFrontend=true
```

### 前端

```bash
cd bifro-test-fe
pnpm install
pnpm build
```

## 本地运行

启动后端：

```bash
mvn spring-boot:run -pl bifro-test-bed
```

启动前端开发服务器：

```bash
cd bifro-test-fe
pnpm dev
```

默认本地访问地址：

- 管理界面：`http://localhost:8081/admin`
- Swagger UI：`http://localhost:8081/swagger-ui.html`

## 任务模板

| 模板 | 类型 | 说明 |
| --- | --- | --- |
| `CONN_STANDARD` | 连接 | 标准连接测试 |
| `CONN_IMMEDIATE_DISCONNECT` | 连接 | 连接后立即断开 |
| `PUBSUB_STANDARD` | 发布订阅 | 标准发布订阅测试 |
| `PUBSUB_PUB_ONLY` | 发布订阅 | 仅发布测试 |
| `PUBSUB_SUB_ONLY` | 发布订阅 | 仅订阅测试 |
| `PUBSUB_SINGLE_MESSAGE` | 发布订阅 | 单消息发布测试 |
| `PUBSUB_SINGLE_SUBSCRIBE` | 发布订阅 | 单次订阅测试 |

## 开发检查

```bash
mvn checkstyle:check
mvn test

cd bifro-test-fe
pnpm lint
pnpm test
```

## 安全说明

项目不再内置固定默认密码。如果启用安全配置、用户库为空且没有配置初始用户，服务会自动创建 `admin`
用户，生成随机密码，并只把明文密码写入 `conf/initial-admin-password`。请保护该文件，并在首次登录后
轮换密码。

生产部署也可以在 `bifro.security.users` 下显式配置初始用户：

```yaml
bifro:
  security:
    enabled: true
    users:
      - username: your-admin
        password: "{bcrypt}$2a$10$..."
        roles:
          - ADMIN
```

不要在公网或未受信网络中使用本地开发配置运行服务。生产部署应显式提供认证配置，并使用适合目标环境的
MongoDB 部署。

## 许可证

本项目使用 Apache License, Version 2.0。详见 [LICENSE](./LICENSE)。
