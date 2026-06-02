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

# bifro-test-bed

Spring Boot application shell for Bifro Test Suite.

## Run Roles

The backend supports three runtime roles:

- `all`: local compatibility mode, starts control-plane and worker-plane components.
- `control`: REST API, security, audit, task allocation, MongoDB, and worker command fan-out.
- `worker`: task execution, node registration, metrics/client/local-port query consumers. It does not start control-plane APIs or MongoDB repositories.

Default local development still uses `all`.

## Local Commands

```bash
mvn spring-boot:run -pl bifro-test-bed
```

Control plane:

```bash
mvn spring-boot:run -pl bifro-test-bed \
  -Dspring-boot.run.arguments="--spring.config.additional-location=file:bifro-test-bed/conf/application-control.yml"
```

Worker plane:

```bash
mvn spring-boot:run -pl bifro-test-bed \
  -Dspring-boot.run.arguments="--spring.config.additional-location=file:bifro-test-bed/conf/application-worker.yml"
```

## Distribution Config

The assembly package includes:

- `conf/application.yml`: baseline config.
- `conf/application-control.yml`: control-plane overlay.
- `conf/application-worker.yml`: worker-plane overlay.

Use Spring Boot `spring.config.additional-location` or equivalent environment variables to load the role overlay after the baseline config.

## Worker Command Settings

Control-plane worker command fan-out uses Vert.x EventBus request/reply:

```yaml
bifro:
  eventbus:
    request-timeout: 5s
    task-command-timeout: 5s
  worker-command:
    start-retries: 1
    stop-retries: 2
```

Retries only apply to command delivery failures or request timeout. A worker ACK rejection is recorded as task state history and is not retried automatically.
