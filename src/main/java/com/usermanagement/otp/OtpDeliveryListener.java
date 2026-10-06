package com.usermanagement.otp;

import com.usermanagement.notification.Notification;
import com.usermanagement.notification.NotificationProperties;
import com.usermanagement.notification.NotificationService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Delivers codes asynchronously and only once the code has been committed to the database. */
@Component
class OtpDeliveryListener {

    private final NotificationService notifications;
    private final NotificationProperties properties;

    OtpDeliveryListener(NotificationService notifications, NotificationProperties properties) {
        this.notifications = notifications;
        this.properties = properties;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOtpIssued(OtpIssuedEvent event) {
        String subject = properties.appName() + " verification code";
        String body = "Your code to " + event.purpose().description() + " is " + event.code()
                + ". It expires in " + Math.max(1, event.ttl().toMinutes()) + " minutes."
                + " If you did not request it, you can ignore this message.";
        notifications.send(new Notification(event.channel(), event.destination(), subject, body));
    }
}
