package com.fishing.platform.mapper;

import com.fishing.platform.domain.DomainModels.Spot;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
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
            """)
    int update(@Param("id") Long id,
               @Param("zoneId") Long zoneId,
               @Param("code") String code,
               @Param("name") String name,
               @Param("mapX") BigDecimal mapX,
               @Param("mapY") BigDecimal mapY,
               @Param("capacity") Integer capacity,
               @Param("status") String status,
               @Param("note") String note);
}
