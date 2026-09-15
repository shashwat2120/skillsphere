package com.skillsphere.shared.audit;

import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Spring Boot's own Kafka autoconfiguration publishes a
 * {@code KafkaTemplate<Object, Object>} bean, and Java generics are
 * invariant — that bean cannot satisfy {@link AdminAuditKafkaBridge}'s
 * constructor, which asks for {@code KafkaTemplate<String,
 * AdminActionRecorded>}. Same fix {@code assessment.internal.KafkaConfig}
 * (for {@code ResponseRecorded}) already uses: one explicitly typed bean,
 * everything else about the producer still comes from {@code spring.kafka.*}.
 */
@Configuration
public class AuditKafkaConfig {

    @Bean
    public ProducerFactory<String, AdminActionRecorded> adminActionRecordedProducerFactory(
            KafkaProperties kafkaProperties) {
        return new DefaultKafkaProducerFactory<>(kafkaProperties.buildProducerProperties());
    }

    @Bean
    public KafkaTemplate<String, AdminActionRecorded> adminActionRecordedKafkaTemplate(
            ProducerFactory<String, AdminActionRecorded> adminActionRecordedProducerFactory) {
        return new KafkaTemplate<>(adminActionRecordedProducerFactory);
    }
}
