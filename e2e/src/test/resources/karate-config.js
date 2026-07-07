function fn() {
    var config = {
        baseUrl: 'http://localhost:8080',
        couponServiceUrl: 'http://localhost:8087',
        paymentServiceUrl: 'http://localhost:8085',
        orderServiceUrl: 'http://localhost:8083',
        kafkaBootstrapServers: 'localhost:9092',
        gatewaySecret: 'local-secret',
        adminSecret: 'local-admin-secret'
    };

    var gatewaySecret = java.lang.System.getenv('GATEWAY_SECRET');
    if (gatewaySecret) config.gatewaySecret = gatewaySecret;

    var adminSecret = java.lang.System.getenv('ADMIN_SECRET');
    if (adminSecret) config.adminSecret = adminSecret;

    var baseUrl = java.lang.System.getenv('BASE_URL');
    if (baseUrl) config.baseUrl = baseUrl;

    var paymentServiceUrl = java.lang.System.getenv('PAYMENT_SERVICE_URL');
    if (paymentServiceUrl) config.paymentServiceUrl = paymentServiceUrl;

    var orderServiceUrl = java.lang.System.getenv('ORDER_SERVICE_URL');
    if (orderServiceUrl) config.orderServiceUrl = orderServiceUrl;

    var kafkaBootstrapServers = java.lang.System.getenv('KAFKA_BOOTSTRAP_SERVERS');
    if (kafkaBootstrapServers) config.kafkaBootstrapServers = kafkaBootstrapServers;

    karate.configure('printEnabled', true);

    return config;
}
