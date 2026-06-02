/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.bifromq.testsuite.app.controller.base;

import org.apache.bifromq.testsuite.config.role.ConditionalOnControlPlane;

import java.net.URI;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@ConditionalOnControlPlane
public class SpaRoutingController {
    @GetMapping("/")
    public Mono<Void> rootRedirect(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create("/admin"));
        return response.setComplete();
    }
    @GetMapping({"/admin", "/admin/"})
    public Mono<Resource> adminRoot() {

        return Mono.just(new ClassPathResource("static/admin/index.html"));
    }
    @GetMapping({
        "/admin/{path:[^\\.]*}",
        "/admin/{path1:[^\\.]*}/{path2:[^\\.]*}"
    })
    public Mono<Resource> adminSpaRoutes() {
        return Mono.just(new ClassPathResource("static/admin/index.html"));
    }
}