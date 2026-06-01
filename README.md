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

[Simplified Chinese](./README.zh-CN.md)

bifro-test-suite is a distributed MQTT stress testing platform for running large-scale connection,
publish, subscribe, and mixed workload tests across multiple worker nodes.

## Features

- MQTT 3.1.1 and MQTT 5.0 workload support
- Connection, publish/subscribe, publish-only, subscribe-only, and single-message scenarios
- Distributed worker execution with cluster node status and heartbeat tracking
- Broker and task group management
- Multiple authentication modes, including no-auth, username/password, BYOC, IoT Core, and mTLS certificates
- Runtime metrics, task reports, throughput, latency, connection success rate, and client-level statistics
- React-based administration UI with internationalization support

## Technology Stack

| Layer | Technology |
| --- | --- |
| Backend | Java 17, Spring Boot 3.5.14 |
| Reactive runtime | Vert.x 5.0.12 |
| Cluster coordination | Hazelcast 5.3.5 |
| Database | MongoDB Reactive |
| Frontend | React, Vite, TypeScript, Ant Design |

## Repository Layout

```text
bifro-test-suite/
├── bifro-test-bed/              # Spring Boot application
├── bifro-test-fe/               # React administration UI
├── test-suite-framework/        # Pipeline and state-machine framework
├── test-suite-common/           # Shared enums, stages, events, and metrics
├── test-suite-mqtt-client/      # MQTT client wrappers and authentication strategies
├── test-suite-mqtt/             # MQTT task implementations
├── test-suite-worker-api/       # Worker API contracts
├── test-suite-worker/           # Worker runtime and pipeline stages
├── test-suite-certificates/     # Certificate management
├── test-suite-task-management/  # Task metadata, reports, and APIs
├── test-suite-cluster-management/ # Cluster node management
├── test-suite-resource-management/ # Broker, group, profile, and certificate APIs
├── test-suite-security/         # Authentication and user management
└── test-suite-audit/            # Audit log support
```

## Build

### Prerequisites

- JDK 17+
- Maven 3.8+
- Node.js 18+
- pnpm
- MongoDB 4.4+

### Backend

```bash
mvn clean install
```

To build backend modules without running the frontend build:

```bash
mvn -U clean install -DskipFrontend=true
```

### Frontend

```bash
cd bifro-test-fe
pnpm install
pnpm build
```

## Run Locally

Start the backend:

```bash
mvn spring-boot:run -pl bifro-test-bed
```

Start the frontend development server:

```bash
cd bifro-test-fe
pnpm dev
```

Default local endpoints:

- Admin UI: `http://localhost:8081/admin`
- Swagger UI: `http://localhost:8081/swagger-ui.html`

## Task Templates

| Template | Type | Description |
| --- | --- | --- |
| `CONN_STANDARD` | Connection | Standard connection test |
| `CONN_IMMEDIATE_DISCONNECT` | Connection | Connect and immediately disconnect |
| `PUBSUB_STANDARD` | Publish/Subscribe | Standard publish and subscribe test |
| `PUBSUB_PUB_ONLY` | Publish/Subscribe | Publish-only test |
| `PUBSUB_SUB_ONLY` | Publish/Subscribe | Subscribe-only test |
| `PUBSUB_SINGLE_MESSAGE` | Publish/Subscribe | Single-message publish test |
| `PUBSUB_SINGLE_SUBSCRIBE` | Publish/Subscribe | Single subscribe test |

## Development Checks

```bash
mvn checkstyle:check
mvn test

cd bifro-test-fe
pnpm lint
pnpm test
```

Run the Apache RAT license header check:

```bash
mvn apache-rat:check -DskipTests -DskipFrontend=true
```

## Security Notes

This project does not ship with a fixed default password. If security is enabled and no users are configured
when the user store is empty, the service creates an `admin` user with a random password and writes the password
once to `conf/initial-admin-password`. Protect this file and rotate the password after the first login.

For production deployments, you can also configure explicit initial users under `bifro.security.users`:

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

Do not run this service on an exposed network with local development settings. Production deployments should
provide explicit authentication configuration and a MongoDB deployment appropriate for the target environment.

## License

This project is licensed under the Apache License, Version 2.0. See [LICENSE](./LICENSE) for details.

## Community

- [Contributing](./CONTRIBUTING.md)
- [Security Policy](./SECURITY.md)
- [Code of Conduct](./CODE_OF_CONDUCT.md)
