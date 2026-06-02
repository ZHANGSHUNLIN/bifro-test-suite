<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# bifro-test-suite

[![CI](https://github.com/ZHANGSHUNLIN/bifro-test-suite/actions/workflows/ci.yml/badge.svg)](https://github.com/ZHANGSHUNLIN/bifro-test-suite/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](./LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://openjdk.org/)
[![MQTT](https://img.shields.io/badge/MQTT-3.1.1%20%7C%205.0-green.svg)](https://mqtt.org/)

[English](./README.md)

bifro-test-suite 是一个分布式 MQTT 压测平台，用于在高连接数、发布订阅流量和混合负载场景下验证
MQTT Broker、IoT 平台以及消息系统的稳定性和性能表现。

项目提供 Spring Boot 后端、React 管理控制台、多节点 Worker 执行、任务编排、运行时指标和测试报告管理，
适合用于 MQTT 系统的日常压测、容量评估和回归验证。

## 为什么选择 bifro-test-suite

- 通过浏览器管理界面运行 MQTT 3.1.1 和 MQTT 5.0 压测任务。
- 支持连接、仅发布、仅订阅、发布订阅、单消息和立即断开等典型测试场景。
- 支持多 Worker 节点分布式执行，并跟踪节点状态、心跳和任务分配。
- 支持 Broker 目标、任务分组、测试画像、证书和执行报告管理。
- 在任务运行期间查看吞吐量、延迟、连接成功率、客户端状态和运行时指标。
- 内置无认证、支持占位符的用户名密码和 mTLS 证书等认证模式。

## 架构

```text
                  +-----------------------------+
                  | React Administration Console |
                  +--------------+--------------+
                                 |
                                 v
                  +-----------------------------+
                  | Spring Boot Test Bed         |
                  | REST API, task orchestration |
                  +------+------------+---------+
                         |            |
               metadata  |            | cluster events
                         v            v
                  +-----------+   +------------------+
                  | MongoDB   |   | Hazelcast/Vert.x |
                  +-----------+   +--------+---------+
                                            |
                                            v
                             +-----------------------------+
                             | Distributed Worker Runtime   |
                             | MQTT clients and pipelines   |
                             +---------------+-------------+
                                             |
                                             v
                                  +--------------------+
                                  | MQTT Broker Targets |
                                  +--------------------+
```

## 功能亮点

| 领域 | 能力 |
| --- | --- |
| MQTT 负载 | 连接、发布订阅、仅发布、仅订阅、单消息和立即断开场景 |
| 协议支持 | MQTT 3.1.1 和 MQTT 5.0 |
| 认证模式 | 无认证、支持客户端占位符的用户名密码和 mTLS 证书认证 |
| 分布式执行 | Worker 节点、节点心跳、集群成员管理和节点级任务分配 |
| 资源管理 | Broker 分组、任务分组、测试画像、证书和可复用任务配置 |
| 可观测性 | 运行时指标、测试报告、延迟、吞吐量、连接成功率和客户端级统计 |
| 管理界面 | React、TypeScript、Ant Design、国际化界面、角色管理和审计日志 |

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 后端 | Java 17, Spring Boot 3.5.x |
| 响应式运行时 | Vert.x 5.x |
| 集群协调 | Hazelcast 5.x |
| 数据库 | MongoDB Reactive |
| 前端 | React, Vite, TypeScript, Ant Design |
| 构建 | Maven, pnpm |

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 18+
- pnpm
- MongoDB 4.4+

### 源码构建

构建全部后端模块：

```bash
mvn clean install
```

只构建后端模块并跳过前端打包：

```bash
mvn -U clean install -DskipFrontend=true
```

构建前端：

```bash
cd bifro-test-fe
pnpm install
pnpm build
```

### 本地运行

先启动 MongoDB，然后运行后端应用：

```bash
mvn spring-boot:run -pl bifro-test-bed
```

后端默认使用 `bifro-test-bed/src/main/resources/application.yml`：

- 服务端口：`8081`
- MongoDB：`localhost:27017`，数据库 `bifro-test-suite`
- 管理界面：`http://localhost:8081/admin`
- Swagger UI：`http://localhost:8081/swagger-ui.html`

前端开发模式：

```bash
cd bifro-test-fe
pnpm install
pnpm dev
```

## 发布产物

手动 GitHub Release 会生成多种产物，适配不同部署方式：

| 产物 | 说明 |
| --- | --- |
| `bifro-test-bed-<version>-all.tar.gz` | 完整包，包含后端运行环境和已构建的管理界面 |
| `bifro-test-suite-<version>-backend.tar.gz` | 后端-only 包，不包含前端静态资源 |
| `bifro-test-suite-<version>-frontend.zip` | 前端-only 包，内容来自 `dist` 构建产物 |
| `bifro-test-suite-<version>-sbom.json` | CycloneDX 软件物料清单 |
| `SHA256SUMS` | 发布产物校验和 |

## 任务模板

| 模板 | 用途 |
| --- | --- |
| `CONN_STANDARD` | 建立并保持标准 MQTT 连接 |
| `CONN_IMMEDIATE_DISCONNECT` | 客户端连接后立即断开 |
| `PUBSUB_STANDARD` | 执行协调的发布订阅流量 |
| `PUBSUB_PUB_ONLY` | 执行仅发布流量 |
| `PUBSUB_SUB_ONLY` | 执行仅订阅流量 |
| `PUBSUB_SINGLE_MESSAGE` | 执行单消息发布负载 |
| `PUBSUB_SINGLE_SUBSCRIBE` | 执行单次订阅负载 |

## 仓库结构

```text
bifro-test-suite/
├── bifro-test-bed/                 # Spring Boot 应用和发布包组装
├── bifro-test-fe/                  # React 管理控制台
├── test-suite-framework/           # Pipeline 与状态机框架
├── test-suite-common/              # 公共领域对象、阶段、事件和指标
├── test-suite-mqtt-client/         # MQTT 客户端封装与认证策略
├── test-suite-mqtt/                # MQTT 负载实现
├── test-suite-worker-api/          # Worker API 契约
├── test-suite-worker/              # Worker 运行时与 Pipeline 阶段
├── test-suite-certificates/        # 证书领域模型与证书服务
├── test-suite-task-management/     # 任务元数据、报告、运行状态和 API
├── test-suite-cluster-management/  # 集群成员与调度支持
├── test-suite-resource-management/ # Broker、分组、画像和证书 API
├── test-suite-security/            # 认证与系统用户管理
└── test-suite-audit/               # 审计日志支持
```

## 开发

运行后端测试：

```bash
mvn test
```

运行 Java 风格检查：

```bash
mvn checkstyle:check
```

运行 Apache RAT 许可证头检查：

```bash
mvn apache-rat:check -DskipTests -DskipFrontend=true
```

运行前端检查：

```bash
cd bifro-test-fe
pnpm lint
pnpm test
```

## 安全说明

项目不内置固定默认密码。如果启用安全配置、未配置初始用户且用户库为空，服务会自动创建 `admin` 用户，
生成随机密码，并只把密码写入一次：

```text
conf/initial-admin-password
```

请保护该文件，并在首次登录后轮换密码。

生产部署应在 `bifro.security.users` 下显式配置用户，并使用适合目标环境的 MongoDB 部署：

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

不要把本地开发配置直接暴露在未受信网络中。

## 参与贡献

欢迎提交贡献。提交变更前，请运行相关后端和前端检查，并尽量让每个 commit 聚焦在单个模块或行为变更上。

- [贡献指南](./CONTRIBUTING.md)
- [安全策略](./SECURITY.md)
- [行为准则](./CODE_OF_CONDUCT.md)

## 许可证

bifro-test-suite 使用 Apache License, Version 2.0。详见 [LICENSE](./LICENSE)。
