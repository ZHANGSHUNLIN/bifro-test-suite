package com.baidu.duhome.cluster;

import com.baidu.duhome.bean.ApiResponse;
import com.baidu.duhome.bean.PageInfo;
import com.baidu.duhome.bean.group.GroupListItem;
import com.baidu.duhome.bean.group.GroupRequest;
import com.baidu.duhome.database.pojo.MqttBroker;
import com.baidu.duhome.database.pojo.MqttGroup;
import com.baidu.duhome.database.pojo.TaskInfoMetadata;
import com.baidu.duhome.database.repository.MqttBrokerRepository;
import com.baidu.duhome.database.repository.MqttGroupRepository;
import com.baidu.duhome.database.repository.TaskInfoMetadataRepository;
import com.baidu.duhome.exception.ApiException;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MQTT 分组管理（支持 Broker 分组和任务分组）
 */
@Slf4j
@Component
public class GroupManager {

    public static final String TYPE_BROKER = "BROKER";
    public static final String TYPE_TASK = "TASK";

    @Resource
    private MqttGroupRepository groupRepository;

    @Resource
    private MqttBrokerRepository brokerRepository;

    @Resource
    private TaskInfoMetadataRepository taskInfoMetadataRepository;

    /**
     * 添加分组
     */
    public Mono<ApiResponse<MqttGroup>> add(@Valid GroupRequest request, String type) {
        return groupRepository.findByNameAndType(request.getName(), type)
                .flatMap(existing -> Mono.<ApiResponse<MqttGroup>>error(
                        new ApiException("分组名称已存在：" + request.getName())))
                .switchIfEmpty(
                        groupRepository.save(MqttGroup.builder()
                                .type(type)
                                .name(request.getName())
                                .description(request.getDescription())
                                .createdAt(Instant.now())
                                .updatedAt(Instant.now())
                                .build())
                                .map(ApiResponse::success)
                );
    }

    /**
     * 更新分组
     */
    public Mono<ApiResponse<MqttGroup>> update(String id, @Valid GroupRequest request, String type) {
        return groupRepository.findById(id)
                .switchIfEmpty(Mono.error(new ApiException("分组不存在")))
                .flatMap(existing -> {
                    // 检查名称是否被其他同类型分组使用
                    return groupRepository.findByNameAndType(request.getName(), type)
                            .filter(g -> !g.getId().equals(id))
                            .hasElement()
                            .flatMap(nameExists -> {
                                if (nameExists) {
                                    return Mono.error(new ApiException("分组名称已存在：" + request.getName()));
                                }
                                // 更新分组
                                BeanUtils.copyProperties(request, existing);
                                existing.setUpdatedAt(Instant.now());
                                return groupRepository.save(existing).map(ApiResponse::success);
                            });
                });
    }

    /**
     * 删除分组
     */
    public Mono<ApiResponse<Void>> delete(String groupId, String type) {
        return groupRepository.findById(groupId)
                .switchIfEmpty(Mono.error(new ApiException("分组不存在")))
                .flatMap(group -> {
                    if (TYPE_BROKER.equals(type)) {
                        // Broker分组：查找使用该分组的 Broker
                        return brokerRepository.findByGroup(group.getName())
                                .collectList()
                                .flatMap(brokers -> {
                                    if (brokers.isEmpty()) {
                                        // 没有 Broker 使用该分组，可以删除
                                        return groupRepository.deleteById(groupId)
                                                .then(Mono.just(ApiResponse.<Void>success()));
                                    }
                                    // 有 Broker 使用该分组，返回错误
                                    String brokerNames = brokers.stream()
                                            .map(MqttBroker::getName)
                                            .collect(Collectors.joining("、"));
                                    return Mono.error(new ApiException("无法删除分组，以下 Broker 正在使用：" + brokerNames));
                                });
                    } else {
                        // 任务分组：直接删除（暂不检查任务关联）
                        return groupRepository.deleteById(groupId)
                                .then(Mono.just(ApiResponse.<Void>success()));
                    }
                });
    }

    /**
     * 分页查询分组列表
     */
    public Mono<ApiResponse<PageInfo<GroupListItem>>> list(Integer pageNum, Integer pageSize, String type) {
        return groupRepository.findByType(type)
                .collectList()
                .flatMapMany(groups -> Flux.fromIterable(groups)
                        .flatMap(group -> {
                            GroupListItem item = new GroupListItem();
                            BeanUtils.copyProperties(group, item);
                            item.setType(type);

                            if (TYPE_BROKER.equals(type)) {
                                // Broker分组：统计 Broker 数量
                                return brokerRepository.findByGroup(group.getName())
                                        .count()
                                        .map(count -> {
                                            item.setCount(count);
                                            return item;
                                        });
                            } else {
                                // 任务分组：统计任务数量
                                return taskInfoMetadataRepository.findByGroup(group.getName())
                                        .count()
                                        .map(count -> {
                                            item.setCount(count);
                                            return item;
                                        });
                            }
                        }))
                .collectList()
                .map(content -> {
                    // 内存分页
                    int total = content.size();
                    int fromIndex = (pageNum - 1) * pageSize;
                    int toIndex = Math.min(fromIndex + pageSize, total);
                    List<GroupListItem> pageList = total > fromIndex
                            ? content.subList(fromIndex, toIndex)
                            : new ArrayList<>();

                    return ApiResponse.pageSuccess(pageList, (long) total, pageNum, pageSize);
                });
    }

    /**
     * 获取分组详情
     */
    public Mono<ApiResponse<MqttGroup>> getDetail(String groupId) {
        return groupRepository.findById(groupId)
                .map(ApiResponse::success)
                .defaultIfEmpty(ApiResponse.error("分组不存在"));
    }

    /**
     * 获取所有分组（不分页）
     */
    public Flux<MqttGroup> getAllGroups(String type) {
        return groupRepository.findByType(type);
    }
}
