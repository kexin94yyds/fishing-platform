package com.fishing.platform.mapper;

import com.fishing.platform.domain.DomainModels.TrafficDailyEntry;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

public interface TrafficDailyMapper {

    String BASE_SELECT = """
            SELECT td.id, td.stat_date, td.visits, td.unique_visitors, td.notes, td.version,
                   td.created_by, creator.display_name AS created_by_name,
                   td.updated_by, updater.display_name AS updated_by_name,
                   td.created_at, td.updated_at
            FROM traffic_daily td
            LEFT JOIN app_user creator ON creator.id = td.created_by
            LEFT JOIN app_user updater ON updater.id = td.updated_by
            """;

    @Select(BASE_SELECT + """
            WHERE td.stat_date >= #{startDate}
            ORDER BY td.stat_date DESC
            """)
    List<TrafficDailyEntry> findRecent(@Param("startDate") LocalDate startDate);

    @Select(BASE_SELECT + " WHERE td.stat_date = #{statDate}")
    TrafficDailyEntry findByStatDate(@Param("statDate") LocalDate statDate);

    @Insert("""
            INSERT INTO traffic_daily
                (stat_date, visits, unique_visitors, notes, version, created_by, updated_by)
            VALUES
                (#{statDate}, #{visits}, #{uniqueVisitors}, #{notes}, 0, #{actorId}, #{actorId})
            """)
    int insert(@Param("statDate") LocalDate statDate,
               @Param("visits") Integer visits,
               @Param("uniqueVisitors") Integer uniqueVisitors,
               @Param("notes") String notes,
               @Param("actorId") Long actorId);

    @Update("""
            UPDATE traffic_daily
            SET visits = #{visits},
                unique_visitors = #{uniqueVisitors},
                notes = #{notes},
                updated_by = #{actorId},
                version = version + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE stat_date = #{statDate}
              AND version = #{expectedVersion}
            """)
    int update(@Param("statDate") LocalDate statDate,
               @Param("visits") Integer visits,
               @Param("uniqueVisitors") Integer uniqueVisitors,
               @Param("notes") String notes,
               @Param("actorId") Long actorId,
               @Param("expectedVersion") Long expectedVersion);
}
