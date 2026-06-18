function fn() {
    var config = {
        baseUrl: 'http://localhost:8080',
        gatewaySecret: '',
        adminSecret: ''
    };

    var gatewaySecret = java.lang.System.getenv('GATEWAY_SECRET');
    if (gatewaySecret) config.gatewaySecret = gatewaySecret;

    var adminSecret = java.lang.System.getenv('ADMIN_SECRET');
    if (adminSecret) config.adminSecret = adminSecret;

    var baseUrl = java.lang.System.getenv('BASE_URL');
    if (baseUrl) config.baseUrl = baseUrl;

    karate.configure('printEnabled', true);

    return config;
}
