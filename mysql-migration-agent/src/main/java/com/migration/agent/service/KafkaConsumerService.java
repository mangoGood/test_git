package com.migration.agent.service;

import com.google.gson.Gson;
import com.migration.agent.model.TaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class KafkaConsumerService {
    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerService.class);
    private static final String TASK_CREATED_TOPIC = "sync-task-created";
    
    private final String bootstrapServers;
    private final String groupId;
    private final TaskHandler taskHandler;
    private final Gson gson = new Gson();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executorService;
    
    public interface TaskHandler {
        void handleTask(TaskMessage taskMessage);
    }
    
    public KafkaConsumerService(String bootstrapServers, String groupId, TaskHandler taskHandler) {
        this.bootstrapServers = bootstrapServers;
        this.groupId = groupId;
        this.taskHandler = taskHandler;
    }
    
    public void start() {
        if (running.getAndSet(true)) {
            logger.warn("Kafka consumer is already running");
            return;
        }
        
        executorService = Executors.newSingleThreadExecutor();
        executorService.submit(this::consumeMessages);
        logger.info("Kafka consumer started");
    }
    
    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }
        
        if (executorService != null) {
            executorService.shutdown();
        }
        logger.info("Kafka consumer stopped");
    }
    
    private void consumeMessages() {
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("group.id", groupId);
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("auto.offset.reset", "latest");
        
        org.apache.kafka.clients.consumer.KafkaConsumer<String, String> consumer = 
            new org.apache.kafka.clients.consumer.KafkaConsumer<>(props);
        
        try {
            consumer.subscribe(java.util.Collections.singletonList(TASK_CREATED_TOPIC));
            logger.info("Subscribed to topic: {}", TASK_CREATED_TOPIC);
            
            while (running.get()) {
                try {
                    org.apache.kafka.clients.consumer.ConsumerRecords<String, String> records = 
                        consumer.poll(java.time.Duration.ofMillis(1000));
                    
                    for (org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record : records) {
                        try {
                            logger.info("Received message: {}", record.value());
                            TaskMessage taskMessage = gson.fromJson(record.value(), TaskMessage.class);
                            taskHandler.handleTask(taskMessage);
                        } catch (Exception e) {
                            logger.error("Error processing message: {}", record.value(), e);
                        }
                    }
                } catch (Exception e) {
                    if (running.get()) {
                        logger.error("Error consuming messages", e);
                        Thread.sleep(5000);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Fatal error in consumer thread", e);
        } finally {
            consumer.close();
        }
    }
}