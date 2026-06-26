package com.omc.gateway;

import org.junit.jupiter.api.Test;

class DockerEnvDebugTest {

    @Test
    void printDockerEnvVars() {
        System.out.println("=== Docker Env Debug ===");
        System.out.println("DOCKER_HOST=" + System.getenv("DOCKER_HOST"));
        System.out.println("DOCKER_API_VERSION=" + System.getenv("DOCKER_API_VERSION"));
        System.out.println("docker.host sys prop=" + System.getProperty("docker.host"));
    }
}
