package com.usermanagement.audit;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class AuditConfig implements WebMvcConfigurer {

    private final AuditLog auditLog;

    AuditConfig(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RejectedRequestAuditor(auditLog));
    }
}
