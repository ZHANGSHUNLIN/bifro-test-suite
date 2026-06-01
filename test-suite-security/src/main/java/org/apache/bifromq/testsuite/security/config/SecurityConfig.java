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

package org.apache.bifromq.testsuite.security.config;

import lombok.RequiredArgsConstructor;
import org.apache.bifromq.testsuite.security.domain.SecurityRole;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
@EnableConfigurationProperties(BifroSecurityProperties.class)
public class SecurityConfig {

    private final BifroSecurityProperties securityProperties;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsRepositoryReactiveAuthenticationManager authenticationManager(
        ReactiveUserDetailsService userDetailsService,
        PasswordEncoder passwordEncoder) {
        UserDetailsRepositoryReactiveAuthenticationManager authenticationManager =
            new UserDetailsRepositoryReactiveAuthenticationManager(userDetailsService);
        authenticationManager.setPasswordEncoder(passwordEncoder);
        return authenticationManager;
    }

    @Bean
    public ServerSecurityContextRepository securityContextRepository() {
        return new WebSessionServerSecurityContextRepository();
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                         ServerSecurityContextRepository repository) {
        if (!securityProperties.isEnabled()) {
            return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .build();
        }

        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .logout(ServerHttpSecurity.LogoutSpec::disable)
            .securityContextRepository(repository)
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .pathMatchers("/", "/admin/**", "/assets/**", "/favicon.ico").permitAll()
                .pathMatchers("/api/auth/login", "/api/auth/me").permitAll()
                .pathMatchers("/api/auth/logout", "/api/auth/change-password")
                .hasAnyRole(SecurityRole.ADMIN, SecurityRole.VIEWER)
                .pathMatchers("/api/health/**", "/api/version", "/actuator/**").permitAll()
                .pathMatchers("/api/debug/**").permitAll()
                .pathMatchers("/v3/api-docs/**", "/swagger-ui/**", "/webjars/**").permitAll()
                .pathMatchers("/api/audit-logs/**").hasRole(SecurityRole.ADMIN)
                .pathMatchers("/api/system/**").hasRole(SecurityRole.ADMIN)
                .pathMatchers(HttpMethod.GET, "/api/**").hasAnyRole(SecurityRole.ADMIN, SecurityRole.VIEWER)
                .pathMatchers("/api/**").hasRole(SecurityRole.ADMIN)
                .anyExchange().permitAll())
            .exceptionHandling(exceptionHandling -> exceptionHandling
                .authenticationEntryPoint((exchange, ex) -> {
                    exchange.getResponse().setRawStatusCode(401);
                    return Mono.empty();
                })
                .accessDeniedHandler((exchange, denied) -> {
                    exchange.getResponse().setRawStatusCode(403);
                    return Mono.empty();
                }))
            .build();
    }
}
