package com.example.interview.notifications;

import java.time.Duration;
import java.util.Map;

import com.example.interview.common.OrderCreated;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaDeliveryTest {
    @Test
    void poisonRecordIsRecoveredToDltWithOriginalPayload() throws Exception {
        EmbeddedKafkaKraftBroker broker = new EmbeddedKafkaKraftBroker(1, 3,
                OrderCreated.TOPIC, OrderCreated.DLT);
        broker.afterPropertiesSet();
        Map<String, Object> producerProperties = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        var producerFactory = new DefaultKafkaProducerFactory<String, String>(producerProperties);
        KafkaTemplate<String, String> template = new KafkaTemplate<>(producerFactory);
        Map<String, Object> consumerProperties = KafkaTestUtils.consumerProps("test-notifications", "false", broker);
        consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        var factory = new DefaultKafkaConsumerFactory<>(consumerProperties,
                new StringDeserializer(), new StringDeserializer());
        ContainerProperties properties = new ContainerProperties(OrderCreated.TOPIC);
        properties.setAckMode(ContainerProperties.AckMode.RECORD);
        properties.setMessageListener((MessageListener<String, String>) record -> {
            throw new IllegalArgumentException("deliberately invalid event");
        });
        var container = new KafkaMessageListenerContainer<>(factory, properties);
        container.setCommonErrorHandler(new KafkaConfiguration().kafkaErrorHandler(template));
        Map<String, Object> dltProperties = KafkaTestUtils.consumerProps("test-dlt", "false", broker);
        try (Consumer<String, String> dlt = new DefaultKafkaConsumerFactory<>(dltProperties,
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(dlt, OrderCreated.DLT);
            container.start();
            ContainerTestUtils.waitForAssignment(container, 3);
            template.send(OrderCreated.TOPIC, "order-key", "poison-payload").get();
            var record = KafkaTestUtils.getSingleRecord(dlt, OrderCreated.DLT, Duration.ofSeconds(30));
            assertThat(record.value()).isEqualTo("poison-payload");
            assertThat(record.key()).isEqualTo("order-key");
            assertThat(record.headers().lastHeader("kafka_dlt-original-topic")).isNotNull();
        } finally {
            container.stop();
            producerFactory.destroy();
            broker.destroy();
        }
    }
}
