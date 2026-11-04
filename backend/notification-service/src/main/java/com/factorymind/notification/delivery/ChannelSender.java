package com.factorymind.notification.delivery;

import com.factorymind.notification.routing.Channel;

/** Sends one message over one channel. Throws {@link DeliveryException} when the message did not go out. */
public interface ChannelSender {

    Channel channel();

    void send(String recipient, OutboundMessage message);
}
