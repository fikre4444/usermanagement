package com.usermanagement.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fallback sender that only logs. Used for a channel when no real sender is configured, which makes
 * local development work without any external provider.
 */
public class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    private final Channel channel;
    private final boolean logContent;

    public LoggingNotificationSender(Channel channel, boolean logContent) {
        this.channel = channel;
        this.logContent = logContent;
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public void send(Notification notification) {
        if (logContent) {
            log.info("[{}] to {}: {} - {}", channel, notification.recipient(), notification.subject(), notification.body());
        } else {
            log.info("[{}] to {}: {} (no {} provider configured, message not delivered)",
                    channel, notification.recipient(), notification.subject(), channel);
        }
    }
}
