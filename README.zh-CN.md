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
- 使用外部数据库模式时需要 MongoDB 4.4+

### 源码构建

构建全部后端模块：

```bash
mvn clean install
```

只构建后端模块并跳过前端打包：

```bash
mvn -U clean install -DskipFrontend=true
```

针对已经归档到聚合目录下的 Maven 模块，建议用 artifactId 选择模块，例如：

```bash
mvn -pl :test-suite-worker -am -DskipFrontend=true test
```

构建前端：

```bash
cd bifro-test-fe
pnpm install
pnpm build
```

### 本地运行

运行后端应用：

```bash
mvn spring-boot:run -pl bifro-test-bed
```

源码启动会使用 `bifro-test-bed/src/main/resources/application.yml` 作为仅用于开发的默认配置：

- 服务端口：`8081`
- 存储模式：嵌入式 MongoDB，单控制面节点
- 管理界面：`http://localhost:8081/admin`
- Swagger UI：`http://localhost:8081/swagger-ui.html`

开发用的 `application.yml` 不会被打进发布应用 jar。发布包启动脚本会通过
`--spring.config.location=conf/` 从外部 `conf/` 目录加载配置，所以部署配置应统一在 `conf/` 下管理。

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

完整后端发布包包含 `conf/application.yml` 以及角色或模式覆盖配置：

- `conf/application-control.yml`
- `conf/application-worker.yml`
- `conf/application-embedded.yml`

在发布包根目录使用启动脚本，例如：

```bash
bin/bifro-test-suite.sh start
bin/bifro-test-suite.sh start control
bin/bifro-test-suite.sh start worker
bin/bifro-test-suite.sh start embedded
```

### 部署边界

当前版本不会把多控制面完整 HA 作为已交付能力。请按存储模式选择部署形态：

| 存储模式 | control/all 节点 | worker 节点 | MongoDB 要求 | 说明 |
| --- | --- | --- | --- | --- |
| `embedded` | 只能有一个 | 一个或多个 | control 节点不需要外部 MongoDB | 单控制面模式，不是 HA；第二个 embedded control/all 节点必须启动失败。 |
| `database` | 一个或多个 | 一个或多个 | control/all 节点需要外部 MongoDB | 多个 control 可以共享 MongoDB，但当前还不是完整 active-active HA；HA 设计完成前建议只暴露一个 active 写入口。 |

任意模式下，worker-only 节点都不需要 MongoDB。embedded 控制面换机时，需要显式迁移或挂载同一个 embedded
数据目录；自动接管默认关闭。

详细边界见 [存储模式设计](docs/arch/DESIGN-storage-modes.md) 和
[控制面最小安全设计](docs/arch/DESIGN-control-plane-minimal-safety.md)。

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
├── bifro-test-fe/                  # React 管理控制台
├── bifro-test-bed/                 # Spring Boot 应用和发布包组装
├── test-suite-shared/              # shared foundation 模块的 Maven 聚合工程
│   ├── test-suite-framework/       # Pipeline 与状态机框架
│   └── test-suite-common/          # 公共领域对象、阶段、事件和指标
├── test-suite-workers/             # worker 模块的 Maven 聚合工程
│   ├── test-suite-mqtt-client/     # MQTT 客户端封装与认证策略
│   ├── test-suite-mqtt/            # MQTT 负载实现
│   ├── test-suite-worker-api/      # Worker API 契约
│   └── test-suite-worker/          # Worker 运行时与 Pipeline 阶段
└── test-suite-control/             # control 模块的 Maven 聚合工程
    ├── test-suite-web-common/      # Web/API 响应与校验公共支持
    ├── test-suite-certificates/    # 证书领域模型与证书服务
    ├── test-suite-audit/           # 审计日志支持
    ├── test-suite-security/        # 认证与系统用户管理
    ├── test-suite-task-management/ # 任务元数据、报告、运行状态和 API
    ├── test-suite-cluster-management/  # 集群成员与调度支持
    └── test-suite-resource-management/ # Broker、分组、画像和证书 API
```

根 Maven reactor 只直接引用三个聚合工程和 `bifro-test-bed`。源码模块已经物理归档到对应聚合工程目录下。

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

源码启动时该路径相对仓库根目录；发布包启动时该路径相对解压后的发布包根目录，例如
`<package>/conf/initial-admin-password`。

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
