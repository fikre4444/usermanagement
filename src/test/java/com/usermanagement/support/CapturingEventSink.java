package com.usermanagement.support;

import com.usermanagement.events.EventSink;
import com.usermanagement.events.OutboxMessage;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CapturingEventSink implements EventSink {

    private final List<OutboxMessage> messages = new CopyOnWriteArrayList<>();

    @Override
    public void publish(OutboxMessage message) {
        messages.add(message);
    }

    public List<OutboxMessage> messagesFor(String aggregateId) {
        return messages.stream().filter(message -> message.aggregateId().equals(aggregateId)).toList();
    }
}
