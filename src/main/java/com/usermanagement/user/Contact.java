package com.usermanagement.user;

import com.usermanagement.notification.Channel;

/** Where to reach a user: an email address or a phone number. */
public record Contact(Channel channel, String destination) {
}
