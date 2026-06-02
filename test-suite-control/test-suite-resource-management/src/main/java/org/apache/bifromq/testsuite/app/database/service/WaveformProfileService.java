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

package org.apache.bifromq.testsuite.app.database.service;

import org.apache.bifromq.testsuite.config.role.ConditionalOnControlPlane;

import org.apache.bifromq.testsuite.i18n.Messages;

import org.apache.bifromq.testsuite.app.group.DefaultGroupInitializer;
import org.apache.bifromq.testsuite.app.group.GroupManager;
import org.apache.bifromq.testsuite.app.database.pojo.TaskInfoMetadata;
import org.apache.bifromq.testsuite.app.database.pojo.WaveformProfile;
import org.apache.bifromq.testsuite.app.database.repository.MqttGroupRepository;
import org.apache.bifromq.testsuite.app.database.repository.TaskInfoMetadataRepository;
import org.apache.bifromq.testsuite.app.database.repository.WaveformProfileRepository;
import org.apache.bifromq.testsuite.app.profile.TaskProfile;
import org.apache.bifromq.testsuite.app.profile.TaskProfileService;
import org.apache.bifromq.testsuite.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnControlPlane
public class WaveformProfileService implements TaskProfileService {

    private static final int PREVIEW_MAX_POINTS = 500;

    private final WaveformProfileRepository repository;
    private final ObjectMapper objectMapper;
    private final TaskInfoMetadataRepository taskRepository;
    private final MqttGroupRepository groupRepository;
    
    static long calcIntegral(List<long[]> dataPoints) {
        long sum = 0;
        for (int i = 0; i < dataPoints.size() - 1; i++) {
            long dt = dataPoints.get(i + 1)[0] - dataPoints.get(i)[0];
            long avgQps = (dataPoints.get(i)[1] + dataPoints.get(i + 1)[1]) / 2;
            sum += avgQps * dt / 1000;
        }
        return sum;
    }

