package com.fishing.platform.mapper;

import com.fishing.platform.domain.DomainModels.UserAccount;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface UserMapper {

    @Select("SELECT COUNT(*) FROM app_user WHERE role = 'ADMIN' AND enabled = TRUE")
    int countEnabledAdmins();

    @Select("SELECT id FROM account_admin_guard WHERE id = 1 FOR UPDATE")
    Long lockAdminGuard();

    String BASE_SELECT = """
            SELECT id, username, password_hash, display_name, role, enabled,
                   version, session_version, created_at, updated_at
            FROM app_user
            """;

    @Select(BASE_SELECT + " ORDER BY id")
    List<UserAccount> findAll();

    @Select(BASE_SELECT + " WHERE username = #{username}")
    UserAccount findByUsername(@Param("username") String username);

    @Select(BASE_SELECT + " WHERE id = #{id}")
    UserAccount findById(@Param("id") Long id);

    @Select(BASE_SELECT + " WHERE id = #{id} FOR UPDATE")
    UserAccount findByIdForUpdate(@Param("id") Long id);

    @Insert("""
            INSERT INTO app_user
                (username, password_hash, display_name, role, enabled)
            VALUES
                (#{username}, #{passwordHash}, #{displayName}, 'USER', TRUE)
            """)
    int insertUser(@Param("username") String username,
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

    @Insert("""
            INSERT INTO app_user
                (username, password_hash, display_name, role, enabled)
            VALUES
                (#{username}, #{passwordHash}, #{displayName}, #{role}, TRUE)
            """)
    int insertAccount(@Param("username") String username,
                      @Param("passwordHash") String passwordHash,
                      @Param("displayName") String displayName,
                      @Param("role") String role);

    @Update("""
            UPDATE app_user
            SET display_name = #{displayName}, role = #{role}, enabled = #{enabled},
                version = version + 1,
                session_version = session_version + CASE WHEN enabled <> #{enabled} THEN 1 ELSE 0 END,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND version = #{expectedVersion}
            """)
    int updateAccount(@Param("id") Long id,
                      @Param("displayName") String displayName,
                      @Param("role") String role,
                      @Param("enabled") boolean enabled,
                      @Param("expectedVersion") Long expectedVersion);

    @Update("""
            UPDATE app_user
            SET password_hash = #{passwordHash}, version = version + 1,
                session_version = session_version + 1, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND version = #{expectedVersion}
            """)
    int updatePassword(@Param("id") Long id,
                       @Param("passwordHash") String passwordHash,
                       @Param("expectedVersion") Long expectedVersion);

    @Update("""
            UPDATE app_user
            SET password_hash = #{passwordHash}, display_name = #{displayName},
                role = 'ADMIN', enabled = TRUE, version = version + 1,
                session_version = session_version + 1, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND enabled = FALSE AND password_hash = #{expectedPasswordHash}
            """)
    int activateRetiredAdmin(@Param("id") Long id,
                             @Param("passwordHash") String passwordHash,
                             @Param("displayName") String displayName,
                             @Param("expectedPasswordHash") String expectedPasswordHash);
}
