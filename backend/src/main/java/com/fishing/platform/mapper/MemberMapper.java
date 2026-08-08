package com.fishing.platform.mapper;

import com.fishing.platform.domain.DomainModels.Member;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface MemberMapper {

    String BASE_SELECT = """
            SELECT id, member_no, name, phone, level, points, status, created_at, updated_at
            FROM member
            """;

    @Select(BASE_SELECT + " ORDER BY id DESC")
    List<Member> findAll();

    @Select(BASE_SELECT + " WHERE id = #{id}")
    Member findById(@Param("id") Long id);

    @Select(BASE_SELECT + " WHERE id = #{id} FOR UPDATE")
    Member findByIdForUpdate(@Param("id") Long id);

    @Select(BASE_SELECT + " WHERE member_no = #{memberNo}")
    Member findByNo(@Param("memberNo") String memberNo);

    @Insert("""
            INSERT INTO member (member_no, name, phone, level, points, status)
            VALUES (#{memberNo}, #{name}, #{phone}, #{level}, #{points}, #{status})
            """)
    int insert(@Param("memberNo") String memberNo,
               @Param("name") String name,
               @Param("phone") String phone,
               @Param("level") String level,
               @Param("points") Integer points,
               @Param("status") String status);

    @Update("""
            UPDATE member
            SET member_no = #{memberNo}, name = #{name}, phone = #{phone},
                level = #{level}, points = #{points}, status = #{status},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int update(@Param("id") Long id,
               @Param("memberNo") String memberNo,
               @Param("name") String name,
               @Param("phone") String phone,
               @Param("level") String level,
               @Param("points") Integer points,
               @Param("status") String status);
}
