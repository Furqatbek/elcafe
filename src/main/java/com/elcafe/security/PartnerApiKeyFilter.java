package com.elcafe.security;

import com.elcafe.modules.partner.entity.Partner;
import com.elcafe.modules.partner.service.PartnerAccessService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Authenticates partner API traffic from the {@code X-Partner-Key} header.
 *
 * <p>Deliberately a separate filter from {@link JwtAuthenticationFilter} rather than another branch
 * inside it: partners present a bearer-less, long-lived credential with its own role and its own
 * authorization model, and folding that into the token filter would put a non-expiring key on the same
 * code path as session tokens.
 *
 * <p>It runs only on {@code /api/v1/partner/**} and it never rejects anything itself. On a missing,
 * unknown, or revoked key it simply leaves the security context empty and lets the chain continue —
 * {@code SecurityConfig} requires {@code ROLE_PARTNER} there, so the request lands on the standard 401
 * entry point. That keeps "no key", "bad key" and "revoked key" indistinguishable to the caller.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PartnerApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Partner-Key";
    private static final String PARTNER_PATH_PREFIX = "/api/v1/partner/";

    private final PartnerAccessService partnerAccessService;

    /** Every other route authenticates by token; spending a hash and a query on them would be waste. */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null || !uri.startsWith(PARTNER_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String presentedKey = request.getHeader(HEADER);
            try {
                Optional<Partner> partner = partnerAccessService.authenticate(presentedKey);
                if (partner.isPresent()) {
                    PartnerPrincipal principal = PartnerPrincipal.create(partner.get());
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            principal, null, principal.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } else if (presentedKey != null && !presentedKey.isBlank()) {
                    // Worth a log line: a key was presented and did not resolve, which is either a
                    // partner still using a rotated key or someone probing. The key itself is never
                    // logged.
                    log.warn("Rejected partner request to {}: unknown or inactive API key",
                            request.getRequestURI());
                }
            } catch (Exception e) {
                // A lookup failure must not authenticate anyone. Leave the context empty and let the
                // chain produce the 401.
                log.error("Partner authentication failed for {}: {}", request.getRequestURI(), e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }
}
