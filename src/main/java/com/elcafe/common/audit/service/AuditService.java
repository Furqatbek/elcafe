package com.elcafe.common.audit.service;

import com.elcafe.common.audit.entity.AuditAction;
import com.elcafe.common.audit.entity.AuditLog;
import com.elcafe.common.audit.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.stream.Collectors;

/**
 * Service for comprehensive audit logging of security-critical operations.
 * Captures who, what, when, where for all financial and sensitive operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Log an audit event with full context
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog logAction(AuditLogBuilder builder) {
        AuditLog auditLog = builder.build();
        enrichWithRequestContext(auditLog);
        enrichWithSecurityContext(auditLog);

        AuditLog saved = auditLogRepository.save(auditLog);

        // Also log to application logs for immediate visibility
        if (auditLog.getResult() == AuditLog.AuditResult.DENIED ||
            auditLog.getResult() == AuditLog.AuditResult.FAILURE) {
            log.warn("AUDIT [{}]: {} by {} - {} (IP: {}, Terminal: {})",
                    auditLog.getResult(),
                    auditLog.getAction(),
                    auditLog.getUsername(),
                    auditLog.getActionDetail(),
                    auditLog.getIpAddress(),
                    auditLog.getTerminalId());
        } else {
            log.info("AUDIT [{}]: {} by {} on {}:{} (IP: {})",
                    auditLog.getResult(),
                    auditLog.getAction(),
                    auditLog.getUsername(),
                    auditLog.getEntityType(),
                    auditLog.getEntityId(),
                    auditLog.getIpAddress());
        }

        return saved;
    }

    /**
     * Async logging for non-critical audit events
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logActionAsync(AuditLogBuilder builder) {
        try {
            logAction(builder);
        } catch (Exception e) {
            log.error("Failed to log audit event asynchronously", e);
        }
    }

    /**
     * Log a financial operation (payment, refund, void)
     */
    public AuditLog logFinancialOperation(
            AuditAction action,
            Long orderId,
            String orderNumber,
            Long restaurantId,
            BigDecimal amount,
            String currency,
            String detail) {

        return logAction(AuditLogBuilder.create()
                .action(action)
                .entityType("Order")
                .entityId(orderId)
                .orderId(orderId)
                .orderNumber(orderNumber)
                .restaurantId(restaurantId)
                .amount(amount)
                .currency(currency)
                .actionDetail(detail)
                .result(AuditLog.AuditResult.SUCCESS));
    }

    /**
     * Log a denied operation
     */
    public AuditLog logDenied(
            AuditAction action,
            String entityType,
            Long entityId,
            Long restaurantId,
            String reason) {

        return logAction(AuditLogBuilder.create()
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .restaurantId(restaurantId)
                .actionDetail(reason)
                .result(AuditLog.AuditResult.DENIED)
                .failureReason(reason));
    }

    /**
     * Log a pending approval request
     */
    public AuditLog logPendingApproval(
            AuditAction action,
            Long orderId,
            String orderNumber,
            Long restaurantId,
            BigDecimal amount,
            String reason) {

        return logAction(AuditLogBuilder.create()
                .action(action)
                .entityType("Order")
                .entityId(orderId)
                .orderId(orderId)
                .orderNumber(orderNumber)
                .restaurantId(restaurantId)
                .amount(amount)
                .actionDetail(reason)
                .result(AuditLog.AuditResult.PENDING_APPROVAL));
    }

    /**
     * Log an authorization event
     */
    public AuditLog logAuthorization(
            AuditAction action,
            Long orderId,
            Long restaurantId,
            Long authorizedById,
            String authorizedByUsername,
            String reason) {

        return logAction(AuditLogBuilder.create()
                .action(action)
                .entityType("Order")
                .entityId(orderId)
                .orderId(orderId)
                .restaurantId(restaurantId)
                .authorizedById(authorizedById)
                .authorizedByUsername(authorizedByUsername)
                .authorizationReason(reason)
                .result(AuditLog.AuditResult.SUCCESS));
    }

    private void enrichWithRequestContext(AuditLog auditLog) {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                auditLog.setIpAddress(getClientIpAddress(request));
                auditLog.setUserAgent(request.getHeader("User-Agent"));
                auditLog.setTerminalId(request.getHeader("X-Terminal-ID"));
                auditLog.setDeviceInfo(request.getHeader("X-Device-Info"));
                auditLog.setSessionId(request.getSession(false) != null ?
                        request.getSession().getId() : null);
            }
        } catch (Exception e) {
            log.debug("Could not enrich audit log with request context", e);
        }
    }

    private void enrichWithSecurityContext(AuditLog auditLog) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                if (auditLog.getUsername() == null) {
                    auditLog.setUsername(auth.getName());
                }
                if (auditLog.getUserRole() == null) {
                    String roles = auth.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .collect(Collectors.joining(","));
                    auditLog.setUserRole(roles);
                }
            }
        } catch (Exception e) {
            log.debug("Could not enrich audit log with security context", e);
        }
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String[] headerNames = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_X_FORWARDED_FOR",
                "HTTP_X_FORWARDED",
                "HTTP_X_CLUSTER_CLIENT_IP",
                "HTTP_CLIENT_IP",
                "HTTP_FORWARDED_FOR",
                "HTTP_FORWARDED"
        };

        for (String header : headerNames) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // Take the first IP if comma-separated
                return ip.split(",")[0].trim();
            }
        }

        return request.getRemoteAddr();
    }

    /**
     * Convert object to JSON for storing previous/new values
     */
    public String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Failed to serialize object for audit log", e);
            return obj.toString();
        }
    }

    /**
     * Builder for creating audit log entries
     */
    public static class AuditLogBuilder {
        private final AuditLog.AuditLogBuilder builder = AuditLog.builder();

        public static AuditLogBuilder create() {
            return new AuditLogBuilder();
        }

        public AuditLogBuilder action(AuditAction action) {
            builder.action(action);
            builder.actionDetail(action.getDescription());
            return this;
        }

        public AuditLogBuilder entityType(String entityType) {
            builder.entityType(entityType);
            return this;
        }

        public AuditLogBuilder entityId(Long entityId) {
            builder.entityId(entityId);
            return this;
        }

        public AuditLogBuilder orderId(Long orderId) {
            builder.orderId(orderId);
            return this;
        }

        public AuditLogBuilder orderNumber(String orderNumber) {
            builder.orderNumber(orderNumber);
            return this;
        }

        public AuditLogBuilder restaurantId(Long restaurantId) {
            builder.restaurantId(restaurantId);
            return this;
        }

        public AuditLogBuilder userId(Long userId) {
            builder.userId(userId);
            return this;
        }

        public AuditLogBuilder username(String username) {
            builder.username(username);
            return this;
        }

        public AuditLogBuilder userRole(String userRole) {
            builder.userRole(userRole);
            return this;
        }

        public AuditLogBuilder amount(BigDecimal amount) {
            builder.amount(amount);
            return this;
        }

        public AuditLogBuilder currency(String currency) {
            builder.currency(currency);
            return this;
        }

        public AuditLogBuilder actionDetail(String detail) {
            builder.actionDetail(detail);
            return this;
        }

        public AuditLogBuilder previousValue(String previousValue) {
            builder.previousValue(previousValue);
            return this;
        }

        public AuditLogBuilder newValue(String newValue) {
            builder.newValue(newValue);
            return this;
        }

        public AuditLogBuilder authorizedById(Long id) {
            builder.authorizedById(id);
            return this;
        }

        public AuditLogBuilder authorizedByUsername(String username) {
            builder.authorizedByUsername(username);
            return this;
        }

        public AuditLogBuilder authorizationReason(String reason) {
            builder.authorizationReason(reason);
            return this;
        }

        public AuditLogBuilder result(AuditLog.AuditResult result) {
            builder.result(result);
            return this;
        }

        public AuditLogBuilder failureReason(String reason) {
            builder.failureReason(reason);
            return this;
        }

        public AuditLog build() {
            return builder.build();
        }
    }
}
