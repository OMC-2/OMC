package com.omc.product.application.event;

import java.util.UUID;

public record ProductUpdatedEvent(UUID productId) {}
