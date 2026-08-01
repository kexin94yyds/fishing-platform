package com.fishing.platform.service;

import com.fishing.platform.mapper.DashboardMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {
    private final DashboardMapper mapper;
    private final AnalyticsService analyticsService;

    public DashboardService(DashboardMapper mapper, AnalyticsService analyticsService) {
        this.mapper = mapper;
        this.analyticsService = analyticsService;
    }

    public Map<String, Object> summary() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("openSpots", mapper.openSpots());
        result.put("todayBookings", mapper.todayBookings());
        result.put("activeMembers", mapper.activeMembers());
        result.put("todayRevenue", mapper.todayRevenue());
        result.put("lowStockProducts", mapper.lowStockProducts());
        result.put("todayCatchCount", mapper.todayCatchCount());
        result.put("pendingPayments", mapper.pendingPayments());

        var trafficSeries = analyticsService.trafficSeries(7);
        result.put("visitorsToday", trafficSeries.get(trafficSeries.size() - 1).visits());
        DateTimeFormatter labelFormat = DateTimeFormatter.ofPattern("MM-dd");
        List<Map<String, Object>> trafficTrend = trafficSeries.stream()
                .map(trafficPoint -> {
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("label", trafficPoint.statDate().format(labelFormat));
                    point.put("value", trafficPoint.visits());
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
