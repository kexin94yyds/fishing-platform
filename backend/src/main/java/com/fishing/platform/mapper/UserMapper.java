package com.fishing.platform.mapper;

import com.fishing.platform.domain.DomainModels.UserAccount;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface UserMapper {

    @Select("SELECT COUNT(*) FROM app_user WHERE role = 'ADMIN' AND enabled = TRUE")
    int countEnabledAdmins();

    @Select("""
            SELECT id, username, password_hash, display_name, role, enabled
            FROM app_user
            WHERE username = #{username}
            """)
    UserAccount findByUsername(@Param("username") String username);

    @Insert("""
            INSERT INTO app_user
                (username, password_hash, display_name, role, enabled)
            VALUES
                (#{username}, #{passwordHash}, #{displayName}, 'OPERATOR', TRUE)
            """)
    int insertOperator(@Param("username") String username,
                       @Param("passwordHash") String passwordHash,
                       @Param("displayName") String displayName);

    @Insert("""
            INSERT INTO app_user
                (username, password_hash, display_name, role, enabled)
            VALUES
                (#{username}, #{passwordHash}, #{displayName}, 'ADMIN', TRUE)
            """)
    int insertAdmin(@Param("username") String username,
                    @Param("passwordHash") String passwordHash,
                    @Param("displayName") String displayName);

    @Update("""
            UPDATE app_user
            SET password_hash = #{passwordHash}, display_name = #{displayName},
                role = 'ADMIN', enabled = TRUE, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND enabled = FALSE AND password_hash = #{expectedPasswordHash}
            """)
    int activateRetiredAdmin(@Param("id") Long id,
                             @Param("passwordHash") String passwordHash,
                             @Param("displayName") String displayName,
                             @Param("expectedPasswordHash") String expectedPasswordHash);
}
