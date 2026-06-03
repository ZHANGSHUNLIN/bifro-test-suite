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

[简体中文](./README.zh-CN.md)

bifro-test-suite is a distributed MQTT stress testing platform for validating brokers, IoT platforms, and
message-oriented systems under high connection counts, publish/subscribe traffic, and mixed workload scenarios.

It provides a Spring Boot backend, a React administration console, multi-node worker execution, task orchestration,
runtime metrics, and report management in one project.

## Why bifro-test-suite

- Run MQTT 3.1.1 and MQTT 5.0 workload tests from a browser-based administration console.
- Model connection, publish-only, subscribe-only, publish/subscribe, and single-message scenarios.
- Distribute large-scale workloads across multiple worker nodes with node status and heartbeat tracking.
- Manage broker targets, task groups, test profiles, certificates, and execution reports.
- Observe throughput, latency, connection success rate, client state, and runtime metrics while tasks are running.
- Use built-in authentication support for no-auth, username/password with placeholders, and mTLS certificate cases.

## Architecture

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

## Feature Highlights

| Area | Capabilities |
| --- | --- |
| MQTT workloads | Connection, pub/sub, publish-only, subscribe-only, single-message, and immediate-disconnect scenarios |
| Protocol support | MQTT 3.1.1 and MQTT 5.0 |
| Authentication | None, username/password with client placeholders, and mTLS certificate based authentication |
| Distributed execution | Worker nodes, node heartbeat, cluster membership, and per-node task assignment |
| Resource management | Broker groups, task groups, profiles, certificates, and reusable task configuration |
| Observability | Runtime metrics, reports, latency, throughput, connection success rate, and client-level statistics |
| Administration | React, TypeScript, Ant Design, internationalized UI, role-based system user management, and audit logs |

## Technology Stack

| Layer | Technology |
| --- | --- |
| Backend | Java 17, Spring Boot 3.5.x |
| Reactive runtime | Vert.x 5.x |
| Cluster coordination | Hazelcast 5.x |
| Database | MongoDB Reactive |
| Frontend | React, Vite, TypeScript, Ant Design |
| Build | Maven, pnpm |

## Getting Started

### Prerequisites

- JDK 17+
- Maven 3.8+
- Node.js 18+
- pnpm
- MongoDB 4.4+ when using external database mode

### Build from Source

Build all backend modules:

```bash
mvn clean install
```

Build backend modules and skip frontend packaging:

```bash
mvn -U clean install -DskipFrontend=true
```

Select a moved Maven module by artifactId when running targeted builds, for example:

```bash
mvn -pl :test-suite-worker -am -DskipFrontend=true test
```

Build the frontend:

```bash
cd bifro-test-fe
pnpm install
pnpm build
```

### Run Locally

Run the backend application:

```bash
mvn spring-boot:run -pl bifro-test-bed
```

Source launches use `bifro-test-bed/src/main/resources/application.yml` as a development-only default:

- Server port: `8081`
- Storage mode: embedded MongoDB, single control-capable node
- Admin UI path: `http://localhost:8081/admin`
- Swagger UI: `http://localhost:8081/swagger-ui.html`

The development `application.yml` is excluded from the packaged application jar. Distribution startup scripts load
configuration from the external `conf/` directory with `--spring.config.location=conf/`, so deployment configuration
must be managed under `conf/`.

For frontend development:

```bash
cd bifro-test-fe
pnpm install
pnpm dev
```

## Release Artifacts

Manual GitHub releases publish multiple assets for different deployment models:

| Artifact | Description |
| --- | --- |
| `bifro-test-bed-<version>-all.tar.gz` | Full package with backend runtime and the built administration UI |
| `bifro-test-suite-<version>-backend.tar.gz` | Backend-only package without frontend static assets |
| `bifro-test-suite-<version>-frontend.zip` | Frontend-only package containing the built `dist` output |
| `bifro-test-suite-<version>-sbom.json` | CycloneDX software bill of materials |
| `SHA256SUMS` | Checksums for release assets |

The full backend package contains `conf/application.yml` plus role or mode overlays:

- `conf/application-control.yml`
- `conf/application-worker.yml`
- `conf/application-embedded.yml`

Use the launcher from the package root, for example:

```bash
bin/bifro-test-suite.sh start
bin/bifro-test-suite.sh start control
bin/bifro-test-suite.sh start worker
bin/bifro-test-suite.sh start embedded
```

