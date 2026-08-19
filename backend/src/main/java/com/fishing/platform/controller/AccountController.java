package com.fishing.platform.controller;

import com.fishing.platform.common.ApiResponse;
import com.fishing.platform.domain.DomainModels.AccountAudit;
import com.fishing.platform.domain.DomainModels.AccountView;
import com.fishing.platform.dto.ApiDtos.AccountCreateRequest;
import com.fishing.platform.dto.ApiDtos.AccountPasswordResetRequest;
import com.fishing.platform.dto.ApiDtos.AccountUpdateRequest;
import com.fishing.platform.dto.ApiDtos.ChangePasswordRequest;
import com.fishing.platform.service.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api")
public class AccountController {
    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @GetMapping("/admin/accounts")
    public ApiResponse<List<AccountView>> list() {
        return ApiResponse.ok(service.findAll());
    }

    @PostMapping("/admin/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AccountView> create(@Valid @RequestBody AccountCreateRequest request) {
        return ApiResponse.ok("账号已创建", service.create(request));
    }

    @PutMapping("/admin/accounts/{id}")
    public ApiResponse<AccountView> update(@PathVariable @Positive Long id,
                                           @Valid @RequestBody AccountUpdateRequest request) {
        return ApiResponse.ok("账号已更新", service.update(id, request));
    }

    @PostMapping("/admin/accounts/{id}/reset-password")
    public ApiResponse<AccountView> resetPassword(@PathVariable @Positive Long id,
                                                  @Valid @RequestBody AccountPasswordResetRequest request) {
        return ApiResponse.ok("账号密码已重置", service.resetPassword(id, request));
    }

    @GetMapping("/admin/account-audits")
    public ApiResponse<List<AccountAudit>> audits(
            @RequestParam(required = false) @Positive Long targetId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int limit) {
        return ApiResponse.ok(service.findAudits(targetId, limit));
    }

    @PostMapping("/auth/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        service.changePassword(request);
        return ApiResponse.ok("密码已修改，请重新登录", null);
    }
}
