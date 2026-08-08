package com.fishing.platform.mapper;

import com.fishing.platform.domain.DomainModels.DashboardMetric;
import com.fishing.platform.domain.DomainModels.RecentBooking;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface DashboardMapper {

    @Select("""
            SELECT COUNT(*)
            FROM fishing_spot s
            JOIN fishing_zone z ON z.id = s.zone_id
            WHERE s.status = 'OPEN' AND z.status = 'ACTIVE'
            """)
    long openSpots();

    @Select("SELECT COUNT(*) FROM booking WHERE fishing_date = #{today} AND status != 'CANCELLED'")
    long todayBookings(@Param("today") LocalDate today);

    @Select("SELECT COUNT(*) FROM member WHERE status = 'ACTIVE'")
    long activeMembers();

    @Select("""
            SELECT COALESCE(SUM(amount), 0)
            FROM payment
            WHERE status = 'PAID'
              AND confirmed_at >= #{dayStart}
              AND confirmed_at < #{dayEnd}
            """)
    BigDecimal todayRevenue(@Param("dayStart") LocalDateTime dayStart,
                            @Param("dayEnd") LocalDateTime dayEnd);

    @Select("SELECT COUNT(*) FROM product WHERE status = 'ACTIVE' AND stock_quantity <= 10")
    long lowStockProducts();

    @Select("SELECT COALESCE(SUM(quantity), 0) FROM catch_record WHERE fishing_date = #{today} AND status != 'VOID'")
    long todayCatchCount(@Param("today") LocalDate today);

    @Select("SELECT COUNT(*) FROM payment WHERE status = 'PENDING'")
    long pendingPayments();

    @Select("""
            SELECT status AS name, COUNT(*) AS metric_value
            FROM booking
            WHERE fishing_date = #{today}
            GROUP BY status
            ORDER BY status
            """)
    List<DashboardMetric> bookingMix(@Param("today") LocalDate today);

    @Select("""
            SELECT b.booking_no, m.name AS member_name, s.name AS spot_name,
                   b.fishing_date, b.time_slot, b.status, b.amount
            FROM booking b
            LEFT JOIN member m ON m.id = b.member_id
            JOIN fishing_spot s ON s.id = b.spot_id
            ORDER BY b.created_at DESC, b.id DESC
            LIMIT 5
            """)
    List<RecentBooking> recentBookings();
}
