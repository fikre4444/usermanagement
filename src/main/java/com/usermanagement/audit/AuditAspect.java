package com.usermanagement.audit;

import com.usermanagement.common.error.ApiException;
import com.usermanagement.common.error.ErrorCode;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Records an audit entry around every {@link Audited} method. It runs before Spring Security's
 * method interceptors ({@code @PreAuthorize} has order 200), so it also sees authorisation failures.
 */
@Aspect
@Component
@Order(AuditAspect.ORDER)
class AuditAspect {

    /** After Spring's ExposeInvocationInterceptor, before method security. */
    static final int ORDER = 0;


    /** Set on the request once the aspect has recorded it, so the interceptor doesn't record it again. */
    static final String RECORDED_ATTRIBUTE = AuditAspect.class.getName() + ".recorded";

    private final AuditLog auditLog;
    private final AuditDetails auditDetails;
    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final Map<String, Expression> expressions = new ConcurrentHashMap<>();

    AuditAspect(AuditLog auditLog, AuditDetails auditDetails) {
        this.auditLog = auditLog;
        this.auditDetails = auditDetails;
    }

    @Around("@annotation(com.usermanagement.audit.Audited)")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Audited audited = method.getAnnotation(Audited.class);
        Object[] args = joinPoint.getArgs();
        Map<String, Object> details = auditDetails.capture(method, args);
        markRecorded();
        try {
            Object result = joinPoint.proceed();
            auditLog.record(audited.action(), AuditOutcome.SUCCESS, null, target(audited, method, args, result),
                    details);
            return result;
        } catch (Throwable ex) {
            auditLog.record(audited.action(), outcomeOf(ex), errorCodeOf(ex), target(audited, method, args, null),
                    details);
            throw ex;
        }
    }

    private AuditRecord.Target target(Audited audited, Method method, Object[] args, Object result) {
        if (audited.target().isEmpty()) {
            return null;
        }
        Object id = audited.targetId().isEmpty()
                ? pathVariableId(method, args)
                : evaluate(audited.targetId(), method, args, result);
        return AuditLog.target(audited.target(), id);
    }

    private Object evaluate(String expression, Method method, Object[] args, Object result) {
        SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().withInstanceMethods().build();
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            context.setVariable(parameters[i].getName(), args[i]);
        }
        context.setVariable("result", result);
        context.setVariable("actorId", currentUserId());
        try {
            return expressions.computeIfAbsent(expression, parser::parseExpression).getValue(context);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static Object pathVariableId(Method method, Object[] args) {
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(PathVariable.class)
                    && (parameters[i].getName().equals("id") || parameters[i].getName().equals("name"))) {
                return args[i];
            }
        }
        return null;
    }

    private static String currentUserId() {
        return SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token
                ? token.getToken().getSubject()
                : null;
    }

    static AuditOutcome outcomeOf(Throwable ex) {
        if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
            return AuditOutcome.DENIED;
        }
        return AuditOutcome.FAILURE;
    }

    static String errorCodeOf(Throwable ex) {
        if (ex instanceof ApiException apiException) {
            return apiException.code().name();
        }
        if (ex instanceof AccessDeniedException) {
            return ErrorCode.ACCESS_DENIED.name();
        }
        if (ex instanceof AuthenticationException) {
            return ErrorCode.UNAUTHORIZED.name();
        }
        return ErrorCode.INTERNAL_ERROR.name();
    }

    private static void markRecorded() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            attributes.setAttribute(RECORDED_ATTRIBUTE, Boolean.TRUE, RequestAttributes.SCOPE_REQUEST);
        }
    }
}
