package com.fishing.platform.service;

import com.fishing.platform.mapper.AnalyticsMapper;
import com.fishing.platform.domain.DomainModels.DailyAmount;
import com.fishing.platform.domain.DomainModels.DailyCount;
import com.fishing.platform.domain.DomainModels.TrafficPoint;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Service
public class AnalyticsService {
    private final AnalyticsMapper mapper;
    private final Clock businessClock;

    public AnalyticsService(AnalyticsMapper mapper, Clock businessClock) {
        this.mapper = mapper;
        this.businessClock = businessClock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> traffic(int requestedDays) {
        int days = Math.max(1, Math.min(90, requestedDays));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", days);
        result.put("series", trafficSeries(days));
        return result;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<TrafficPoint> trafficSeries(int requestedDays) {
        int days = Math.max(1, Math.min(90, requestedDays));
        LocalDate startDate = LocalDate.now(businessClock).minusDays(days - 1L);

        Map<LocalDate, TrafficPoint> trafficByDate = new HashMap<>();
        mapper.trafficObservations(startDate)
                .forEach(point -> trafficByDate.put(point.statDate(), point));
        Map<LocalDate, Long> membersByDate = countsByDate(mapper.newMembers(startDate));
        Map<LocalDate, Long> bookingsByDate = countsByDate(mapper.bookings(startDate));
        Map<LocalDate, Long> salesOrdersByDate = countsByDate(mapper.salesOrders(startDate));
        Map<LocalDate, Long> productQuantitiesByDate = countsByDate(mapper.productQuantities(startDate));
        Map<LocalDate, BigDecimal> revenueByDate = new HashMap<>();
        mapper.revenue(startDate)
                .forEach(point -> revenueByDate.put(point.statDate(), point.metricValue()));

        return IntStream.range(0, days)
                .mapToObj(offset -> startDate.plusDays(offset))
                .map(date -> {
                    TrafficPoint observation = trafficByDate.get(date);
                    int visits = observation == null ? 0 : observation.visits();
                    int uniqueVisitors = observation == null ? 0 : observation.uniqueVisitors();
                    return new TrafficPoint(
                            date,
                            visits,
                            uniqueVisitors,
                            Math.toIntExact(membersByDate.getOrDefault(date, 0L)),
                            Math.toIntExact(bookingsByDate.getOrDefault(date, 0L)),
                            Math.toIntExact(salesOrdersByDate.getOrDefault(date, 0L)),
                            Math.toIntExact(productQuantitiesByDate.getOrDefault(date, 0L)),
                            revenueByDate.getOrDefault(date, BigDecimal.ZERO));
                })
                .toList();
    }

    private Map<LocalDate, Long> countsByDate(List<DailyCount> points) {
        Map<LocalDate, Long> result = new HashMap<>();
        points.forEach(point -> result.put(point.statDate(), point.metricValue()));
        return result;
    }
}
