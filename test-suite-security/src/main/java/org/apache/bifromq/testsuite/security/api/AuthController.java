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

package org.apache.bifromq.testsuite.security.api;

import org.apache.bifromq.testsuite.config.role.ConditionalOnControlPlane;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.bifromq.testsuite.audit.application.AuditLogService;
import org.apache.bifromq.testsuite.audit.domain.AuditAction;
import org.apache.bifromq.testsuite.security.application.SystemUserService;
import org.apache.bifromq.testsuite.security.config.BifroSecurityProperties;
import org.apache.bifromq.testsuite.security.domain.SecurityRole;
import org.apache.bifromq.testsuite.web.ApiResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@ConditionalOnControlPlane
public class AuthController {

    private final BifroSecurityProperties securityProperties;
    private final UserDetailsRepositoryReactiveAuthenticationManager authenticationManager;
    private final ServerSecurityContextRepository securityContextRepository;
    private final AuditLogService auditLogService;
    private final SystemUserService systemUserService;

    @PostMapping("/login")
    public Mono<ApiResponse<AuthUserResponse>> login(@Valid @RequestBody LoginRequest request,
                                                     ServerWebExchange exchange) {
        if (!securityProperties.isEnabled()) {
            return Mono.just(ApiResponse.success(AuthUserResponse.disabled()));
        }
        UsernamePasswordAuthenticationToken token =
            new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword());
        return authenticationManager.authenticate(token)
            .flatMap(authentication -> securityContextRepository.save(exchange, new SecurityContextImpl(authentication))
                .then(auditLogService.record(exchange, AuditAction.AUTH_LOGIN_SUCCESS, "AUTH",
                    request.getUsername(), true, "Login succeeded"))
                .thenReturn(ApiResponse.success(AuthUserResponse.from(authentication, true))))
            .onErrorResume(BadCredentialsException.class, ex -> auditLogService.record(exchange,
                    AuditAction.AUTH_LOGIN_FAILURE, "AUTH", request.getUsername(), false, "Invalid credentials")
                .thenReturn(ApiResponse.error(401, "Invalid username or password")));
    }

    @PostMapping("/logout")
    public Mono<ApiResponse<Void>> logout(ServerWebExchange exchange) {
        return auditLogService.record(exchange, AuditAction.AUTH_LOGOUT, "AUTH", null, true, "Logout")
            .then(securityContextRepository.save(exchange, null))
            .then(exchange.getSession().flatMap(session -> {
                session.invalidate();
                return Mono.just(ApiResponse.<Void>success());
            }));
    }

    @GetMapping("/me")
    public Mono<ApiResponse<AuthUserResponse>> me(Mono<Authentication> authentication) {
        if (!securityProperties.isEnabled()) {
            return Mono.just(ApiResponse.success(AuthUserResponse.disabled()));
        }
        return authentication
            .filter(Authentication::isAuthenticated)
            .map(auth -> ApiResponse.success(AuthUserResponse.from(auth, true)))
            .defaultIfEmpty(ApiResponse.success(AuthUserResponse.anonymous(true)));
    }

    @PostMapping("/change-password")
    public Mono<ApiResponse<Void>> changePassword(@Valid @RequestBody SystemUserService.ChangePasswordRequest request,
                                                  Mono<Authentication> authentication,
                                                  ServerWebExchange exchange) {
        if (!securityProperties.isEnabled()) {
            return Mono.just(ApiResponse.success());
        }
        return authentication
            .filter(Authentication::isAuthenticated)
            .switchIfEmpty(Mono.error(new BadCredentialsException("Not authenticated")))
            .flatMap(auth -> systemUserService.changeOwnPassword(auth.getName(), request)
                .then(auditLogService.record(exchange, AuditAction.USER_CHANGE_PASSWORD, "USER", auth.getName(), true,
                    "Change own password")))
            .thenReturn(ApiResponse.success());
    }

    @Data
    public static class LoginRequest {
        @NotBlank
        private String username;
        @NotBlank
        private String password;
    }

    public record AuthUserResponse(boolean enabled, boolean authenticated, String username, List<String> roles) {

        static AuthUserResponse disabled() {
            return new AuthUserResponse(false, true, "local", List.of(SecurityRole.ADMIN));
        }

        static AuthUserResponse anonymous(boolean enabled) {
            return new AuthUserResponse(enabled, false, null, List.of());
        }

        static AuthUserResponse from(Authentication authentication, boolean enabled) {
            List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(role -> role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role)
                .toList();
            return new AuthUserResponse(enabled, authentication.isAuthenticated(), authentication.getName(), roles);
        }
    }
}
