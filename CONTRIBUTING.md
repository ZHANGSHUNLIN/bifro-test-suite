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

# Contributing

Thank you for your interest in contributing to bifro-test-suite.

## Before You Start

- Use English for code, comments, commit messages, pull requests, issues, and public project discussion.
- Keep changes focused on one module or behavior at a time.
- Follow the existing project structure and style before adding new abstractions.
- Include tests for behavior changes when practical.

## Development Setup

Prerequisites:

- JDK 17+
- Maven 3.8+
- Node.js 18+
- pnpm
- MongoDB 4.4+

Build backend modules:

```bash
mvn -U clean install -DskipFrontend=true
```

Build the frontend:

```bash
cd bifro-test-fe
pnpm install
pnpm build
```

## Checks

Run the checks that match your change:

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

## Pull Requests

Pull requests should include:

- Problem statement
- Change summary
- Affected modules
- Verification commands and results
- Screenshots or sample payloads for UI/API changes
