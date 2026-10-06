package com.usermanagement.support;

import com.usermanagement.notification.Channel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class TestSupportConfig {

    @Bean
    CapturingNotificationSender emailInbox() {
        return new CapturingNotificationSender(Channel.EMAIL);
    }

    @Bean
    CapturingNotificationSender smsInbox() {
        return new CapturingNotificationSender(Channel.SMS);
    }

    @Bean
    CapturingEventSink capturingEventSink() {
        return new CapturingEventSink();
    }
}
