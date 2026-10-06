package com.usermanagement.audit;

import com.usermanagement.common.error.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Audits calls to {@link Audited} endpoints that were rejected before the method ran (invalid body,
 * malformed JSON, wrong parameter types...), which {@link AuditAspect} cannot see.
 */
class RejectedRequestAuditor implements HandlerInterceptor {

    private final AuditLog auditLog;

    RejectedRequestAuditor(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        if (!(handler instanceof HandlerMethod handlerMethod)
                || request.getAttribute(AuditAspect.RECORDED_ATTRIBUTE) != null) {
            return;
        }
        Audited audited = handlerMethod.getMethodAnnotation(Audited.class);
        if (audited == null || response.getStatus() < 400) {
            return;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        if (request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) instanceof Map<?, ?> variables) {
            variables.forEach((key, value) -> details.put(String.valueOf(key), value));
        }
        if (request.getAttribute(GlobalExceptionHandler.ERRORS_ATTRIBUTE) instanceof Map<?, ?> errors) {
            details.put("errors", errors);
        }
        Object code = request.getAttribute(GlobalExceptionHandler.ERROR_CODE_ATTRIBUTE);
        Object targetId = details.getOrDefault("id", details.get("name"));
        int status = response.getStatus();
        AuditOutcome outcome = status == 401 || status == 403 ? AuditOutcome.DENIED : AuditOutcome.FAILURE;
        auditLog.record(audited.action(), outcome, code == null ? null : code.toString(),
                audited.target().isEmpty() ? null : AuditLog.target(audited.target(), targetId), details);
    }
}
