package com.ordersystem.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;

@Getter
public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String email;
    private final UUID customerSaasId;
    private final String password;
    private final LocalDateTime tokenRevokedBefore;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(UUID id, String email, UUID customerSaasId, String password,
                         LocalDateTime tokenRevokedBefore,
                         Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.email = email;
        this.customerSaasId = customerSaasId;
        this.password = password;
        this.tokenRevokedBefore = tokenRevokedBefore;
        this.authorities = authorities;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}
