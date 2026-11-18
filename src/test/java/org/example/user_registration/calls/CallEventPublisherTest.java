package org.example.user_registration.calls;

import com.google.api.core.SettableApiFuture;
import com.google.cloud.pubsub.v1.Publisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallEventPublisherTest {
    private final CallEventRequest event = new CallEventRequest(
            "call-1", "+1 555 010 0100", "INBOUND", "IN_PROGRESS", null,
            Instant.parse("2026-01-02T03:04:05Z"), null);

    @Test
    void reportsMissingPubSubConfiguration() throws Exception {
        CallEventPublisher publisher = new CallEventPublisher(mock(ObjectMapper.class), "", "");

        assertThrows(CompletionException.class, () -> publisher.publish(event).toCompletableFuture().join());
    }

    @Test
    void publishesJsonAndReturnsThePubSubMessageId() {
        ObjectMapper mapper = mock(ObjectMapper.class);
        Publisher pubsub = mock(Publisher.class);
        SettableApiFuture<String> messageId = SettableApiFuture.create();
        messageId.set("message-123");
        when(mapper.writeValueAsBytes(event)).thenReturn("{\"callId\":\"call-1\"}".getBytes());
        when(pubsub.publish(org.mockito.ArgumentMatchers.any())).thenReturn(messageId);
        CallEventPublisher publisher = new CallEventPublisher(mapper, pubsub);

        assertEquals("message-123", publisher.publish(event).toCompletableFuture().join());
        ArgumentCaptor<com.google.pubsub.v1.PubsubMessage> message =
                ArgumentCaptor.forClass(com.google.pubsub.v1.PubsubMessage.class);
        verify(pubsub).publish(message.capture());
        assertEquals("call-1", message.getValue().getAttributesOrThrow("callId"));
        assertEquals("{\"callId\":\"call-1\"}", message.getValue().getData().toStringUtf8());
    }

    @Test
    void propagatesSerializationAndPubSubFailures() {
        ObjectMapper mapper = mock(ObjectMapper.class);
        Publisher pubsub = mock(Publisher.class);
        CallEventPublisher serializationFailure = new CallEventPublisher(mapper, pubsub);
        when(mapper.writeValueAsBytes(event)).thenThrow(new IllegalStateException("serialization failed"));
        assertThrows(CompletionException.class,
                () -> serializationFailure.publish(event).toCompletableFuture().join());

        doReturn("{}".getBytes()).when(mapper).writeValueAsBytes(event);
        SettableApiFuture<String> failedMessage = SettableApiFuture.create();
        failedMessage.setException(new IllegalStateException("pubsub failed"));
        when(pubsub.publish(org.mockito.ArgumentMatchers.any())).thenReturn(failedMessage);
        assertThrows(CompletionException.class,
                () -> serializationFailure.publish(event).toCompletableFuture().join());
    }
}
