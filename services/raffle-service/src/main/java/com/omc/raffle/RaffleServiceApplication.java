package com.omc.raffle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * 래플(Raffle) 서비스의 진입점이 되는 Spring Boot 메인 애플리케이션 클래스입니다.
 */
@SpringBootApplication(exclude = { org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class })
@EnableFeignClients
@EnableJpaAuditing
public class RaffleServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RaffleServiceApplication.class, args);
    }
}
