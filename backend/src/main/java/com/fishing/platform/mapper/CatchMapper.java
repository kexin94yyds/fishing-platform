package com.fishing.platform.mapper;

import com.fishing.platform.domain.DomainModels.CatchRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface CatchMapper {

    String BASE_SELECT = """
            SELECT c.id, c.catch_no, c.booking_id, b.booking_no, c.spot_id, s.name AS spot_name,
                   z.id AS zone_id, z.name AS zone_name,
                   c.member_id, m.name AS member_name, c.fishing_date, c.time_slot,
                   c.species, c.weight, c.quantity, c.notes, c.status,
                   c.version, c.created_at, c.updated_at
            FROM catch_record c
            LEFT JOIN booking b ON b.id = c.booking_id
            JOIN fishing_spot s ON s.id = c.spot_id
            JOIN fishing_zone z ON z.id = s.zone_id
            LEFT JOIN member m ON m.id = c.member_id
            """;

    @Select("""
            <script>
            """ + BASE_SELECT + """
            <where>
              <if test="date != null">AND c.fishing_date = #{date}</if>
              <if test="zoneId != null">AND z.id = #{zoneId}</if>
              <if test="species != null and species != ''">AND c.species = #{species}</if>
              <if test="memberId != null">AND c.member_id = #{memberId}</if>
            </where>
            ORDER BY c.fishing_date DESC, c.id DESC
            </script>
            """)
    List<CatchRecord> findAll(@Param("date") LocalDate date,
                              @Param("zoneId") Long zoneId,
                              @Param("species") String species,
                              @Param("memberId") Long memberId);

    @Select(BASE_SELECT + " WHERE c.id = #{id}")
    CatchRecord findById(@Param("id") Long id);

    @Select("SELECT id FROM catch_record WHERE id = #{id} FOR UPDATE")
    Long lockById(@Param("id") Long id);

    @Select(BASE_SELECT + " WHERE c.catch_no = #{catchNo}")
    CatchRecord findByNo(@Param("catchNo") String catchNo);

    @Insert("""
            INSERT INTO catch_record
                (catch_no, booking_id, spot_id, member_id, fishing_date, time_slot,
                 species, weight, quantity, notes, status)
            VALUES
                (#{catchNo}, #{bookingId}, #{spotId}, #{memberId}, #{fishingDate}, #{timeSlot}, #{species}, #{weight},
                 #{quantity}, #{notes}, #{status})
            """)
    int insert(@Param("catchNo") String catchNo,
               @Param("bookingId") Long bookingId,
               @Param("spotId") Long spotId,
               @Param("memberId") Long memberId,
               @Param("fishingDate") LocalDate fishingDate,
               @Param("timeSlot") String timeSlot,
               @Param("species") String species,
               @Param("weight") BigDecimal weight,
               @Param("quantity") Integer quantity,
               @Param("notes") String notes,
               @Param("status") String status);

    @Update("""
            UPDATE catch_record
            SET booking_id = #{bookingId}, spot_id = #{spotId}, member_id = #{memberId}, fishing_date = #{fishingDate},
                time_slot = #{timeSlot}, species = #{species}, weight = #{weight}, quantity = #{quantity},
                notes = #{notes}, status = #{status}, version = version + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND status = #{expectedStatus} AND version = #{expectedVersion}
            """)
    int update(@Param("id") Long id,
               @Param("bookingId") Long bookingId,
               @Param("spotId") Long spotId,
               @Param("memberId") Long memberId,
               @Param("fishingDate") LocalDate fishingDate,
               @Param("timeSlot") String timeSlot,
               @Param("species") String species,
               @Param("weight") BigDecimal weight,
               @Param("quantity") Integer quantity,
               @Param("notes") String notes,
               @Param("status") String status,
               @Param("expectedStatus") String expectedStatus,
               @Param("expectedVersion") Long expectedVersion);
}
