package com.skillsphere.skill.internal;

import com.skillsphere.skill.SkillCatalogChanged;
import com.skillsphere.skill.SkillPrerequisiteChanged;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Spring Boot's own Kafka autoconfiguration publishes a
 * {@code KafkaTemplate<Object, Object>} bean, which Java's invariant
 * generics reject for {@link SkillCatalogKafkaBridge} and
 * {@link SkillPrerequisiteKafkaBridge}'s narrower constructors — the same
 * fix every other Kafka producer in this codebase uses: one explicitly
 * typed bean per event type.
 */
@Configuration
public class SkillCatalogKafkaConfig {

    @Bean
    public ProducerFactory<String, SkillCatalogChanged> skillCatalogChangedProducerFactory(
            KafkaProperties kafkaProperties) {
        return new DefaultKafkaProducerFactory<>(kafkaProperties.buildProducerProperties());
    }

    @Bean
    public KafkaTemplate<String, SkillCatalogChanged> skillCatalogChangedKafkaTemplate(
            ProducerFactory<String, SkillCatalogChanged> skillCatalogChangedProducerFactory) {
        return new KafkaTemplate<>(skillCatalogChangedProducerFactory);
    }

    @Bean
    public ProducerFactory<String, SkillPrerequisiteChanged> skillPrerequisiteChangedProducerFactory(
            KafkaProperties kafkaProperties) {
        return new DefaultKafkaProducerFactory<>(kafkaProperties.buildProducerProperties());
    }

    @Bean
    public KafkaTemplate<String, SkillPrerequisiteChanged> skillPrerequisiteChangedKafkaTemplate(
            ProducerFactory<String, SkillPrerequisiteChanged> skillPrerequisiteChangedProducerFactory) {
        return new KafkaTemplate<>(skillPrerequisiteChangedProducerFactory);
    }
}
