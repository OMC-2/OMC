package e2e.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public final class KafkaEventPublisher {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private KafkaEventPublisher() {
    }

    public static void publishStockFailed(
            String bootstrapServers,
            String eventId,
            String orderId,
            String productId,
            String dropId,
            String userId
    ) {
        String payload = toJson(Map.of(
                "eventId", eventId,
                "orderId", orderId,
                "productId", productId,
                "dropId", dropId,
                "userId", userId
        ));

        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.ACKS_CONFIG, "all");

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(properties)) {
            producer.send(new ProducerRecord<>("stock.failed", orderId, payload))
                    .get(10, TimeUnit.SECONDS);
            producer.flush();
        } catch (Exception e) {
            throw new IllegalStateException("stock.failed 이벤트 발행에 실패했습니다", e);
        }
    }

    private static String toJson(Map<String, String> payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("stock.failed 이벤트 직렬화에 실패했습니다", e);
        }
    }
}
