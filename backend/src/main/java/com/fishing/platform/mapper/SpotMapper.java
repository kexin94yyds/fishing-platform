package com.fishing.platform.mapper;

import com.fishing.platform.domain.DomainModels.Spot;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface SpotMapper {

    String BASE_SELECT = """
            SELECT s.id, s.zone_id, z.name AS zone_name, s.code, s.name,
                   s.map_x, s.map_y, s.capacity, s.status, s.note,
                   s.created_at, s.updated_at
            FROM fishing_spot s
            JOIN fishing_zone z ON z.id = s.zone_id
            """;

    @Select(BASE_SELECT + " ORDER BY s.id")
    List<Spot> findAll();

    @Select(BASE_SELECT + " WHERE s.id = #{id}")
    Spot findById(@Param("id") Long id);

    @Select("SELECT id FROM fishing_spot WHERE id = #{id} FOR UPDATE")
    Long lockById(@Param("id") Long id);

    @Select(BASE_SELECT + " WHERE s.code = #{code}")
    Spot findByCode(@Param("code") String code);

    @Select(BASE_SELECT + " WHERE s.map_x IS NOT NULL AND s.map_y IS NOT NULL ORDER BY s.id")
    List<Spot> findMapSpots();

    @Insert("""
            INSERT INTO fishing_spot
                (zone_id, code, name, map_x, map_y, capacity, status, note)
            VALUES
                (#{zoneId}, #{code}, #{name}, #{mapX}, #{mapY}, #{capacity}, #{status}, #{note})
            """)
    int insert(@Param("zoneId") Long zoneId,
               @Param("code") String code,
               @Param("name") String name,
               @Param("mapX") BigDecimal mapX,
               @Param("mapY") BigDecimal mapY,
               @Param("capacity") Integer capacity,
               @Param("status") String status,
               @Param("note") String note);

    @Update("""
            UPDATE fishing_spot
            SET zone_id = #{zoneId}, code = #{code}, name = #{name},
                map_x = #{mapX}, map_y = #{mapY}, capacity = #{capacity},
                status = #{status}, note = #{note}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND NOT EXISTS (
                  SELECT 1
                  FROM fishing_slot_inventory i
                  WHERE i.spot_id = #{id}
                    AND i.fishing_date >= #{fromDate}
                    AND i.reserved_count > #{capacity}
              )
            """)
    int updateIfCapacityAllows(@Param("id") Long id,
                               @Param("zoneId") Long zoneId,
                               @Param("code") String code,
                               @Param("name") String name,
                               @Param("mapX") BigDecimal mapX,
                               @Param("mapY") BigDecimal mapY,
                               @Param("capacity") Integer capacity,
                               @Param("status") String status,
                               @Param("note") String note,
                               @Param("fromDate") LocalDate fromDate);

    @Update("""
            UPDATE fishing_slot_inventory
            SET capacity = #{capacity}, updated_at = CURRENT_TIMESTAMP
            WHERE spot_id = #{spotId}
              AND fishing_date >= #{fromDate}
            """)
    int updateFutureInventoryCapacity(@Param("spotId") Long spotId,
                                      @Param("capacity") Integer capacity,
                                      @Param("fromDate") LocalDate fromDate);

    @Select("""
            SELECT COALESCE(MAX(i.reserved_count), 0)
            FROM fishing_slot_inventory i
            WHERE i.spot_id = #{spotId}
              AND i.fishing_date >= #{fromDate}
            """)
    int maximumActiveReservationCount(@Param("spotId") Long spotId,
                                      @Param("fromDate") LocalDate fromDate);
}
