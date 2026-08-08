package com.fishing.platform.config;

import com.fishing.platform.mapper.UserMapper;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

@Component
@Profile("mysql")
@DependsOnDatabaseInitialization
public class MysqlAdminBootstrap implements InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(MysqlAdminBootstrap.class);
    private static final String RETIRED_PASSWORD_HASH =
            "$2y$12$pD3HE0BRYXxqqQYc5O8/8eIBKAp074oS7m238p9ZEmICyYK0PLcnG";
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[a-z][a-z0-9_]{3,31}");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)\\S{12,64}$");

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;
    private final String username;
    private final String displayName;
    private final String password;

    public MysqlAdminBootstrap(UserMapper userMapper,
                               PasswordEncoder passwordEncoder,
                               TransactionTemplate transactionTemplate,
                               @Value("${fishing.bootstrap-admin.username:}") String username,
                               @Value("${fishing.bootstrap-admin.display-name:系统管理员}") String displayName,
                               @Value("${fishing.bootstrap-admin.password:}") String password) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.transactionTemplate = transactionTemplate;
        this.username = username == null ? "" : username.trim();
        this.displayName = displayName == null ? "" : displayName.trim();
        this.password = password == null ? "" : password;
    }

    @Override
    public void afterPropertiesSet() {
        transactionTemplate.executeWithoutResult(status -> initializeAdmin());
    }

    private void initializeAdmin() {
        if (userMapper.countEnabledAdmins() > 0) {
            return;
        }
        validateConfiguration();
        String passwordHash = passwordEncoder.encode(password);
        var existing = userMapper.findByUsername(username);
        if (existing == null) {
            if (userMapper.insertAdmin(username, passwordHash, displayName) != 1) {
                throw new IllegalStateException("MySQL 管理员初始化失败");
            }
            log.info("MySQL 初始管理员已创建：{}", username);
            return;
        }
        if (userMapper.activateRetiredAdmin(existing.id(), passwordHash, displayName,
                RETIRED_PASSWORD_HASH) != 1) {
            throw new IllegalStateException("FISHING_BOOTSTRAP_ADMIN_USERNAME 已被非演示账号占用，请更换用户名");
        }
        log.info("MySQL 演示管理员已使用安全凭据恢复：{}", username);
    }

    private void validateConfiguration() {
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalStateException("必须设置合法的 FISHING_BOOTSTRAP_ADMIN_USERNAME（4-32 位小写字母、数字或下划线，并以字母开头）");
        }
        if (displayName.length() < 2 || displayName.length() > 100) {
            throw new IllegalStateException("FISHING_BOOTSTRAP_ADMIN_DISPLAY_NAME 长度必须为 2-100 位");
        }
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new IllegalStateException("必须设置 FISHING_BOOTSTRAP_ADMIN_PASSWORD（12-64 位，至少包含一个英文字母和一个数字，且不能含空格）");
        }
    }
}
