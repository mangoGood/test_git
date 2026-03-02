package com.synctask.service;

import com.synctask.dto.TaskCreatedMessage;
import com.synctask.entity.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class KafkaProducerService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaProducerService.class);

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.kafka.topics.task-created}")
    private String taskCreatedTopic;

    public void sendTaskCreatedMessage(Workflow workflow) {
        TaskCreatedMessage message = new TaskCreatedMessage();
        message.setTaskId(workflow.getId());
        message.setTaskName(workflow.getName());
        message.setUserId(workflow.getUserId());
        message.setSourceConnection(workflow.getSourceConnection());
        message.setTargetConnection(workflow.getTargetConnection());
        message.setCreatedAt(workflow.getCreatedAt());

        logger.info("发送任务创建消息到 Kafka: taskId={}, topic={}", workflow.getId(), taskCreatedTopic);

        CompletableFuture<SendResult<String, Object>> future = 
            kafkaTemplate.send(taskCreatedTopic, workflow.getId(), message);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                logger.info("任务创建消息发送成功: taskId={}, partition={}, offset={}", 
                    workflow.getId(), result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            } else {
                logger.error("任务创建消息发送失败: taskId={}", workflow.getId(), ex);
            }
        });
    }

    public void sendTaskCreatedMessageSync(Workflow workflow) throws Exception {
        TaskCreatedMessage message = new TaskCreatedMessage();
        message.setTaskId(workflow.getId());
        message.setTaskName(workflow.getName());
        message.setUserId(workflow.getUserId());
        message.setSourceConnection(workflow.getSourceConnection());
        message.setTargetConnection(workflow.getTargetConnection());
        message.setCreatedAt(workflow.getCreatedAt());

        logger.info("同步发送任务创建消息到 Kafka: taskId={}, topic={}", workflow.getId(), taskCreatedTopic);

        try {
            SendResult<String, Object> result = kafkaTemplate.send(taskCreatedTopic, workflow.getId(), message).get();
            logger.info("任务创建消息发送成功: taskId={}, partition={}, offset={}", 
                workflow.getId(), result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
        } catch (Exception e) {
            logger.error("任务创建消息发送失败: taskId={}", workflow.getId(), e);
            throw e;
        }
    }
}
