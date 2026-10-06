package com.usermanagement.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * One audit log entry: who did what to which object, when, from where, and how it ended.
 * This is the {@code data} of every message on the {@code audit} stream.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditRecord(
        String action,
        AuditOutcome outcome,
        /* Error code (see ErrorCode) when the outcome is not SUCCESS. */
        String errorCode,
        Actor actor,
        Target target,
        RequestInfo request,
        Map<String, Object> details,
        Instant occurredAt,
        String service) {

    public enum ActorType {
        /** An authenticated user (id = user id). */
        USER,
        /** An unauthenticated caller, e.g. someone signing in or registering. */
        ANONYMOUS,
        /** The service itself (startup tasks, scheduled jobs). */
        SYSTEM
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Actor(ActorType type, String id, String userType, List<String> roles) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Target(String type, String id) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RequestInfo(String requestId, String method, String path, String ip, String userAgent) {
    }
}
