package com.fishing.platform.service;

import com.fishing.platform.mapper.DashboardMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {
    private final DashboardMapper mapper;
    private final AnalyticsService analyticsService;
    private final Clock businessClock;

    public DashboardService(DashboardMapper mapper, AnalyticsService analyticsService, Clock businessClock) {
        this.mapper = mapper;
        this.analyticsService = analyticsService;
        this.businessClock = businessClock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> summary() {
        LocalDate today = LocalDate.now(businessClock);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("openSpots", mapper.openSpots());
        result.put("todayBookings", mapper.todayBookings(today));
        result.put("activeMembers", mapper.activeMembers());
        result.put("todayRevenue", mapper.todayRevenue(today.atStartOfDay(), today.plusDays(1).atStartOfDay()));
        result.put("lowStockProducts", mapper.lowStockProducts());
        result.put("todayCatchCount", mapper.todayCatchCount(today));
        result.put("pendingPayments", mapper.pendingPayments());
        result.put("todaySalesOrders", mapper.todaySalesOrders(
                today.atStartOfDay(), today.plusDays(1).atStartOfDay()));
        result.put("todayProductQuantity", mapper.todayProductQuantity(
                today.atStartOfDay(), today.plusDays(1).atStartOfDay()));

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
        List<Map<String, Object>> bookingMix = mapper.bookingMix(today).stream()
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
