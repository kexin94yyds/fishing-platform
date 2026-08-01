package com.fishing.platform.service;

import com.fishing.platform.mapper.DashboardMapper;
import com.fishing.platform.mapper.AnalyticsMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Service
public class DashboardService {
    private final DashboardMapper mapper;
    private final AnalyticsMapper analyticsMapper;

    public DashboardService(DashboardMapper mapper, AnalyticsMapper analyticsMapper) {
        this.mapper = mapper;
        this.analyticsMapper = analyticsMapper;
    }

    public Map<String, Object> summary() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("openSpots", mapper.openSpots());
        result.put("todayBookings", mapper.todayBookings());
        result.put("activeMembers", mapper.activeMembers());
        result.put("todayRevenue", mapper.todayRevenue());
        result.put("lowStockProducts", mapper.lowStockProducts());
        result.put("todayCatchCount", mapper.todayCatchCount());
        result.put("visitorsToday", mapper.visitorsToday());
        result.put("pendingPayments", mapper.pendingPayments());

        LocalDate start = LocalDate.now().minusDays(6);
        Map<LocalDate, Integer> visitsByDate = new HashMap<>();
        analyticsMapper.traffic(start).forEach(point -> visitsByDate.put(point.statDate(), point.visits()));
        DateTimeFormatter labelFormat = DateTimeFormatter.ofPattern("MM-dd");
        List<Map<String, Object>> trafficTrend = IntStream.range(0, 7)
                .mapToObj(offset -> start.plusDays(offset))
                .map(date -> {
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("label", date.format(labelFormat));
                    point.put("value", visitsByDate.getOrDefault(date, 0));
                    return point;
                })
                .toList();
        result.put("trafficTrend", trafficTrend);
        List<Map<String, Object>> bookingMix = mapper.bookingMix().stream()
                .map(metric -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", metric.name());
                    item.put("value", metric.metricValue());
                    return item;
                })
                .toList();
        result.put("bookingMix", bookingMix);
        result.put("recentBookings", mapper.recentBookings());
        return result;
    }
}
