package com.omc.common.util;

import com.github.f4b6a3.uuid.UuidCreator;
import java.util.UUID;

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
