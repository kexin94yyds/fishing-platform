package com.fishing.platform.mapper;

import com.fishing.platform.domain.DomainModels.UserAccount;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserMapper {

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
}
