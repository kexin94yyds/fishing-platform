package com.fishing.platform.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BusinessTimeConfigTest {

    @Test
    void businessClockUsesShanghaiCalendarDateAtUtcBoundary() {
        Clock configuredClock = new BusinessTimeConfig().businessClock();
        assertEquals(ZoneId.of("Asia/Shanghai"), configuredClock.getZone());

        Clock boundaryClock = Clock.fixed(
                Instant.parse("2026-08-07T16:30:00Z"), configuredClock.getZone());
        assertEquals(LocalDate.of(2026, 8, 8), LocalDate.now(boundaryClock));
    }
}
