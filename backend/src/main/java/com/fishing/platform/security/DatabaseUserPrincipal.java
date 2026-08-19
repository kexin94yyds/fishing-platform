package com.fishing.platform.security;

import com.fishing.platform.domain.DomainModels.UserAccount;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

public final class DatabaseUserPrincipal implements UserDetails {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String username;
    private final String password;
    private final String role;
    private final boolean enabled;
    private final long sessionVersion;

    public DatabaseUserPrincipal(String username,
                                 String password,
                                 String role,
                                 boolean enabled,
                                 long sessionVersion) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.enabled = enabled;
        this.sessionVersion = sessionVersion;
    }

    public static DatabaseUserPrincipal from(UserAccount account) {
        return new DatabaseUserPrincipal(account.username(), account.passwordHash(), account.role(),
                account.enabled(), account.sessionVersion());
    }

    public long sessionVersion() {
        return sessionVersion;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
