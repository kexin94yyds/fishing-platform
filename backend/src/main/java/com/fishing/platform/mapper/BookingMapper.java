package com.fishing.platform.mapper;

import com.fishing.platform.domain.DomainModels.Booking;
import com.fishing.platform.domain.DomainModels.SpotAvailability;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface BookingMapper {

    String BOOKING_SELECT = """
            SELECT b.id, b.booking_no, b.member_id, m.name AS member_name,
                   b.spot_id, s.name AS spot_name, z.name AS zone_name,
                   b.fishing_date, b.time_slot, b.guests, b.amount, b.status,
                   b.notes, b.cancelled_at, b.created_at, b.updated_at
            FROM booking b
            JOIN fishing_spot s ON s.id = b.spot_id
            JOIN fishing_zone z ON z.id = s.zone_id
            LEFT JOIN member m ON m.id = b.member_id
            """;

    @Select("""
            <script>
            """ + BOOKING_SELECT + """
            <where>
              <if test="status != null and status != ''">AND b.status = #{status}</if>
              <if test="date != null">AND b.fishing_date = #{date}</if>
              <if test="memberId != null">AND b.member_id = #{memberId}</if>
            </where>
            ORDER BY b.created_at DESC, b.id DESC
            </script>
            """)
    List<Booking> findAll(@Param("status") String status,
                          @Param("date") LocalDate date,
                          @Param("memberId") Long memberId);

    @Select(BOOKING_SELECT + " WHERE b.id = #{id}")
    Booking findById(@Param("id") Long id);

    @Select(BOOKING_SELECT + " WHERE b.id = #{id} FOR UPDATE")
    Booking findByIdForUpdate(@Param("id") Long id);

    @Select(BOOKING_SELECT + " WHERE b.booking_no = #{bookingNo}")
    Booking findByNo(@Param("bookingNo") String bookingNo);

    @Select("""
            SELECT COUNT(*) > 0
            FROM catch_record
            WHERE booking_id = #{bookingId}
              AND status != 'VOID'
            """)
    boolean hasActiveCatch(@Param("bookingId") Long bookingId);

    @Insert("""
            INSERT IGNORE INTO fishing_slot_inventory
                (spot_id, fishing_date, time_slot, capacity, reserved_count, status)
            SELECT s.id, #{date}, #{timeSlot}, s.capacity, 0, 'AVAILABLE'
            FROM fishing_spot s
            JOIN fishing_zone z ON z.id = s.zone_id
            WHERE s.id = #{spotId} AND s.status = 'OPEN' AND z.status = 'ACTIVE'
            """)
    int insertInventoryIfMissing(@Param("spotId") Long spotId,
                                 @Param("date") LocalDate date,
                                 @Param("timeSlot") String timeSlot);

    @Update("""
            UPDATE fishing_slot_inventory
            SET reserved_count = reserved_count + #{guests}, updated_at = CURRENT_TIMESTAMP
            WHERE spot_id = #{spotId}
              AND fishing_date = #{date}
              AND time_slot = #{timeSlot}
              AND status = 'AVAILABLE'
              AND reserved_count + #{guests} <= capacity
              AND EXISTS (
                  SELECT 1
                  FROM fishing_spot s
                  JOIN fishing_zone z ON z.id = s.zone_id
                  WHERE s.id = fishing_slot_inventory.spot_id
                    AND s.status = 'OPEN'
                    AND z.status = 'ACTIVE'
                    AND fishing_slot_inventory.reserved_count + #{guests} <= s.capacity
              )
            """)
    int reserve(@Param("spotId") Long spotId,
                @Param("date") LocalDate date,
                @Param("timeSlot") String timeSlot,
                @Param("guests") Integer guests);

    @Insert("""
            INSERT INTO booking
                (booking_no, member_id, spot_id, user_id, fishing_date, time_slot,
                 guests, amount, status, notes)
            VALUES
                (#{bookingNo}, #{memberId}, #{spotId}, #{userId}, #{date}, #{timeSlot},
                 #{guests}, #{amount}, 'CONFIRMED', #{notes})
            """)
    int insertBooking(@Param("bookingNo") String bookingNo,
                      @Param("memberId") Long memberId,
                      @Param("spotId") Long spotId,
                      @Param("userId") Long userId,
                      @Param("date") LocalDate date,
                      @Param("timeSlot") String timeSlot,
                      @Param("guests") Integer guests,
                      @Param("amount") BigDecimal amount,
                      @Param("notes") String notes);

    @Update("""
            UPDATE booking
            SET status = 'CANCELLED', cancelled_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND status = 'CONFIRMED'
            """)
    int cancel(@Param("id") Long id);

    @Update("""
            UPDATE fishing_slot_inventory
            SET reserved_count = reserved_count - #{guests}, updated_at = CURRENT_TIMESTAMP
            WHERE spot_id = #{spotId}
              AND fishing_date = #{date}
              AND time_slot = #{timeSlot}
              AND reserved_count >= #{guests}
            """)
    int release(@Param("spotId") Long spotId,
                @Param("date") LocalDate date,
                @Param("timeSlot") String timeSlot,
                @Param("guests") Integer guests);

    @Select("""
            <script>
            SELECT s.id AS spot_id, s.code AS spot_code, s.name AS spot_name,
                   z.id AS zone_id, z.name AS zone_name,
                   #{date} AS fishing_date, #{timeSlot} AS time_slot,
                   LEAST(COALESCE(i.capacity, s.capacity), s.capacity) AS capacity,
                   COALESCE(i.reserved_count, 0) AS reserved_count,
                   GREATEST(
                       LEAST(COALESCE(i.capacity, s.capacity), s.capacity)
                       - COALESCE(i.reserved_count, 0),
                       0
                   ) AS available_count,
                   CASE WHEN z.status != 'ACTIVE' THEN 'CLOSED'
                        WHEN s.status != 'OPEN' THEN s.status
                        WHEN i.status = 'CLOSED' THEN 'CLOSED'
                        WHEN COALESCE(i.reserved_count, 0)
                             >= LEAST(COALESCE(i.capacity, s.capacity), s.capacity) THEN 'FULL'
                        ELSE 'AVAILABLE' END AS status
            FROM fishing_spot s
            JOIN fishing_zone z ON z.id = s.zone_id
            LEFT JOIN fishing_slot_inventory i
              ON i.spot_id = s.id
             AND i.fishing_date = #{date}
             AND i.time_slot = #{timeSlot}
            <where>
              <if test="spotId != null">s.id = #{spotId}</if>
            </where>
            ORDER BY z.id, s.id
            </script>
            """)
    List<SpotAvailability> availability(@Param("date") LocalDate date,
                                        @Param("timeSlot") String timeSlot,
                                        @Param("spotId") Long spotId);
}
