package com.omc.notification.infrastructure.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Deserializer;

import java.nio.charset.StandardCharsets;
import java.util.Map;

// 서비스마다 Kafka 전송 포맷이 다를 수 있어 두 가지를 모두 수용한다.
// - KafkaTemplate<String, Object> + JsonSerializer (drop-service): bytes = { ... }
// - KafkaTemplate<String, String> + JsonSerializer (coupon/order/raffle): bytes = "\"{ ... }\"" (double-encoded)
public class FlexibleMapDeserializer implements Deserializer<Map<String, Object>> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }
        String raw = new String(data, StandardCharsets.UTF_8);
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception e) {
            try {
                String unwrapped = objectMapper.readValue(raw, String.class);
                return objectMapper.readValue(unwrapped, new TypeReference<>() {});
            } catch (Exception ex) {
                throw new RuntimeException(
                        "Kafka payload 파싱 실패. topic=" + topic + ", raw=" + raw, ex);
            }
        }
    }
}
