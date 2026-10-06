package com.usermanagement.notification;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Routes notifications to the sender registered for their channel. A real sender always wins over
 * the {@link LoggingNotificationSender} fallback.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final Map<Channel, NotificationSender> senders = new EnumMap<>(Channel.class);

    public NotificationService(List<NotificationSender> available) {
        for (Channel channel : Channel.values()) {
            available.stream()
                    .filter(sender -> sender.channel() == channel)
                    .min(Comparator.comparing(sender -> sender instanceof LoggingNotificationSender))
                    .ifPresent(sender -> senders.put(channel, sender));
        }
        senders.forEach((channel, sender) -> log.info("{} notifications are sent with {}",
                channel, sender.getClass().getSimpleName()));
    }

    /** Sends the notification; failures are logged rather than propagated. */
    public void send(Notification notification) {
        NotificationSender sender = senders.get(notification.channel());
        if (sender == null) {
            log.warn("No sender for channel {}, dropping {}", notification.channel(), notification);
            return;
        }
        try {
            sender.send(notification);
        } catch (RuntimeException ex) {
            log.error("Failed to send {}", notification, ex);
        }
    }
}
