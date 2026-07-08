package com.omc.coupon.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@Endpoint(id = "forcegc")
public class ForceGcEndpoint {

    @WriteOperation
    public Map<String, Object> forceGc() {
        Runtime rt = Runtime.getRuntime();
        long beforeMb = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        System.gc();
        long afterMb = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        log.info("[ForceGC] 힙 정리: {}MB → {}MB ({}MB 회수)", beforeMb, afterMb, beforeMb - afterMb);
        return Map.of("before_mb", beforeMb, "after_mb", afterMb, "freed_mb", beforeMb - afterMb);
    }
}
