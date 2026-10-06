package com.usermanagement.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.usermanagement.notification.Channel;
import com.usermanagement.notification.Notification;
import com.usermanagement.notification.NotificationSender;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Records notifications instead of sending them so tests can read the one-time codes. */
public class CapturingNotificationSender implements NotificationSender {

    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");

    private final Channel channel;
    private final Map<String, BlockingQueue<Notification>> inbox = new ConcurrentHashMap<>();

    public CapturingNotificationSender(Channel channel) {
        this.channel = channel;
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public void send(Notification notification) {
        queue(notification.recipient()).add(notification);
    }

    /** Waits for the next message to {@code recipient} and returns the code it contains. */
    public String takeCode(String recipient) {
        try {
            Notification notification = queue(recipient).poll(5, TimeUnit.SECONDS);
            assertThat(notification).as("notification to %s", recipient).isNotNull();
            Matcher matcher = CODE.matcher(notification.body());
            assertThat(matcher.find()).as("code in %s", notification.body()).isTrue();
            return matcher.group(1);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    public boolean hasPending(String recipient) {
        return !queue(recipient).isEmpty();
    }

    private BlockingQueue<Notification> queue(String recipient) {
        return inbox.computeIfAbsent(recipient, key -> new LinkedBlockingQueue<>());
    }
}
