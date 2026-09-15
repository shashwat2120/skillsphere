package com.skillsphere.career.internal;

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
 * defaults to {@link SkillCatalogChangedEvent} for {@link
 * SkillCatalogEventListener}'s topic. {@link SkillPrerequisiteEventListener}
 * 's {@code skill-prerequisite-events} carries a different shape — see
 * analytics-service's own copy of this class for the fuller reasoning,
 * the first place this pattern was needed in this split.
 */
@Configuration
public class SkillPrerequisiteKafkaConfig {

    @Bean
    public ConsumerFactory<String, Object> skillPrerequisiteConsumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties();
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, SkillPrerequisiteChangedEvent.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.skillsphere.career.internal");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> skillPrerequisiteListenerContainerFactory(
            ConsumerFactory<String, Object> skillPrerequisiteConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(skillPrerequisiteConsumerFactory);
        return factory;
    }
}
