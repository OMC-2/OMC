function fn() {
    var config = {
        baseUrl: 'http://localhost:8080',
        paymentServiceUrl: 'http://localhost:8085',
        gatewaySecret: '',
        adminSecret: ''
    };

    var gatewaySecret = java.lang.System.getenv('GATEWAY_SECRET');
    if (gatewaySecret) config.gatewaySecret = gatewaySecret;

    var adminSecret = java.lang.System.getenv('ADMIN_SECRET');
    if (adminSecret) config.adminSecret = adminSecret;

    var baseUrl = java.lang.System.getenv('BASE_URL');
    if (baseUrl) config.baseUrl = baseUrl;

    var paymentServiceUrl = java.lang.System.getenv('PAYMENT_SERVICE_URL');
    if (paymentServiceUrl) config.paymentServiceUrl = paymentServiceUrl;

    karate.configure('printEnabled', true);

    return config;
}
