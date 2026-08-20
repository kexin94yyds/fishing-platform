package com.fishing.platform.mapper;

import com.fishing.platform.domain.DomainModels.Member;
import com.fishing.platform.domain.DomainModels.MemberAudit;
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

    @Insert("""
            INSERT INTO member_audit_log
                (actor_user_id, actor_username, target_member_id, target_member_no, action,
                 before_level, after_level, before_points, after_points, before_status, after_status)
            VALUES
                (#{actorUserId}, #{actorUsername}, #{targetMemberId}, #{targetMemberNo}, #{action},
                 #{beforeLevel}, #{afterLevel}, #{beforePoints}, #{afterPoints}, #{beforeStatus}, #{afterStatus})
            """)
    int insertAudit(@Param("actorUserId") Long actorUserId,
                    @Param("actorUsername") String actorUsername,
                    @Param("targetMemberId") Long targetMemberId,
                    @Param("targetMemberNo") String targetMemberNo,
                    @Param("action") String action,
                    @Param("beforeLevel") String beforeLevel,
                    @Param("afterLevel") String afterLevel,
                    @Param("beforePoints") Integer beforePoints,
                    @Param("afterPoints") Integer afterPoints,
                    @Param("beforeStatus") String beforeStatus,
                    @Param("afterStatus") String afterStatus);

    @Select("""
            SELECT id, actor_user_id, actor_username, target_member_id, target_member_no,
                   action, before_level, after_level, before_points, after_points,
                   before_status, after_status, created_at
            FROM member_audit_log
            WHERE target_member_id = #{memberId}
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<MemberAudit> findAudits(@Param("memberId") Long memberId, @Param("limit") int limit);
}
