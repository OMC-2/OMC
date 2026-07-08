package com.omc.coupon.infrastructure.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;

import java.lang.management.ManagementFactory;
import javax.management.MBeanServer;
import javax.management.ObjectName;

@Configuration
public class MetricsConfig {

    public MetricsConfig(MeterRegistry registry) {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            ObjectName name = new ObjectName("jdk.management.virtualthreads:type=VirtualThreads");
            
            // isRegistered 체크를 제거하여 지연 평가(Lazy Binding)로 지표 강제 등록
            Gauge.builder("jdk_virtual_thread_pinning_total", mbs, server -> {
                try {
                    return ((Long) server.getAttribute(name, "PinnedThreadCount")).doubleValue();
                } catch (Exception e) {
                    return 0.0; // MBean이 아직 등록되지 않았거나 속성이 없으면 0.0 리턴
                }
            }).register(registry);
        } catch (Exception ignored) {
        }
    }
}
