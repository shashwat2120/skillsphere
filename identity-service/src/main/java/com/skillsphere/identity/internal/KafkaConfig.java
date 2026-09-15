package com.skillsphere.identity.internal;

import com.skillsphere.identity.events.IdentityEventEnvelope;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Spring Boot's own Kafka autoconfiguration publishes a
 * {@code KafkaTemplate<Object, Object>} bean, and Java generics are
 * invariant — that bean cannot satisfy {@link KafkaEventBridge}'s
 * constructor, which asks for {@code KafkaTemplate<String,
 * IdentityEventEnvelope>}. This is the one bean this service has to define
 * itself; everything else about the producer (bootstrap servers,
 * serializers) still comes from {@code spring.kafka.*} in application.yml
 * via the autoconfigured {@link KafkaProperties}.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public ProducerFactory<String, IdentityEventEnvelope> identityEventProducerFactory(
            KafkaProperties kafkaProperties) {
        return new DefaultKafkaProducerFactory<>(kafkaProperties.buildProducerProperties());
    }

    @Bean
    public KafkaTemplate<String, IdentityEventEnvelope> identityEventKafkaTemplate(
            ProducerFactory<String, IdentityEventEnvelope> identityEventProducerFactory) {
        return new KafkaTemplate<>(identityEventProducerFactory);
    }
}
