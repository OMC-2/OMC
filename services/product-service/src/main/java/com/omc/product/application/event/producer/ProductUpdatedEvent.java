package com.omc.product.application.event.producer;

import java.util.UUID;

public record ProductUpdatedEvent(UUID productId) {}