### Deployment Boundary

Control-plane HA is intentionally limited in the current release. Pick the deployment shape by storage mode:

| Storage mode | Control/all nodes | Worker nodes | MongoDB requirement | Notes |
| --- | --- | --- | --- | --- |
| `embedded` | Exactly one | One or more | No external MongoDB for the control node | Single-control mode only. It is not HA, and a second embedded control/all node must fail startup. |
| `database` | One or more | One or more | External MongoDB for control/all nodes | Multiple control nodes may share MongoDB, but this is not full active-active HA yet. Use a single active API writer until HA is designed. |

Worker-only nodes do not require MongoDB in either mode. For embedded control-node movement, migrate or mount the same
embedded data directory explicitly; automatic takeover is disabled by default.

See [storage mode design](docs/arch/DESIGN-storage-modes.md) and
[control-plane minimal safety design](docs/arch/DESIGN-control-plane-minimal-safety.md) for the detailed boundaries.

## Task Templates

| Template | Purpose |
| --- | --- |
| `CONN_STANDARD` | Establish and hold standard MQTT connections |
| `CONN_IMMEDIATE_DISCONNECT` | Connect and immediately disconnect clients |
| `PUBSUB_STANDARD` | Run coordinated publish and subscribe traffic |
| `PUBSUB_PUB_ONLY` | Run publish-only traffic |
| `PUBSUB_SUB_ONLY` | Run subscribe-only traffic |
| `PUBSUB_SINGLE_MESSAGE` | Publish a single-message workload |
| `PUBSUB_SINGLE_SUBSCRIBE` | Run a single subscribe workload |

## Repository Layout

```text
bifro-test-suite/
├── bifro-test-fe/                  # React administration console
├── bifro-test-bed/                 # Spring Boot application and release assembly
├── test-suite-shared/              # Maven aggregator for shared foundation modules
│   ├── test-suite-framework/       # Pipeline and state-machine framework
│   └── test-suite-common/          # Shared domain objects, stages, events, and metrics
├── test-suite-workers/             # Maven aggregator for worker modules
│   ├── test-suite-mqtt-client/     # MQTT client wrappers and authentication strategies
│   ├── test-suite-mqtt/            # MQTT workload implementations
│   ├── test-suite-worker-api/      # Worker API contracts
│   └── test-suite-worker/          # Worker runtime and pipeline stages
└── test-suite-control/             # Maven aggregator for control modules
    ├── test-suite-web-common/      # Shared Web/API response and validation support
    ├── test-suite-certificates/    # Certificate domain and certificate services
    ├── test-suite-audit/           # Audit log support
    ├── test-suite-security/        # Authentication and system user management
    ├── test-suite-task-management/ # Task metadata, reports, runtime state, and APIs
    ├── test-suite-cluster-management/  # Cluster membership and scheduling support
    └── test-suite-resource-management/ # Broker, group, profile, and certificate APIs
```

The root Maven reactor references the three aggregator modules plus `bifro-test-bed`. Source modules are physically
archived under the corresponding aggregator directory.

## Development

Run backend tests:

```bash
mvn test
```

Run Java style checks:

```bash
mvn checkstyle:check
```

Run Apache RAT license checks:

```bash
mvn apache-rat:check -DskipTests -DskipFrontend=true
```

Run frontend checks:

```bash
cd bifro-test-fe
pnpm lint
pnpm test
```

## Security

The project does not ship with a fixed default password. If security is enabled, no users are configured, and the
user store is empty, the service creates an `admin` user with a random password and writes that password once to:

```text
conf/initial-admin-password
```

For source launches, this path is relative to the repository root. For distribution launches, it is relative to the
unpacked package root, for example `<package>/conf/initial-admin-password`.

Protect this file and rotate the password after the first login.

Production deployments should configure explicit users under `bifro.security.users` and use a MongoDB deployment
appropriate for the target environment:

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

Do not expose a local development configuration directly on an untrusted network.

## Contributing

Contributions are welcome. Before submitting a change, run the relevant backend and frontend checks and keep commits
focused on a single module or behavior change.

- [Contributing Guide](./CONTRIBUTING.md)
- [Security Policy](./SECURITY.md)
- [Code of Conduct](./CODE_OF_CONDUCT.md)

## License

bifro-test-suite is licensed under the Apache License, Version 2.0. See [LICENSE](./LICENSE) for details.
