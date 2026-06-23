package com.omc.raffle.application.port.out;

import java.util.concurrent.CompletableFuture;

public interface EventProducerPort {
    CompletableFuture<Boolean> send(String topic, String key, String payload);
}
