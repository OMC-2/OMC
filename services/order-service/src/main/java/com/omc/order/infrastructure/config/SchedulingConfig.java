package com.omc.order.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

//메인 클래스 수정 없이 @Sheduled 활성화
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
