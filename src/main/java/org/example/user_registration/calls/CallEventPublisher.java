package org.example.user_registration.calls;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.api.core.ApiFutureCallback;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import com.google.protobuf.ByteString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
public class CallEventPublisher {
    private final Publisher publisher;
    private final ObjectMapper objectMapper;

    @Autowired
    public CallEventPublisher(
            ObjectMapper objectMapper,
            @Value("${app.calls.pubsub.project-id:}") String projectId,
            @Value("${app.calls.pubsub.topic-id:}") String topicId) throws IOException {
        this(objectMapper, projectId.isBlank() || topicId.isBlank()
                ? null
                : Publisher.newBuilder(ProjectTopicName.of(projectId, topicId)).build());
    }

    CallEventPublisher(ObjectMapper objectMapper, Publisher publisher) {
        this.objectMapper = objectMapper;
        this.publisher = publisher;
    }

    public CompletionStage<String> publish(CallEventRequest event) {
        if (publisher == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Call event publishing is not configured"));
        }

        final byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(event);
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }

        PubsubMessage message = PubsubMessage.newBuilder()
                .setData(ByteString.copyFrom(payload))
                .putAttributes("callId", event.callId())
                .build();
        ApiFuture<String> messageId = publisher.publish(message);
        CompletableFuture<String> result = new CompletableFuture<>();
        ApiFutures.addCallback(messageId, new ApiFutureCallback<>() {
            @Override
            public void onFailure(Throwable throwable) {
                result.completeExceptionally(throwable);
            }

            @Override
            public void onSuccess(String id) {
                result.complete(id);
            }
        }, Runnable::run);
        return result;
    }
}
