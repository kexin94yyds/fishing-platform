package com.fishing.platform.mapper;

import com.fishing.platform.domain.DomainModels.AccountAudit;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface AccountAuditMapper {

    @Insert("""
            INSERT INTO account_audit_log
                (actor_user_id, actor_username, target_user_id, target_username, action,
                 before_role, after_role, before_enabled, after_enabled)
            VALUES
                (#{actorUserId}, #{actorUsername}, #{targetUserId}, #{targetUsername}, #{action},
                 #{beforeRole}, #{afterRole}, #{beforeEnabled}, #{afterEnabled})
            """)
    int insert(@Param("actorUserId") Long actorUserId,
               @Param("actorUsername") String actorUsername,
               @Param("targetUserId") Long targetUserId,
               @Param("targetUsername") String targetUsername,
               @Param("action") String action,
               @Param("beforeRole") String beforeRole,
               @Param("afterRole") String afterRole,
               @Param("beforeEnabled") Boolean beforeEnabled,
               @Param("afterEnabled") Boolean afterEnabled);

    @Select("""
            <script>
            SELECT id, actor_user_id, actor_username, target_user_id, target_username, action,
                   before_role, after_role, before_enabled, after_enabled, created_at
            FROM account_audit_log
            <if test="targetId != null">WHERE target_user_id = #{targetId}</if>
            ORDER BY id DESC
            LIMIT #{limit}
            </script>
            """)
    List<AccountAudit> findRecent(@Param("targetId") Long targetId, @Param("limit") int limit);
}
