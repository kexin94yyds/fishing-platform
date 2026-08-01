package com.fishing.platform.controller;

import com.fishing.platform.common.ApiResponse;
import com.fishing.platform.common.BusinessException;
import com.fishing.platform.dto.ApiDtos.CsrfView;
import com.fishing.platform.dto.ApiDtos.LoginRequest;
import com.fishing.platform.dto.ApiDtos.MeView;
import com.fishing.platform.dto.ApiDtos.RegisterRequest;
import com.fishing.platform.service.CurrentUserService;
import com.fishing.platform.service.RegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextHolderStrategy contextHolderStrategy;
    private final CurrentUserService currentUserService;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final RegistrationService registrationService;

    public AuthController(AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository,
                          SecurityContextHolderStrategy contextHolderStrategy,
                          CurrentUserService currentUserService,
                          SessionAuthenticationStrategy sessionAuthenticationStrategy,
                          RegistrationService registrationService) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.contextHolderStrategy = contextHolderStrategy;
        this.currentUserService = currentUserService;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.registrationService = registrationService;
    }

    @GetMapping("/csrf")
    public ApiResponse<CsrfView> csrf(CsrfToken token) {
        return ApiResponse.ok(new CsrfView(token.getHeaderName(), token.getParameterName(), token.getToken()));
    }

    @PostMapping("/login")
    public ApiResponse<MeView> login(@Valid @RequestBody LoginRequest request,
                                     HttpServletRequest servletRequest,
                                     HttpServletResponse servletResponse) {
        try {
            authenticateAndPersist(RegistrationService.normalizeUsername(request.username()), request.password(),
                    servletRequest, servletResponse);
            return ApiResponse.ok("登录成功", toMe());
        } catch (AuthenticationException exception) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MeView> register(@Valid @RequestBody RegisterRequest request,
                                        HttpServletRequest servletRequest,
                                        HttpServletResponse servletResponse) {
        var account = registrationService.register(request);
        try {
            authenticateAndPersist(account.username(), request.password(), servletRequest, servletResponse);
            return ApiResponse.ok("注册成功", toMe());
        } catch (AuthenticationException exception) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "注册成功但自动登录失败，请重新登录");
        }
    }

    @GetMapping("/me")
    public ApiResponse<MeView> me() {
        return ApiResponse.ok(toMe());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        new SecurityContextLogoutHandler().logout(
                request, response, contextHolderStrategy.getContext().getAuthentication());
        return ApiResponse.ok("已退出登录", null);
    }

    private MeView toMe() {
        var user = currentUserService.current();
        return new MeView(user.id(), user.username(), user.displayName(), user.role());
    }

    private void authenticateAndPersist(String username,
                                        String password,
                                        HttpServletRequest request,
                                        HttpServletResponse response) {
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(username, password));
        request.getSession(true);
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
        var context = contextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
