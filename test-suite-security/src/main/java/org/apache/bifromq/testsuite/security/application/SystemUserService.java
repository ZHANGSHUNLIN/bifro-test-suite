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

package org.apache.bifromq.testsuite.security.application;

import org.apache.bifromq.testsuite.config.role.ConditionalOnControlPlane;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.security.config.BifroSecurityProperties;
import org.apache.bifromq.testsuite.security.domain.SecurityRole;
import org.apache.bifromq.testsuite.security.domain.SystemUser;
import org.apache.bifromq.testsuite.security.infrastructure.SystemUserRepository;
import org.apache.bifromq.testsuite.web.ApiException;
import org.apache.bifromq.testsuite.web.PageInfo;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnControlPlane
public class SystemUserService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final int GENERATED_PASSWORD_BYTES = 32;
    private static final Path INITIAL_ADMIN_PASSWORD_FILE = Path.of("conf", "initial-admin-password");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SystemUserRepository systemUserRepository;
    private final BifroSecurityProperties securityProperties;
    private final PasswordEncoder passwordEncoder;
    private final ReactiveMongoTemplate mongoTemplate;

    @PostConstruct
    public void initializeUsers() {
        systemUserRepository.count()
            .filter(count -> count == 0)
            .flatMapMany(count -> Flux.fromIterable(seededUsers()))
            .concatMap(systemUserRepository::save)
            .doOnError(error -> log.warn("Failed to initialize system users: {}", error.getMessage()))
            .subscribe(user -> log.info("Initialized system user: {}", user.getUsername()));
    }

    public Flux<SystemUserResponse> list() {
        return systemUserRepository.findAll()
            .sort((left, right) -> left.getUsername().compareToIgnoreCase(right.getUsername()))
            .map(SystemUserResponse::from);
    }

    public Mono<PageInfo<SystemUserResponse>> list(int pageNum, int pageSize) {
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
        Query pageQuery = new Query()
            .with(Sort.by(Sort.Direction.ASC, "username"))
            .skip((long) (safePageNum - 1) * safePageSize)
            .limit(safePageSize);
        return Mono.zip(
                mongoTemplate.find(pageQuery, SystemUser.class)
                    .map(SystemUserResponse::from)
                    .collectList(),
                mongoTemplate.count(new Query(), SystemUser.class))
            .map(tuple -> pageInfo(tuple.getT1(), tuple.getT2(), safePageNum, safePageSize));
    }

    public Mono<SystemUserResponse> create(CreateUserRequest request) {
        String username = normalizeUsername(request.getUsername());
        validatePassword(request.getPassword());
        List<String> roles = normalizeRoles(request.getRoles());
        return systemUserRepository.existsByUsername(username)
            .flatMap(exists -> {
                if (exists) {
                    return Mono.error(new ApiException("Username already exists"));
                }
                Instant now = Instant.now();
                return systemUserRepository.save(SystemUser.builder()
                    .username(username)
                    .passwordHash(passwordEncoder.encode(request.getPassword()))
                    .roles(roles)
                    .enabled(request.getEnabled() == null || request.getEnabled())
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
            })
            .map(SystemUserResponse::from);
    }

    public Mono<SystemUserResponse> update(String id, UpdateUserRequest request) {
        return systemUserRepository.findById(id)
            .switchIfEmpty(Mono.error(new ApiException("User not found")))
            .flatMap(user -> validateAdminInvariant(user, request.getEnabled(), request.getRoles())
                .then(Mono.defer(() -> {
                    if (request.getRoles() != null) {
                        user.setRoles(normalizeRoles(request.getRoles()));
                    }
                    if (request.getEnabled() != null) {
                        user.setEnabled(request.getEnabled());
                    }
                    user.setUpdatedAt(Instant.now());
                    return systemUserRepository.save(user);
                })))
            .map(SystemUserResponse::from);
    }

    public Mono<Void> delete(String id) {
        return systemUserRepository.findById(id)
            .switchIfEmpty(Mono.error(new ApiException("User not found")))
            .flatMap(user -> validateAdminInvariant(user, false, user.getRoles())
                .then(systemUserRepository.deleteById(id)));
    }

    public Mono<Void> resetPassword(String id, ResetPasswordRequest request) {
        validatePassword(request.getPassword());
        return systemUserRepository.findById(id)
            .switchIfEmpty(Mono.error(new ApiException("User not found")))
            .flatMap(user -> {
                user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
                user.setUpdatedAt(Instant.now());
                return systemUserRepository.save(user);
            })
            .then();
    }

    public Mono<Void> changeOwnPassword(String username, ChangePasswordRequest request) {
        validatePassword(request.getNewPassword());
        return systemUserRepository.findByUsername(username)
            .switchIfEmpty(Mono.error(new ApiException("User not found")))
            .flatMap(user -> {
                if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
                    return Mono.error(new ApiException("Current password is invalid"));
                }
                user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
                user.setUpdatedAt(Instant.now());
                return systemUserRepository.save(user);
            })
            .then();
    }

    private Mono<Void> validateAdminInvariant(SystemUser target, Boolean nextEnabled, List<String> nextRoles) {
        boolean enabledAfterUpdate = nextEnabled == null ? target.isEnabled() : nextEnabled;
        List<String> rolesAfterUpdate = nextRoles == null ? target.getRoles() : nextRoles;
        boolean removesAdmin = target.isEnabled()
            && hasAdminRole(target.getRoles())
            && (!enabledAfterUpdate || !hasAdminRole(rolesAfterUpdate));
        if (!removesAdmin) {
            return Mono.empty();
        }
        return systemUserRepository.findAll()
            .filter(user -> !target.getId().equals(user.getId()))
            .filter(SystemUser::isEnabled)
            .filter(user -> hasAdminRole(user.getRoles()))
            .hasElements()
            .flatMap(hasAnotherAdmin -> hasAnotherAdmin
                ? Mono.empty()
                : Mono.error(new ApiException("At least one enabled ADMIN user is required")));
    }

    private List<SystemUser> seededUsers() {
        Instant now = Instant.now();
        List<BifroSecurityProperties.User> configuredUsers = securityProperties.getUsers();
        if (configuredUsers == null || configuredUsers.isEmpty()) {
            if (securityProperties.isEnabled()) {
                String password = initialAdminPassword();
                log.warn("No initial system users are configured. Generated initial administrator '{}' and wrote "
                    + "the password to {}", DEFAULT_ADMIN_USERNAME, INITIAL_ADMIN_PASSWORD_FILE);
                return List.of(SystemUser.builder()
                    .username(DEFAULT_ADMIN_USERNAME)
                    .passwordHash(passwordEncoder.encode(password))
                    .roles(List.of(SecurityRole.ADMIN))
                    .enabled(true)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
            }
            return List.of();
        }
        return configuredUsers.stream()
            .filter(user -> user.getUsername() != null && user.getPassword() != null)
            .map(user -> SystemUser.builder()
                .username(normalizeUsername(user.getUsername()))
                .passwordHash(passwordHash(user.getPassword()))
                .roles(normalizeRoles(user.getRoles()))
                .enabled(true)
                .createdAt(now)
                .updatedAt(now)
                .build())
            .toList();
    }

    private String initialAdminPassword() {
        try {
            if (Files.exists(INITIAL_ADMIN_PASSWORD_FILE)) {
                String existingPassword = Files.readString(INITIAL_ADMIN_PASSWORD_FILE, StandardCharsets.UTF_8).trim();
                if (!existingPassword.isBlank()) {
                    restrictOwnerAccess(INITIAL_ADMIN_PASSWORD_FILE);
                    return existingPassword;
                }
            }
            Files.createDirectories(INITIAL_ADMIN_PASSWORD_FILE.getParent());
            String password = generatePassword();
            Files.writeString(INITIAL_ADMIN_PASSWORD_FILE, password + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            restrictOwnerAccess(INITIAL_ADMIN_PASSWORD_FILE);
            return password;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize admin password file: " + INITIAL_ADMIN_PASSWORD_FILE,
                e);
        }
    }

    private String generatePassword() {
        byte[] bytes = new byte[GENERATED_PASSWORD_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void restrictOwnerAccess(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, EnumSet.of(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            log.debug("POSIX file permissions are not supported for {}", path);
        }
    }

    private String passwordHash(String password) {
        return password.startsWith("{") ? password : passwordEncoder.encode(password);
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new ApiException("Username is required");
        }
        return username.trim();
    }

    private List<String> normalizeRoles(List<String> roles) {
        Set<String> normalized = new LinkedHashSet<>();
        if (roles != null) {
            roles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(role -> role.toUpperCase(Locale.ROOT))
                .forEach(role -> {
                    if (!SecurityRole.ADMIN.equals(role) && !SecurityRole.VIEWER.equals(role)) {
                        throw new ApiException("Unsupported role: " + role);
                    }
                    normalized.add(role);
                });
        }
        if (normalized.isEmpty()) {
            normalized.add(SecurityRole.VIEWER);
        }
        return List.copyOf(normalized);
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new ApiException("Password is required");
        }
    }

    private boolean hasAdminRole(List<String> roles) {
        return roles != null && roles.stream()
            .anyMatch(role -> SecurityRole.ADMIN.equals(role.toUpperCase(Locale.ROOT)));
    }

    private PageInfo<SystemUserResponse> pageInfo(List<SystemUserResponse> content, long total,
                                                  int pageNum, int pageSize) {
        PageInfo<SystemUserResponse> pageInfo = new PageInfo<>();
        pageInfo.setContent(content);
        pageInfo.setTotalElements(total);
        pageInfo.setTotalPages((int) Math.ceil((double) total / pageSize));
        pageInfo.setSize(pageSize);
        pageInfo.setNumber(pageNum - 1);
        pageInfo.setNumberOfElements(content.size());
        pageInfo.setFirst(pageNum == 1);
        pageInfo.setLast((long) pageNum * pageSize >= total);
        return pageInfo;
    }

    public record SystemUserResponse(String id, String username, List<String> roles, boolean enabled,
                                     Instant createdAt, Instant updatedAt) {

        static SystemUserResponse from(SystemUser user) {
            return new SystemUserResponse(user.getId(), user.getUsername(), user.getRoles(), user.isEnabled(),
                user.getCreatedAt(), user.getUpdatedAt());
        }
    }

    @Data
    public static class CreateUserRequest {
        private String username;
        private String password;
        private List<String> roles;
        private Boolean enabled;
    }

    @Data
    public static class UpdateUserRequest {
        private List<String> roles;
        private Boolean enabled;
    }

    @Data
    public static class ResetPasswordRequest {
        private String password;
    }

    @Data
    public static class ChangePasswordRequest {
        private String oldPassword;
        private String newPassword;
    }
}
