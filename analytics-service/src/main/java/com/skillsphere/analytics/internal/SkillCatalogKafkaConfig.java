package com.skillsphere.analytics.internal;

import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.Map;

/**
 * A second, differently-typed consumer factory.
 *
 * <p>This service's own {@code spring.kafka.consumer.*} properties (see
 * application.yml) set one global {@code spring.json.value.default.type} —
 * {@code ResponseRecordedEvent}, for {@link ResponseEventListener}'s
 * {@code assessment-events} topic. That works because every consumer in
 * every other service in this split so far only ever listens to one topic.
 * This is the first service that needs a second: {@link
 * SkillCatalogEventListener}'s {@code skill-catalog-events} carries a
 * different shape entirely. Spring Kafka resolves the deserializer's
 * default type per {@code ConsumerFactory}, not per listener method, so a
 * second factory — referenced by {@code containerFactory} on the second
 * {@code @KafkaListener} — is the mechanism, not a workaround.
 */
@Configuration
public class SkillCatalogKafkaConfig {

    @Bean
    public ConsumerFactory<String, Object> skillCatalogConsumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties();
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, SkillCatalogChangedEvent.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.skillsphere.analytics.internal");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> skillCatalogListenerContainerFactory(
            ConsumerFactory<String, Object> skillCatalogConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(skillCatalogConsumerFactory);
        return factory;
    }
}
