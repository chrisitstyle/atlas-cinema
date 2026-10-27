package com.atlascinema.notification.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Bean
    public KafkaTemplate<String, Object> dltKafkaTemplate(
            KafkaProperties kafkaProperties,
            KafkaConnectionDetails connectionDetails
    ) {
        Map<String, Object> producerProperties = new HashMap<>(
                        kafkaProperties.buildProducerProperties());

        /*
         * ServiceConnection can replace the Kafka address in tests
         * so use the actual connection details instead of a hardcoded address
         */
        producerProperties.put(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                connectionDetails
                        .getProducer()
                        .getBootstrapServers());

        /*
         * Serializers are provided directly to the ProducerFactory
         * so remove serializer classes inherited from application properties
         */
        producerProperties.remove(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG);

        producerProperties.remove(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG);

        Map<Class<?>, org.apache.kafka.common.serialization.Serializer<?>>
                serializers = new LinkedHashMap<>();

        // Deserialization failures contain the original raw Kafka payload
        serializers.put(
                byte[].class,
                new ByteArraySerializer());

        // Normal listener failures contain the already deserialized event object
        serializers.put(Object.class, new JacksonJsonSerializer<>().noTypeInfo());

        DelegatingByTypeSerializer valueSerializer = new DelegatingByTypeSerializer(
                        serializers,
                        true);

        DefaultKafkaProducerFactory<String, Object> producerFactory = new DefaultKafkaProducerFactory<>(
                        producerProperties,
                        new StringSerializer(),
                        valueSerializer);

        return new KafkaTemplate<>(producerFactory);
    }
}