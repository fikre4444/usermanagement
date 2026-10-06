package com.usermanagement.audit;

import com.usermanagement.common.web.CorrelationIdFilter;
import com.usermanagement.events.OutboxStore;
import com.usermanagement.events.Streams;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Records audit entries. Callers only describe the activity; the actor (from the security
 * context), the request information and the timestamp are filled in here.
 * <p>
 * Each entry is written to the outbox ({@link Streams#AUDIT} stream) in its <b>own</b> transaction,
 * so it is kept even when the activity itself fails and its transaction rolls back. From there the
 * relay publishes it to whichever sink handles the stream (Kafka, webhook, log...): this class
 * knows nothing about the transport.
 */
@Service
public class AuditLog {

    private static final Logger log = LoggerFactory.getLogger(AuditLog.class);
    private static final int MAX_USER_AGENT = 256;

    private final OutboxStore outbox;
    private final AuditProperties properties;
    private final TransactionTemplate independentTransaction;
    private final Clock clock;
    private final String serviceName;

    public AuditLog(OutboxStore outbox, AuditProperties properties, PlatformTransactionManager transactionManager,
                    Clock clock, @Value("${spring.application.name:user-management-service}") String serviceName) {
        this.outbox = outbox;
        this.properties = properties;
        this.independentTransaction = new TransactionTemplate(transactionManager);
        this.independentTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
        this.serviceName = serviceName;
    }

    public void success(String action, AuditRecord.Target target, Map<String, Object> details) {
        record(action, AuditOutcome.SUCCESS, null, target, details);
    }

    /**
     * Records one entry. Never throws: a failure to audit is logged as an error but does not break
     * the activity being audited.
     */
    public void record(String action, AuditOutcome outcome, String errorCode, AuditRecord.Target target,
                       Map<String, Object> details) {
        if (!properties.enabled()) {
            return;
        }
        try {
            AuditRecord entry = new AuditRecord(action, outcome, errorCode, currentActor(), target,
                    currentRequest(), details == null || details.isEmpty() ? null : details, clock.instant(),
                    serviceName);
            independentTransaction.executeWithoutResult(status -> append(entry));
        } catch (RuntimeException ex) {
            log.error("Could not record audit entry {} {}", action, outcome, ex);
        }
    }

    public static AuditRecord.Target target(String type, Object id) {
        return new AuditRecord.Target(type, id == null ? null : id.toString());
    }

    private void append(AuditRecord entry) {
        // Partition key: the object acted upon, else the actor, so one user's history stays ordered.
        boolean hasTarget = entry.target() != null && entry.target().id() != null;
        String aggregateType = hasTarget ? entry.target().type() : entry.actor().type().name().toLowerCase();
        String aggregateId = hasTarget ? entry.target().id()
                : entry.actor().id() != null ? entry.actor().id() : entry.actor().type().name().toLowerCase();
        Instant occurredAt = entry.occurredAt();
        outbox.append(Streams.AUDIT, entry.action(), aggregateType, aggregateId, occurredAt, entry);
    }

    private static AuditRecord.Actor currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            List<String> roles = token.getToken().getClaimAsStringList("roles");
            return new AuditRecord.Actor(AuditRecord.ActorType.USER, token.getToken().getSubject(),
                    token.getToken().getClaimAsString("user_type"), roles);
        }
        return currentHttpRequest() != null
                ? new AuditRecord.Actor(AuditRecord.ActorType.ANONYMOUS, null, null, null)
                : new AuditRecord.Actor(AuditRecord.ActorType.SYSTEM, null, null, null);
    }

    private static AuditRecord.RequestInfo currentRequest() {
        HttpServletRequest request = currentHttpRequest();
        if (request == null) {
            return null;
        }
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && userAgent.length() > MAX_USER_AGENT) {
            userAgent = userAgent.substring(0, MAX_USER_AGENT);
        }
        return new AuditRecord.RequestInfo(MDC.get(CorrelationIdFilter.MDC_KEY), request.getMethod(),
                request.getRequestURI(), request.getRemoteAddr(), userAgent);
    }

    private static HttpServletRequest currentHttpRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest()
                : null;
    }
}