    public Flux<WaveformProfile> list(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return repository.findAll();
        }
        return repository.findByNameContainingIgnoreCase(keyword);
    }

    public Flux<WaveformProfile> list(String keyword, Pageable pageable) {
        return list(keyword, null, pageable);
    }

    public Flux<WaveformProfile> list(String keyword, String group, Pageable pageable) {
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasGroup = group != null && !group.isBlank();
        if (!hasGroup && !hasKeyword) {
            return repository.findAllBy(pageable);
        }
        if (!hasGroup) {
            return repository.findByNameContainingIgnoreCase(keyword.trim(), pageable);
        }
        if (!hasKeyword) {
            return repository.findByGroup(group, pageable);
        }
        String trimmedKeyword = keyword.trim();
        return repository.findByGroup(group)
            .filter(profile -> profile.getName() != null
                && profile.getName().toLowerCase().contains(trimmedKeyword.toLowerCase()))
            .skip(pageable.getOffset())
            .take(pageable.getPageSize());
    }

    public Mono<Long> count(String keyword) {
        return count(keyword, null);
    }

    public Mono<Long> count(String keyword, String group) {
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasGroup = group != null && !group.isBlank();
        if (!hasGroup && !hasKeyword) {
            return repository.count();
        }
        if (!hasGroup) {
            return repository.countByNameContainingIgnoreCase(keyword.trim());
        }
        if (!hasKeyword) {
            return repository.countByGroup(group);
        }
        String trimmedKeyword = keyword.trim();
        return repository.findByGroup(group)
            .filter(profile -> profile.getName() != null
                && profile.getName().toLowerCase().contains(trimmedKeyword.toLowerCase()))
            .count();
    }

    public Mono<WaveformProfile> getById(String id) {
        return repository.findById(id)
            .switchIfEmpty(Mono.error(new ApiException(Messages.get("error.profile.notFound", id))));
    }

    @Override
    public Mono<TaskProfile> getTaskProfileById(String id) {
        return getById(id).map(WaveformProfileService::toTaskProfile);
    }

    private static TaskProfile toTaskProfile(WaveformProfile profile) {
        return TaskProfile.builder()
            .id(profile.getId())
            .name(profile.getName())
            .description(profile.getDescription())
            .group(profile.getGroup())
            .dataPoints(profile.getDataPoints())
            .totalDurationMs(profile.getTotalDurationMs())
            .maxQps(profile.getMaxQps())
            .peakQps(profile.getPeakQps())
            .avgQps(profile.getAvgQps())
            .integral(profile.getIntegral())
            .targetTotalCount(profile.getTargetTotalCount())
            .createdAt(profile.getCreatedAt())
            .build();
    }
    
    public Mono<List<long[]>> getPreviewData(String id) {
        return getById(id).map(profile -> downsample(profile.getDataPoints(), PREVIEW_MAX_POINTS));
    }
    
    public Mono<WaveformProfile> importFromGrafana(String grafanaJson, String name,
                                                   String description, String createdBy) {
        return repository.existsByName(name)
            .flatMap(exists -> {
                if (Boolean.TRUE.equals(exists)) {
                    return Mono.error(new ApiException(Messages.get("error.profile.nameExists", name)));
                }
                List<long[]> rawPoints = parseGrafanaJson(grafanaJson);
                return resolveProfileGroup(null)
                    .flatMap(group -> saveProfile(rawPoints, name, description, group, createdBy));
            });
    }

    public Mono<WaveformProfile> importFromCsv(String csv, String name,
                                               String description, String createdBy) {
        return repository.existsByName(name)
            .flatMap(exists -> {
                if (Boolean.TRUE.equals(exists)) {
                    return Mono.error(new ApiException(Messages.get("error.profile.nameExists", name)));
                }
                List<long[]> rawPoints = parseCsv(csv);
                return resolveProfileGroup(null)
                    .flatMap(group -> saveProfile(rawPoints, name, description, group, createdBy));
            });
    }

    public Mono<WaveformProfile> createManual(List<long[]> dataPoints, String name,
                                              String description,
                                              int maxQps,
                                              Long targetTotalCount,
                                              String createdBy) {
        return createManual(dataPoints, name, description, null, maxQps, targetTotalCount, createdBy);
    }

    public Mono<WaveformProfile> createManual(List<long[]> dataPoints, String name,
                                              String description,
                                              String group,
                                              int maxQps,
                                              Long targetTotalCount,
                                              String createdBy) {
        if (dataPoints == null || dataPoints.size() < 2) {
            return Mono.error(new ApiException(Messages.get("error.profile.dataPointsMin")));
        }
        if (maxQps <= 0) {
            return Mono.error(new ApiException(Messages.get("error.profile.maxQpsMin")));
        }
        return repository.existsByName(name)
            .flatMap(exists -> {
                if (Boolean.TRUE.equals(exists)) {
                    return Mono.error(new ApiException(Messages.get("error.profile.nameExists", name)));
                }
                long totalDurationMs = dataPoints.get(dataPoints.size() - 1)[0];
                int peakQps = (int) dataPoints.stream().mapToLong(p -> p[1]).max().orElse(0);
                double avgQps = dataPoints.stream().mapToLong(p -> p[1]).average().orElse(0);
                long integral = calcIntegral(dataPoints);

                return resolveProfileGroup(group)
                    .flatMap(resolvedGroup -> {
                        WaveformProfile profile = WaveformProfile.builder()
                            .name(name)
                            .description(description)
                            .group(resolvedGroup)
                            .dataPoints(dataPoints)
                            .totalDurationMs(totalDurationMs)
                            .maxQps(maxQps)
                            .peakQps(peakQps)
                            .avgQps(avgQps)
                            .integral(integral)
                            .targetTotalCount(targetTotalCount)
                            .createdAt(Instant.now())
                            .build();
                        return repository.save(profile);
                    });
            });
    }
    
    public Mono<WaveformProfile> updateManual(String id, List<long[]> dataPoints, String name,
                                              String description, int maxQps,
                                              Long targetTotalCount) {
        return updateManual(id, dataPoints, name, description, null, maxQps, targetTotalCount);
    }

    public Mono<WaveformProfile> updateManual(String id, List<long[]> dataPoints, String name,
                                              String description, String group, int maxQps,
                                              Long targetTotalCount) {
        if (dataPoints == null || dataPoints.size() < 2) {
            return Mono.error(new ApiException(Messages.get("error.profile.dataPointsMin")));
        }
        if (maxQps <= 0) {
            return Mono.error(new ApiException(Messages.get("error.profile.maxQpsMin")));
        }
        return repository.findById(id)
            .switchIfEmpty(Mono.error(new ApiException(Messages.get("error.profile.notFound", id))))
            .flatMap(existing -> {
                boolean nameChanged = !existing.getName().equals(name);
                Mono<Boolean> nameCheck = nameChanged
                    ? repository.existsByName(name)
                    : Mono.just(false);
                return nameCheck.flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.error(new ApiException(Messages.get("error.profile.nameExists", name)));
                    }
                    long totalDurationMs = dataPoints.get(dataPoints.size() - 1)[0];
                    int peakQps = (int) dataPoints.stream().mapToLong(p -> p[1]).max().orElse(0);
                    double avgQps = dataPoints.stream().mapToLong(p -> p[1]).average().orElse(0);
                    long integral = calcIntegral(dataPoints);

                    return resolveProfileGroup(group)
                        .flatMap(resolvedGroup -> {
                            existing.setName(name);
                            existing.setDescription(description);
                            existing.setGroup(resolvedGroup);
                            existing.setDataPoints(dataPoints);
                            existing.setTotalDurationMs(totalDurationMs);
                            existing.setMaxQps(maxQps);
                            existing.setPeakQps(peakQps);
                            existing.setAvgQps(avgQps);
                            existing.setIntegral(integral);
                            existing.setTargetTotalCount(targetTotalCount);
                            return repository.save(existing);
                        });
                });
            });
    }
    
    public Mono<Void> deleteById(String id) {
        return repository.existsById(id)
            .flatMap(exists -> {
                if (Boolean.FALSE.equals(exists)) {
                    return Mono.error(new ApiException(Messages.get("error.profile.notFound", id)));
                }
                
                return taskRepository.findByProfileId(id)
                    .map(TaskInfoMetadata::getTaskName)
                    .collectList()
                    .flatMap(names -> {
                        if (!names.isEmpty()) {
                            return Mono.error(new ApiException(
                                Messages.get("error.profile.usedByTasks", String.join(", ", names))));
                        }
                        return repository.deleteById(id);
                    });
            });
    }

    List<long[]> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            throw new ApiException(Messages.get("error.profile.dataPointsMin"));
        }
        List<long[]> raw = new ArrayList<>();
        String[] lines = csv.split("\\r?\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] cols = line.split(",");
            if (cols.length < 2) {
                continue;
            }
            try {
                long tsMs = Long.parseLong(cols[0].trim());
                
                if (tsMs < 1_000_000_000_000L) {
                    tsMs *= 1000L;
                }
                double qps = Double.parseDouble(cols[cols.length - 1].trim());
                raw.add(new long[] {tsMs, Math.round(qps)});
            } catch (NumberFormatException ignored) {
                
            }
        }
        return toRelative(raw);
    }
    
    List<long[]> parseGrafanaJson(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            if (root.isArray()) {
                return parseOldFormat(root);
            } else if (root.has("results")) {
                return parseNewFormat(root);
            } else {
                throw new ApiException(Messages.get("error.profile.notFound", "unsupported Grafana format"));
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(Messages.get("error.profile.notFound", e.getMessage()));
        }
    }
    
    private List<long[]> parseOldFormat(JsonNode array) {
        if (array.isEmpty()) {
            throw new ApiException(Messages.get("error.profile.dataPointsMin"));
        }
        JsonNode series = array.get(0);
        JsonNode datapoints = series.get("datapoints");
        if (datapoints == null || !datapoints.isArray()) {
            throw new ApiException(Messages.get("error.profile.dataPointsMin"));
        }

        List<long[]> raw = new ArrayList<>();
        for (JsonNode point : datapoints) {
            double value = point.get(0).asDouble(0);
            long tsMs = point.get(1).asLong(0);
            raw.add(new long[] {tsMs, Math.round(value)});
        }
        return toRelative(raw);
    }
    
    private List<long[]> parseNewFormat(JsonNode root) {
        try {
            JsonNode refNode = root.path("results").fields().next().getValue();
            JsonNode frame = refNode.path("frames").get(0);
            JsonNode values = frame.path("data").path("values");

            JsonNode timestamps = values.get(0);
            JsonNode qpsValues = values.get(1);

            if (timestamps == null || qpsValues == null) {
                throw new ApiException(Messages.get("error.profile.dataPointsMin"));
            }

            List<long[]> raw = new ArrayList<>();
            for (int i = 0; i < timestamps.size(); i++) {
                long tsMs = timestamps.get(i).asLong(0);
                double qps = qpsValues.get(i).asDouble(0);
                raw.add(new long[] {tsMs, Math.round(qps)});
            }
            return toRelative(raw);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(Messages.get("error.profile.notFound", e.getMessage()));
        }
    }

    private List<long[]> toRelative(List<long[]> raw) {
        if (raw.isEmpty()) {
            throw new ApiException(Messages.get("error.profile.dataPointsMin"));
        }
        raw.sort((a, b) -> Long.compare(a[0], b[0]));
        long origin = raw.get(0)[0];
        List<long[]> relative = new ArrayList<>(raw.size());
        for (long[] pt : raw) {
            relative.add(new long[] {pt[0] - origin, Math.max(0, pt[1])});
        }
        return relative;
    }

    private Mono<WaveformProfile> saveProfile(List<long[]> dataPoints, String name,
                                              String description,
                                              String group,
                                              String createdBy) {
        long totalDurationMs = dataPoints.isEmpty() ? 0
            : dataPoints.get(dataPoints.size() - 1)[0];
        int peakQps = dataPoints.stream().mapToLong(p -> p[1]).max().orElse(0) > Integer.MAX_VALUE
            ? Integer.MAX_VALUE
            : (int) dataPoints.stream().mapToLong(p -> p[1]).max().orElse(0);
        double avgQps = dataPoints.stream().mapToLong(p -> p[1]).average().orElse(0);
        long integral = calcIntegral(dataPoints);

        WaveformProfile profile = WaveformProfile.builder()
            .name(name)
            .description(description)
            .group(group)
            .dataPoints(dataPoints)
            .totalDurationMs(totalDurationMs)
            .maxQps(peakQps)   
            .peakQps(peakQps)
            .avgQps(avgQps)
            .integral(integral)
            .createdAt(Instant.now())
            .build();

        return repository.save(profile);
    }

    private Mono<String> resolveProfileGroup(String group) {
        if (group != null && !group.isBlank()) {
            return Mono.just(group);
        }
        return groupRepository.findByNameAndType(DefaultGroupInitializer.DEFAULT_GROUP_NAME, GroupManager.TYPE_PROFILE)
            .map(org.apache.bifromq.testsuite.app.database.pojo.MqttGroup::getId)
            .switchIfEmpty(Mono.defer(() -> {
                Instant now = Instant.now();
                return groupRepository.save(org.apache.bifromq.testsuite.app.database.pojo.MqttGroup.builder()
                    .type(GroupManager.TYPE_PROFILE)
                    .name(DefaultGroupInitializer.DEFAULT_GROUP_NAME)
                    .description(DefaultGroupInitializer.DEFAULT_GROUP_DESCRIPTION)
                    .createdAt(now)
                    .updatedAt(now)
                    .build()).map(org.apache.bifromq.testsuite.app.database.pojo.MqttGroup::getId);
            }));
    }
    
    private List<long[]> downsample(List<long[]> points, int maxPoints) {
        if (points == null || points.size() <= maxPoints) {
            return points;
        }
        List<long[]> result = new ArrayList<>(maxPoints);
        double stride = (double) points.size() / maxPoints;
        for (int i = 0; i < maxPoints; i++) {
            result.add(points.get((int) (i * stride)));
        }
        return result;
    }
}
