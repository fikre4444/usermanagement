package com.usermanagement.notification;

/**
 * Extension point: delivers notifications for one channel.
 * <p>
 * Register a Spring bean implementing this interface (e.g. a Twilio or AWS SNS SMS sender) and it
 * automatically replaces the built-in logging fallback for its channel.
 */
public interface NotificationSender {

    Channel channel();

    void send(Notification notification);
}
