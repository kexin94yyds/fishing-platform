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
                   c.member_id, m.name AS member_name, c.fishing_date,
                   c.species, c.weight, c.quantity, c.notes, c.status,
                   c.created_at, c.updated_at
            FROM catch_record c
            LEFT JOIN booking b ON b.id = c.booking_id
            JOIN fishing_spot s ON s.id = c.spot_id
            LEFT JOIN member m ON m.id = c.member_id
            """;

    @Select(BASE_SELECT + " ORDER BY c.fishing_date DESC, c.id DESC")
    List<CatchRecord> findAll();

    @Select(BASE_SELECT + " WHERE c.id = #{id}")
    CatchRecord findById(@Param("id") Long id);

    @Select(BASE_SELECT + " WHERE c.catch_no = #{catchNo}")
    CatchRecord findByNo(@Param("catchNo") String catchNo);

    @Insert("""
            INSERT INTO catch_record
                (catch_no, booking_id, spot_id, member_id, fishing_date, species, weight, quantity, notes, status)
            VALUES
                (#{catchNo}, #{bookingId}, #{spotId}, #{memberId}, #{fishingDate}, #{species}, #{weight},
                 #{quantity}, #{notes}, #{status})
            """)
    int insert(@Param("catchNo") String catchNo,
               @Param("bookingId") Long bookingId,
               @Param("spotId") Long spotId,
               @Param("memberId") Long memberId,
               @Param("fishingDate") LocalDate fishingDate,
               @Param("species") String species,
               @Param("weight") BigDecimal weight,
               @Param("quantity") Integer quantity,
               @Param("notes") String notes,
               @Param("status") String status);

    @Update("""
            UPDATE catch_record
            SET booking_id = #{bookingId}, spot_id = #{spotId}, member_id = #{memberId}, fishing_date = #{fishingDate},
                species = #{species}, weight = #{weight}, quantity = #{quantity},
                notes = #{notes}, status = #{status}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int update(@Param("id") Long id,
               @Param("bookingId") Long bookingId,
               @Param("spotId") Long spotId,
               @Param("memberId") Long memberId,
               @Param("fishingDate") LocalDate fishingDate,
               @Param("species") String species,
               @Param("weight") BigDecimal weight,
               @Param("quantity") Integer quantity,
               @Param("notes") String notes,
               @Param("status") String status);
}
