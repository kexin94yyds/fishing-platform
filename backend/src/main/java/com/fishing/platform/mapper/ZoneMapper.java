package com.fishing.platform.mapper;

import com.fishing.platform.domain.DomainModels.Zone;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface ZoneMapper {

    @Select("""
            SELECT id, code, name, description, status, created_at, updated_at
            FROM fishing_zone
            ORDER BY id
            """)
    List<Zone> findAll();

    @Select("""
            SELECT id, code, name, description, status, created_at, updated_at
            FROM fishing_zone WHERE id = #{id}
            """)
    Zone findById(@Param("id") Long id);

    @Select("""
            SELECT id, code, name, description, status, created_at, updated_at
            FROM fishing_zone WHERE code = #{code}
            """)
    Zone findByCode(@Param("code") String code);

    @Insert("""
            INSERT INTO fishing_zone (code, name, description, status)
            VALUES (#{code}, #{name}, #{description}, #{status})
            """)
    int insert(@Param("code") String code,
               @Param("name") String name,
               @Param("description") String description,
               @Param("status") String status);

    @Update("""
            UPDATE fishing_zone
            SET code = #{code}, name = #{name}, description = #{description},
                status = #{status}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int update(@Param("id") Long id,
               @Param("code") String code,
               @Param("name") String name,
               @Param("description") String description,
               @Param("status") String status);
}
