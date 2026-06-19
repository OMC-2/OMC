package com.omc.drop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DropServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DropServiceApplication.class, args);
    }
}
