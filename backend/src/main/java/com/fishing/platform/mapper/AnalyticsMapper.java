package com.fishing.platform.mapper;

import com.fishing.platform.domain.DomainModels.DailyAmount;
import com.fishing.platform.domain.DomainModels.DailyCount;
import com.fishing.platform.domain.DomainModels.TrafficPoint;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

public interface AnalyticsMapper {

    @Select("""
            SELECT stat_date, visits, unique_visitors,
                   0 AS new_members, 0 AS booking_count,
                   0 AS sales_order_count, 0 AS product_quantity, 0 AS revenue
            FROM traffic_daily
            WHERE stat_date >= #{startDate}
            ORDER BY stat_date
            """)
    List<TrafficPoint> trafficObservations(@Param("startDate") LocalDate startDate);

    @Select("""
            SELECT CAST(created_at AS DATE) AS stat_date, COUNT(*) AS metric_value
            FROM member
            WHERE created_at >= #{startDate}
            GROUP BY CAST(created_at AS DATE)
            ORDER BY stat_date
            """)
    List<DailyCount> newMembers(@Param("startDate") LocalDate startDate);

    @Select("""
            SELECT fishing_date AS stat_date, COUNT(*) AS metric_value
            FROM booking
            WHERE fishing_date >= #{startDate}
              AND status IN ('CONFIRMED', 'COMPLETED')
            GROUP BY fishing_date
            ORDER BY stat_date
            """)
    List<DailyCount> bookings(@Param("startDate") LocalDate startDate);

    @Select("""
            SELECT CAST(created_at AS DATE) AS stat_date, COUNT(*) AS metric_value
            FROM sales_order
            WHERE created_at >= #{startDate}
              AND status != 'CANCELLED'
            GROUP BY CAST(created_at AS DATE)
            ORDER BY stat_date
            """)
    List<DailyCount> salesOrders(@Param("startDate") LocalDate startDate);

    @Select("""
            SELECT CAST(o.created_at AS DATE) AS stat_date,
                   COALESCE(SUM(i.quantity), 0) AS metric_value
            FROM sales_order o
            JOIN sales_order_item i ON i.order_id = o.id
            WHERE o.created_at >= #{startDate}
              AND o.status != 'CANCELLED'
            GROUP BY CAST(o.created_at AS DATE)
            ORDER BY stat_date
            """)
    List<DailyCount> productQuantities(@Param("startDate") LocalDate startDate);

    @Select("""
            SELECT CAST(confirmed_at AS DATE) AS stat_date,
                   COALESCE(SUM(amount), 0) AS metric_value
            FROM payment
            WHERE status = 'PAID'
              AND confirmed_at >= #{startDate}
            GROUP BY CAST(confirmed_at AS DATE)
            ORDER BY stat_date
            """)
    List<DailyAmount> revenue(@Param("startDate") LocalDate startDate);
}
