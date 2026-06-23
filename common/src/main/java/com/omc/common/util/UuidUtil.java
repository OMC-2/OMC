package com.omc.common.util;

import com.github.f4b6a3.uuid.UuidCreator;
import java.util.UUID;

/**
 * UUID 생성 관련 유틸리티 메서드를 제공합니다.
 * 특히, 데이터베이스 인덱싱 효율성을 위한 UUIDv7 생성을 지원합니다.
 */
public class UuidUtil {
    
    private UuidUtil() {
        // utility class
    }

    /**
     * Generates a UUIDv7 (time-ordered).
     * Very efficient for DB indexing.
     */
    public static UUID v7() {
        return UuidCreator.getTimeOrderedEpoch();
    }
}
