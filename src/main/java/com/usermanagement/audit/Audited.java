package com.usermanagement.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint as an auditable activity. Every call produces one audit record, whether it
 * succeeds, fails or is denied, including calls rejected before the method runs (e.g. invalid
 * input). Request bodies, path variables and query parameters are captured as details, with
 * secrets redacted.
 *
 * <pre>
 * &#64;Audited(action = "admin.user.status-changed", target = "user")       // target id = path variable "id"
 * &#64;Audited(action = "auth.register", target = "user", targetId = "#result?.id()")
 * &#64;Audited(action = "user.profile.updated", target = "user", targetId = "#actorId")
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Audited {

    /** Stable, dotted name of the activity, e.g. {@code auth.login}. */
    String action();

    /** Type of the object acted upon, e.g. {@code user} or {@code role}. Empty if none. */
    String target() default "";

    /**
     * SpEL expression for the target id. Available variables: the method parameters by name,
     * {@code #result} (on success) and {@code #actorId}. Defaults to the {@code id} or {@code name}
     * path variable.
     */
    String targetId() default "";
}
