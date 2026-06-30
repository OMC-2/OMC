package com.omc.drop.infrastructure.client;

public class ProductServiceUnavailableException extends RuntimeException {

    public ProductServiceUnavailableException(Throwable cause) {
        super("product-service 호출 실패", cause);
    }
}
