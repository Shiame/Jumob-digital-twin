package com.gama_adapter.gama_adapter.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaProducerService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaProducerService.class);
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${kafka.topic.gama-state:gama-state}")
    private String topic;

    public KafkaProducerService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendMessage(String message) {
        logger.info("Producing message to topic {}", topic);
        logger.debug("Message content: {}", message.length() > 300 ? message.substring(0, 300) + "..." : message);
        kafkaTemplate.send(topic, message);
    }
}
