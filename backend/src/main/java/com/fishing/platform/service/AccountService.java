package com.fishing.platform.service;

import com.fishing.platform.common.BusinessException;
import com.fishing.platform.common.NotFoundException;
import com.fishing.platform.domain.DomainModels.AccountAudit;
import com.fishing.platform.domain.DomainModels.AccountView;
import com.fishing.platform.domain.DomainModels.UserAccount;
import com.fishing.platform.dto.ApiDtos.AccountCreateRequest;
import com.fishing.platform.dto.ApiDtos.AccountPasswordResetRequest;
import com.fishing.platform.dto.ApiDtos.AccountUpdateRequest;
import com.fishing.platform.dto.ApiDtos.ChangePasswordRequest;
import com.fishing.platform.mapper.AccountAuditMapper;
import com.fishing.platform.mapper.UserMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AccountService {
    private final UserMapper userMapper;
    private final AccountAuditMapper auditMapper;
    private final CurrentUserService currentUserService;
    private final PasswordEncoder passwordEncoder;

    public AccountService(UserMapper userMapper,
                          AccountAuditMapper auditMapper,
                          CurrentUserService currentUserService,
                          PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.auditMapper = auditMapper;
        this.currentUserService = currentUserService;
        this.passwordEncoder = passwordEncoder;
    }

    public List<AccountView> findAll() {
        return userMapper.findAll().stream().map(AccountView::from).toList();
    }

    public List<AccountAudit> findAudits(Long targetId, int limit) {
        return auditMapper.findRecent(targetId, Math.min(Math.max(limit, 1), 200));
    }

    @Transactional
    public AccountView create(AccountCreateRequest request) {
        UserAccount actor = lockCurrentAdmin();
        String username = RegistrationService.normalizeUsername(request.username());
        if (userMapper.findByUsername(username) != null) {
            throw new BusinessException("用户名已存在");
        }
        try {
            userMapper.insertAccount(username, passwordEncoder.encode(request.password()),
                    request.displayName().trim(), request.role());
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException("用户名已存在");
        }
        UserAccount created = userMapper.findByUsername(username);
        audit(actor, created, "CREATE", null, created.role(), null, created.enabled());
        return AccountView.from(created);
    }

    @Transactional
    public AccountView update(Long id, AccountUpdateRequest request) {
        UserAccount actor = lockCurrentAdmin();
        UserAccount target = lockTarget(id);
        if (actor.id().equals(target.id())
                && (!target.role().equals(request.role()) || target.enabled() != request.enabled())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "不能停用自己或修改自己的角色");
        }
        if (removesEnabledAdmin(target, request.role(), request.enabled())
                && userMapper.countEnabledAdmins() <= 1) {
            throw new BusinessException("必须至少保留一个启用的管理员账号");
        }
        if (crossesUserBoundary(target.role(), request.role())) {
            throw new BusinessException("钓友用户与工作人员不能互相转换角色");
        }
        if (!target.version().equals(request.expectedVersion())) {
            throw staleAccount();
        }
        if (userMapper.updateAccount(id, request.displayName().trim(), request.role(), request.enabled(),
                request.expectedVersion()) == 0) {
            throw staleAccount();
        }
        UserAccount updated = userMapper.findById(id);
        audit(actor, updated, "UPDATE", target.role(), updated.role(), target.enabled(), updated.enabled());
        return AccountView.from(updated);
    }

    @Transactional
    public AccountView resetPassword(Long id, AccountPasswordResetRequest request) {
        UserAccount actor = lockCurrentAdmin();
        UserAccount target = lockTarget(id);
        if (actor.id().equals(target.id())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "请通过修改本人密码功能更新自己的密码");
        }
        if (!target.version().equals(request.expectedVersion())) {
            throw staleAccount();
        }
        if (userMapper.updatePassword(id, passwordEncoder.encode(request.newPassword()),
                request.expectedVersion()) == 0) {
            throw staleAccount();
        }
        UserAccount updated = userMapper.findById(id);
        audit(actor, updated, "RESET_PASSWORD", target.role(), updated.role(),
                target.enabled(), updated.enabled());
        return AccountView.from(updated);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        UserAccount snapshot = currentUserService.current();
        userMapper.lockAdminGuard();
        UserAccount current = userMapper.findByIdForUpdate(snapshot.id());
        if (current == null || !current.enabled()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "登录状态已失效");
        }
        if (!passwordEncoder.matches(request.currentPassword(), current.passwordHash())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "当前密码不正确");
        }
        if (passwordEncoder.matches(request.newPassword(), current.passwordHash())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "新密码不能与当前密码相同");
        }
        if (userMapper.updatePassword(current.id(), passwordEncoder.encode(request.newPassword()),
                current.version()) == 0) {
            throw staleAccount();
        }
        UserAccount updated = userMapper.findById(current.id());
        audit(current, updated, "CHANGE_PASSWORD", current.role(), updated.role(),
                current.enabled(), updated.enabled());
    }

    private UserAccount lockCurrentAdmin() {
        UserAccount snapshot = currentUserService.current();
        userMapper.lockAdminGuard();
        UserAccount actor = userMapper.findByIdForUpdate(snapshot.id());
        if (actor == null || !actor.enabled() || !"ADMIN".equals(actor.role())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "管理员权限已发生变化");
        }
        return actor;
    }

    private UserAccount lockTarget(Long id) {
        UserAccount target = userMapper.findByIdForUpdate(id);
        if (target == null) {
            throw new NotFoundException("账号不存在");
        }
        return target;
    }

    private boolean removesEnabledAdmin(UserAccount target, String nextRole, boolean nextEnabled) {
        return target.enabled() && "ADMIN".equals(target.role())
                && (!nextEnabled || !"ADMIN".equals(nextRole));
    }

    private boolean crossesUserBoundary(String currentRole, String nextRole) {
        return "USER".equals(currentRole) != "USER".equals(nextRole);
    }

    private BusinessException staleAccount() {
        return new BusinessException("账号已被其他操作修改，请刷新后重试");
    }

    private void audit(UserAccount actor,
                       UserAccount target,
                       String action,
                       String beforeRole,
                       String afterRole,
                       Boolean beforeEnabled,
                       Boolean afterEnabled) {
        auditMapper.insert(actor.id(), actor.username(), target.id(), target.username(), action,
                beforeRole, afterRole, beforeEnabled, afterEnabled);
    }
}
