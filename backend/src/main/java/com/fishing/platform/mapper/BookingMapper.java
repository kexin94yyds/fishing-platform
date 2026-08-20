package com.fishing.platform.mapper;

import com.fishing.platform.domain.DomainModels.Booking;
import com.fishing.platform.domain.DomainModels.BookingAudit;
import com.fishing.platform.domain.DomainModels.SlotInventory;
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
                   b.contact_name, b.contact_phone,
                   b.spot_id, s.name AS spot_name, z.name AS zone_name,
                   b.fishing_date, b.time_slot, b.guests, b.amount, b.payment_status,
                   b.status, b.notes, b.cancelled_at, b.created_at, b.updated_at
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

    @Select("""
            SELECT COUNT(*) > 0
            FROM booking
            WHERE fishing_date = #{date}
              AND time_slot = #{timeSlot}
              AND active_customer_key = #{customerKey}
              AND status = 'CONFIRMED'
            """)
    boolean hasActiveCustomerBooking(@Param("date") LocalDate date,
                                     @Param("timeSlot") String timeSlot,
                                     @Param("customerKey") String customerKey);

    @Select(BOOKING_SELECT + " WHERE b.id = #{id}")
    Booking findById(@Param("id") Long id);

    @Select("SELECT id FROM booking WHERE id = #{id} FOR UPDATE")
    Long lockById(@Param("id") Long id);

    @Select(BOOKING_SELECT + " WHERE b.booking_no = #{bookingNo}")
    Booking findByNo(@Param("bookingNo") String bookingNo);

    @Select("""
            SELECT COUNT(*) > 0
            FROM catch_record
            WHERE booking_id = #{bookingId}
              AND status != 'VOID'
            """)
    boolean hasActiveCatch(@Param("bookingId") Long bookingId);

    @Update("""
            UPDATE booking
            SET status = #{status}, active_customer_key = NULL, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND status = 'CONFIRMED'
            """)
    int settle(@Param("id") Long id, @Param("status") String status);

    @Insert("""
            INSERT IGNORE INTO fishing_slot_inventory
                (spot_id, fishing_date, time_slot, capacity, reserved_count, price, status)
            SELECT s.id, #{date}, #{timeSlot}, s.capacity, 0, s.default_price, 'AVAILABLE'
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
                (booking_no, member_id, contact_name, contact_phone, active_customer_key,
                 spot_id, user_id, fishing_date, time_slot, guests, amount, payment_status, status, notes)
            VALUES
                (#{bookingNo}, #{memberId}, #{contactName}, #{contactPhone}, #{customerKey},
                 #{spotId}, #{userId}, #{date}, #{timeSlot}, #{guests}, #{amount},
                 #{paymentStatus}, 'CONFIRMED', #{notes})
            """)
    int insertBooking(@Param("bookingNo") String bookingNo,
                      @Param("memberId") Long memberId,
                      @Param("contactName") String contactName,
                      @Param("contactPhone") String contactPhone,
                      @Param("customerKey") String customerKey,
                      @Param("spotId") Long spotId,
                      @Param("userId") Long userId,
                      @Param("date") LocalDate date,
                      @Param("timeSlot") String timeSlot,
                      @Param("guests") Integer guests,
                      @Param("amount") BigDecimal amount,
                      @Param("paymentStatus") String paymentStatus,
                      @Param("notes") String notes);

    @Update("""
            UPDATE booking
            SET status = 'CANCELLED',
                payment_status = CASE WHEN payment_status = 'PENDING' THEN 'CANCELLED' ELSE payment_status END,
                active_customer_key = NULL,
                cancelled_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
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
                   COALESCE(i.price, s.default_price) AS price,
                   CASE WHEN z.status != 'ACTIVE' THEN 'CLOSED'
                        WHEN s.status != 'OPEN' THEN s.status
                        WHEN i.status = 'CLOSED' THEN 'CLOSED'
                        WHEN COALESCE(i.reserved_count, 0)
                             >= LEAST(COALESCE(i.capacity, s.capacity), s.capacity) THEN 'FULL'
                        ELSE 'AVAILABLE' END AS status,
                   COALESCE(i.version, 0) AS version
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

    String INVENTORY_SELECT = """
            SELECT i.id, i.spot_id, s.code AS spot_code, s.name AS spot_name,
                   z.id AS zone_id, z.name AS zone_name,
                   i.fishing_date, i.time_slot, i.capacity, i.reserved_count,
                   i.price, i.status, i.version, i.created_at, i.updated_at
            FROM fishing_slot_inventory i
            JOIN fishing_spot s ON s.id = i.spot_id
            JOIN fishing_zone z ON z.id = s.zone_id
            """;

    @Select("""
            <script>
            """ + INVENTORY_SELECT + """
            WHERE i.fishing_date = #{date}
            <if test="spotId != null">AND i.spot_id = #{spotId}</if>
            ORDER BY z.id, s.id, i.time_slot
            </script>
            """)
    List<SlotInventory> findInventories(@Param("date") LocalDate date,
                                         @Param("spotId") Long spotId);

    @Select(INVENTORY_SELECT + """
            WHERE i.spot_id = #{spotId}
              AND i.fishing_date = #{date}
              AND i.time_slot = #{timeSlot}
            FOR UPDATE
            """)
    SlotInventory findInventoryForUpdate(@Param("spotId") Long spotId,
                                          @Param("date") LocalDate date,
                                          @Param("timeSlot") String timeSlot);

    @Insert("""
            INSERT INTO fishing_slot_inventory
                (spot_id, fishing_date, time_slot, capacity, reserved_count, price, status, version)
            VALUES
                (#{spotId}, #{date}, #{timeSlot}, #{capacity}, 0, #{price}, #{status}, 0)
            """)
    int insertInventory(@Param("spotId") Long spotId,
                        @Param("date") LocalDate date,
                        @Param("timeSlot") String timeSlot,
                        @Param("capacity") Integer capacity,
                        @Param("price") BigDecimal price,
                        @Param("status") String status);

    @Update("""
            UPDATE fishing_slot_inventory
            SET capacity = #{capacity}, price = #{price}, status = #{status},
                version = version + 1, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND version = #{expectedVersion}
              AND reserved_count <= #{capacity}
            """)
    int updateInventory(@Param("id") Long id,
                        @Param("capacity") Integer capacity,
                        @Param("price") BigDecimal price,
                        @Param("status") String status,
                        @Param("expectedVersion") Long expectedVersion);

    @Update("""
            UPDATE booking
            SET payment_status = 'PAID', updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND payment_status = 'PENDING'
              AND status != 'CANCELLED'
            """)
    int markPaid(@Param("id") Long id);

    @Insert("""
            INSERT INTO booking_audit_log
                (actor_user_id, actor_username, booking_id, booking_no, action,
                 before_status, after_status, before_payment_status, after_payment_status)
            VALUES
                (#{actorUserId}, #{actorUsername}, #{bookingId}, #{bookingNo}, #{action},
                 #{beforeStatus}, #{afterStatus}, #{beforePaymentStatus}, #{afterPaymentStatus})
            """)
    int insertAudit(@Param("actorUserId") Long actorUserId,
                    @Param("actorUsername") String actorUsername,
                    @Param("bookingId") Long bookingId,
                    @Param("bookingNo") String bookingNo,
                    @Param("action") String action,
                    @Param("beforeStatus") String beforeStatus,
                    @Param("afterStatus") String afterStatus,
                    @Param("beforePaymentStatus") String beforePaymentStatus,
                    @Param("afterPaymentStatus") String afterPaymentStatus);

    @Select("""
            SELECT id, actor_user_id, actor_username, booking_id, booking_no, action,
                   before_status, after_status, before_payment_status, after_payment_status, created_at
            FROM booking_audit_log
            WHERE booking_id = #{bookingId}
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<BookingAudit> findAudits(@Param("bookingId") Long bookingId, @Param("limit") int limit);
}
