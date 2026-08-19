package com.fishing.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishing.platform.common.ApiResponse;
import com.fishing.platform.mapper.UserMapper;
import com.fishing.platform.security.DatabaseUserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class AccountStateFilter extends OncePerRequestFilter {
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/csrf",
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/registration",
            "/error"
    );
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextHolderStrategy contextHolderStrategy;

    public AccountStateFilter(UserMapper userMapper,
                              ObjectMapper objectMapper,
                              SecurityContextRepository securityContextRepository,
                              SecurityContextHolderStrategy contextHolderStrategy) {
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
        this.securityContextRepository = securityContextRepository;
        this.contextHolderStrategy = contextHolderStrategy;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = contextHolderStrategy.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            filterChain.doFilter(request, response);
            return;
        }

        var account = userMapper.findByUsername(authentication.getName());
        boolean sessionVersionIsCurrent = account != null
                && authentication.getPrincipal() instanceof DatabaseUserPrincipal principal
                && principal.sessionVersion() == account.sessionVersion();
        if (account == null || !account.enabled() || !sessionVersionIsCurrent
                || !("ADMIN".equals(account.role()) || "OPERATOR".equals(account.role()))) {
            contextHolderStrategy.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            String requestPath = request.getRequestURI().substring(request.getContextPath().length());
            if (PUBLIC_PATHS.contains(requestPath)) {
                filterChain.doFilter(request, response);
                return;
            }
            writeUnauthorized(response);
            return;
        }

        String currentRole = "ROLE_" + account.role();
        boolean roleIsCurrent = authentication.getAuthorities().size() == 1
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> currentRole.equals(authority.getAuthority()));
        if (!roleIsCurrent) {
            var refreshed = UsernamePasswordAuthenticationToken.authenticated(
                    DatabaseUserPrincipal.from(account), null, List.of(new SimpleGrantedAuthority(currentRole)));
            refreshed.setDetails(authentication.getDetails());
            var context = contextHolderStrategy.createEmptyContext();
            context.setAuthentication(refreshed);
            contextHolderStrategy.setContext(context);
            securityContextRepository.saveContext(context, request, response);
        }
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.error("登录状态已失效"));
    }
}
