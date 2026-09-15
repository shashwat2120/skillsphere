package com.skillsphere.realtime.internal;

import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.Map;

/**
 * A second, differently-typed consumer factory — this service's default
 * one (configured via {@code spring.kafka.consumer.*} in application.yml)
 * defaults to {@link SkillCatalogChangedEvent} for
 * {@link SkillCatalogEventListener}'s topic. {@link
 * ItemCatalogEventListener}'s {@code item-catalog-events} carries a
 * different shape, and Spring Kafka resolves a JSON deserializer's default
 * type per {@code ConsumerFactory}, not per listener method — see
 * analytics-service's own copy of this class for the fuller reasoning,
 * the first place this pattern was needed in this split.
 */
@Configuration
public class ItemCatalogKafkaConfig {

    @Bean
    public ConsumerFactory<String, Object> itemCatalogConsumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties();
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ItemCatalogChangedEvent.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.skillsphere.realtime.internal");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> itemCatalogListenerContainerFactory(
            ConsumerFactory<String, Object> itemCatalogConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(itemCatalogConsumerFactory);
        return factory;
    }
}
