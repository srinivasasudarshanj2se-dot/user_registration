package org.example.user_registration.calls;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletionStage;

@Service
public class CallEventService {
    private final CallEventPublisher publisher;
    private final CallRecordRepository repository;

    public CallEventService(CallEventPublisher publisher, CallRecordRepository repository) {
        this.publisher = publisher;
        this.repository = repository;
    }

    public CompletionStage<CallAcceptedResponse> publish(CallEventRequest event) {
        return publisher.publish(event).thenApply(messageId ->
                new CallAcceptedResponse(event.callId(), messageId, "Call event queued"));
    }

    public List<CallRecordResponse> recent(int limit) {
        return repository.findAllByOrderByStartedAtDesc(PageRequest.of(0, limit))
                .stream()
                .map(CallRecordResponse::from)
                .toList();
    }

    public record CallAcceptedResponse(String callId, String messageId, String message) {
    }

    public record CallRecordResponse(
            String callId,
            String callerNumber,
            String direction,
            String status,
            String agentName,
            java.time.Instant startedAt,
            java.time.Instant endedAt
    ) {
        static CallRecordResponse from(CallRecord record) {
            return new CallRecordResponse(record.getCallId(), record.getCallerNumber(), record.getDirection(),
                    record.getStatus(), record.getAgentName(), record.getStartedAt(), record.getEndedAt());
        }
    }
}
