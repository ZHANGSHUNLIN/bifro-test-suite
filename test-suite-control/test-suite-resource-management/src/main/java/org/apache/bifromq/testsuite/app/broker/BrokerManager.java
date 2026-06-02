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

package org.apache.bifromq.testsuite.app.broker;

import org.apache.bifromq.testsuite.config.role.ConditionalOnControlPlane;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.bifromq.testsuite.app.bean.broker.BrokerListItem;
import org.apache.bifromq.testsuite.app.bean.broker.MqttBrokerRequest;
import org.apache.bifromq.testsuite.app.database.pojo.MqttBroker;
import org.apache.bifromq.testsuite.app.database.repository.MqttBrokerRepository;
import org.apache.bifromq.testsuite.i18n.Messages;
import org.apache.bifromq.testsuite.web.ApiException;
import org.apache.bifromq.testsuite.web.ApiResponse;
import org.apache.bifromq.testsuite.web.PageInfo;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@Slf4j
@ConditionalOnControlPlane
public class BrokerManager {

    @Resource
    private MqttBrokerRepository mqttBrokerRepository;

    public Mono<ApiResponse<MqttBroker>> addTask(MqttBrokerRequest mqttBrokerRequest) {

        return mqttBrokerRepository.findByName(mqttBrokerRequest.getName())
            .flatMap(existing -> Mono.<ApiResponse<MqttBroker>>error(
                new ApiException(Messages.get("error.broker.nameExists", mqttBrokerRequest.getName()))))
            .switchIfEmpty(Mono.defer(() -> {
                MqttBroker broker = new MqttBroker();
                BeanUtils.copyProperties(mqttBrokerRequest, broker);
                return mqttBrokerRepository.save(broker)
                    .map(ApiResponse::success);
            }));
    }

    public Mono<ApiResponse<PageInfo<BrokerListItem>>> list(Boolean enabled, String group, Integer pageNum,
                                                            Integer pageSize) {
        Flux<MqttBroker> allBrokers = mqttBrokerRepository.findAll();
        if (enabled != null) {
            allBrokers = allBrokers.filter(b -> Objects.equals(b.getEnabled(), enabled));
        }
        if (group != null && !group.isEmpty()) {
            allBrokers = allBrokers.filter(b -> group.equals(b.getGroup()));
        }

        return allBrokers.collectList()
            .map(brokerList -> {

                int total = brokerList.size();
                int fromIndex = (pageNum - 1) * pageSize;
                int toIndex = Math.min(fromIndex + pageSize, total);
                List<MqttBroker> pageList = total > fromIndex
                    ? brokerList.subList(fromIndex, toIndex)
                    : new ArrayList<>();

                List<BrokerListItem> resultList = pageList.stream()
                    .map(broker -> {
                        BrokerListItem item = new BrokerListItem();
                        BeanUtils.copyProperties(broker, item);
                        return item;
                    })
                    .collect(Collectors.toList());

                return ApiResponse.pageSuccess(resultList, total, pageNum, pageSize);
            });
    }

    public Mono<ApiResponse<MqttBroker>> get(String brokerId) {
        return mqttBrokerRepository.findFirstById(brokerId)
            .map(ApiResponse::success)
            .defaultIfEmpty(ApiResponse.error(Messages.get("error.broker.notFound")));
    }

    public Mono<ApiResponse<MqttBroker>> del(String brokerId) {
        return mqttBrokerRepository.deleteById(brokerId)
            .then(Mono.just(ApiResponse.success()));
    }

    public Mono<ApiResponse<MqttBroker>> update(String id, MqttBrokerRequest mqttBrokerRequest) {
        return mqttBrokerRepository.findById(id)
            .switchIfEmpty(Mono.error(new ApiException(Messages.get("error.broker.notFound"))))
            .flatMap(existing -> {

                return mqttBrokerRepository.findByName(mqttBrokerRequest.getName())
                    .filter(b -> !b.getId().equals(id))
                    .hasElement()
                    .flatMap(nameExists -> {
                        if (nameExists) {
                            return Mono.error(
                                new ApiException(Messages.get("error.broker.nameExists", mqttBrokerRequest.getName())));
                        }
                        BeanUtils.copyProperties(mqttBrokerRequest, existing);
                        return mqttBrokerRepository.save(existing);
                    });
            })
            .map(ApiResponse::success)
            .defaultIfEmpty(ApiResponse.error(Messages.get("error.broker.notFound")));
    }

    public Mono<ApiResponse<MqttBroker>> enable(String id, Boolean enabled) {
        return mqttBrokerRepository.findById(id)
            .flatMap(broker -> {
                broker.setEnabled(enabled);
                return mqttBrokerRepository.save(broker);
            })
            .map(ApiResponse::success)
            .defaultIfEmpty(ApiResponse.error(Messages.get("error.broker.notFound")));
    }
}
