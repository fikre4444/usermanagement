package com.usermanagement.notification;

/** A message to deliver to one recipient (an email address or an E.164 phone number). */
public record Notification(Channel channel, String recipient, String subject, String body) {

    @Override
    public String toString() {
        // Bodies may contain one-time codes: never print them by accident.
        return "Notification[channel=" + channel + ", recipient=" + recipient + ", subject=" + subject + "]";
    }
}
