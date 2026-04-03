package com.baidu.iot.test.suite.worker;

import com.baidu.iot.test.suite.TaskTemplate;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;

/**
 * Factory for creating task workers based on template.
 */
@Slf4j
public final class TaskWorkerFactory {

    private TaskWorkerFactory() {
    }

    /**
     * Create task worker based on template.
     *
     * @param vertx  vertx instance
     * @param config task configuration
     * @return task worker instance
     * @throws UnsupportedOperationException if template is not implemented
     */
    public static TaskWorker create(Vertx vertx, TaskConfig config) {
        TaskTemplate template = config.getTemplate();

        log.info("Creating task worker for template: {}, taskId: {}", template, config.getTaskId());

        if (template == TaskTemplate.CONN_STANDARD) {
            return new ConnStandardWorker(vertx, config);
        } else if (template == TaskTemplate.PUBSUB_STANDARD) {
            return new PubsubStandardWorker(vertx, config);
        } else if (template == TaskTemplate.CUSTOM) {
            throw new UnsupportedOperationException("自定义模板暂未实现");
        }
        throw new IllegalArgumentException("Unknown template: " + template);
    }
}
