package com.usermanagement.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
class NotificationConfig {

    @Bean
    LoggingNotificationSender loggingEmailSender(NotificationProperties properties) {
        return new LoggingNotificationSender(Channel.EMAIL, properties.logContent());
    }

    @Bean
    LoggingNotificationSender loggingSmsSender(NotificationProperties properties) {
        return new LoggingNotificationSender(Channel.SMS, properties.logContent());
    }

    /** Real email delivery as soon as {@code spring.mail.host} is configured. */
    @Bean
    @ConditionalOnProperty(prefix = "spring.mail", name = "host")
    SmtpEmailSender smtpEmailSender(JavaMailSender mailSender, NotificationProperties properties) {
        return new SmtpEmailSender(mailSender, properties.emailFrom());
    }
}
