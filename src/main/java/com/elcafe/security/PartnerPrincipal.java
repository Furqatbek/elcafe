package com.elcafe.security;

import com.elcafe.modules.partner.entity.Partner;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * The authenticated caller behind a valid {@code X-Partner-Key}: an external system, not a person.
 *
 * <p>It carries {@code ROLE_PARTNER} and nothing else. That role is deliberately outside the staff role
 * hierarchy — a partner is never an operator, however privileged the integration feels — so it can only
 * reach endpoints that name it explicitly.
 *
 * <p>Holding this principal proves <em>who</em> is calling, never <em>what they may touch</em>: a
 * partner's venues are granted per restaurant, so every handler must still consult
 * {@code PartnerAccessService} before reading or writing a venue's data.
 */
@Data
@AllArgsConstructor
public class PartnerPrincipal implements UserDetails {

    private Long id;
    private String slug;
    private String name;

    public static PartnerPrincipal create(Partner partner) {
        return new PartnerPrincipal(partner.getId(), partner.getSlug(), partner.getName());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_PARTNER"));
    }

    /** Partners authenticate by API key; there is no password to compare. */
    @Override
    public String getPassword() {
        return null;
    }

    /** The slug, so rate-limit buckets and audit logs key on a stable partner handle. */
    @Override
    public String getUsername() {
        return slug;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Always true here: an inactive partner never reaches this object, because the authentication
     * filter refuses to build a principal for one.
     */
    @Override
    public boolean isEnabled() {
        return true;
    }
}
