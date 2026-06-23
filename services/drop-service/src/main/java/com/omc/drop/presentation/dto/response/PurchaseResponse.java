package com.omc.drop.presentation.dto.response;

import java.util.UUID;

public record PurchaseResponse(UUID orderId, long queueNumber) {
}
