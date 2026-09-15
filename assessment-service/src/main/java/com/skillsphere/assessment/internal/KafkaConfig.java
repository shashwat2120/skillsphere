package com.skillsphere.assessment.internal;

import com.skillsphere.assessment.ItemCatalogChanged;
import com.skillsphere.assessment.ResponseRecorded;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Spring Boot's own Kafka autoconfiguration publishes a
 * {@code KafkaTemplate<Object, Object>} bean, and Java generics are
 * invariant — that bean cannot satisfy {@link AnalyticsEventBridge}'s or
 * {@link ItemCatalogKafkaBridge}'s constructors, which each ask for their
 * own narrowly-typed {@code KafkaTemplate}. Same fix identity-service's own
 * KafkaConfig uses: one explicitly typed bean per event type, everything
 * else about the producer still comes from {@code spring.kafka.*}.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public ProducerFactory<String, ResponseRecorded> responseRecordedProducerFactory(
            KafkaProperties kafkaProperties) {
        return new DefaultKafkaProducerFactory<>(kafkaProperties.buildProducerProperties());
    }

    @Bean
    public KafkaTemplate<String, ResponseRecorded> responseRecordedKafkaTemplate(
            ProducerFactory<String, ResponseRecorded> responseRecordedProducerFactory) {
        return new KafkaTemplate<>(responseRecordedProducerFactory);
    }

    @Bean
    public ProducerFactory<String, ItemCatalogChanged> itemCatalogChangedProducerFactory(
            KafkaProperties kafkaProperties) {
        return new DefaultKafkaProducerFactory<>(kafkaProperties.buildProducerProperties());
    }

    @Bean
    public KafkaTemplate<String, ItemCatalogChanged> itemCatalogChangedKafkaTemplate(
            ProducerFactory<String, ItemCatalogChanged> itemCatalogChangedProducerFactory) {
        return new KafkaTemplate<>(itemCatalogChangedProducerFactory);
    }
}
