package com.fishing.platform.service;

import com.fishing.platform.mapper.AnalyticsMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AnalyticsService {
    private final AnalyticsMapper mapper;

    public AnalyticsService(AnalyticsMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, Object> traffic(int requestedDays) {
        int days = Math.max(1, Math.min(90, requestedDays));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", days);
        result.put("series", mapper.traffic(LocalDate.now().minusDays(days - 1L)));
        return result;
    }
}
