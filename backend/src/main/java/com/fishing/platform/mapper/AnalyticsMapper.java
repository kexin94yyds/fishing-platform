package com.fishing.platform.mapper;

import com.fishing.platform.domain.DomainModels.TrafficPoint;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

public interface AnalyticsMapper {

    @Select("""
            SELECT stat_date, visits, unique_visitors, new_members, booking_count, revenue
            FROM traffic_daily
            WHERE stat_date >= #{startDate}
            ORDER BY stat_date
            """)
    List<TrafficPoint> traffic(@Param("startDate") LocalDate startDate);
}
