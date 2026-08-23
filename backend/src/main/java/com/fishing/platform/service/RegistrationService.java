package com.fishing.platform.service;

import com.fishing.platform.common.BusinessException;
import com.fishing.platform.domain.DomainModels.UserAccount;
import com.fishing.platform.dto.ApiDtos.RegisterRequest;
import com.fishing.platform.mapper.UserMapper;
import com.fishing.platform.mapper.AccountAuditMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class RegistrationService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AccountAuditMapper auditMapper;
    private final boolean enabled;

    public RegistrationService(UserMapper userMapper,
                               PasswordEncoder passwordEncoder,
                               AccountAuditMapper auditMapper,
                               @Value("${fishing.registration.enabled:false}") boolean enabled) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditMapper = auditMapper;
        this.enabled = enabled;
    }

    @Transactional
    public UserAccount register(RegisterRequest request) {
        if (!enabled) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "当前环境已关闭公开注册");
        }
        String username = normalizeUsername(request.username());
        if (userMapper.findByUsername(username) != null) {
            throw new BusinessException("用户名已存在");
        }

        try {
            userMapper.insertUser(username, passwordEncoder.encode(request.password()),
                    request.displayName().trim());
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException("用户名已存在");
        }
        UserAccount created = userMapper.findByUsername(username);
        auditMapper.insert(null, "PUBLIC_REGISTER", created.id(), created.username(), "PUBLIC_REGISTER",
                null, created.role(), null, created.enabled());
        return created;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public static String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
