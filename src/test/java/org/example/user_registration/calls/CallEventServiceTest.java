package org.example.user_registration.calls;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallEventServiceTest {
    private final CallEventPublisher publisher = mock(CallEventPublisher.class);
    private final CallRecordRepository repository = mock(CallRecordRepository.class);
    private final CallEventService service = new CallEventService(publisher, repository);

    @Test
    void publishesAndReturnsAnAcknowledgment() {
        CallEventRequest request = new CallEventRequest(
                "call-1", "+1 555 010 0100", "INBOUND", "RINGING", null,
                Instant.parse("2026-01-02T03:04:05Z"), null);
        when(publisher.publish(request)).thenReturn(CompletableFuture.completedFuture("message-1"));

        CallEventService.CallAcceptedResponse response = service.publish(request).toCompletableFuture().join();

        assertEquals(new CallEventService.CallAcceptedResponse("call-1", "message-1", "Call event queued"), response);
    }

    @Test
    void listsRecentCallRecordsAsResponses() {
        Instant startedAt = Instant.parse("2026-01-02T03:04:05Z");
        CallRecord record = new CallRecord(
                "call-1", "+1 555 010 0100", "OUTBOUND", "COMPLETED", "Alex",
                startedAt, startedAt.plusSeconds(60));
        when(repository.findAllByOrderByStartedAtDesc(PageRequest.of(0, 10))).thenReturn(List.of(record));

        List<CallEventService.CallRecordResponse> response = service.recent(10);

        verify(repository).findAllByOrderByStartedAtDesc(PageRequest.of(0, 10));
        assertEquals(new CallEventService.CallRecordResponse(
                "call-1", "+1 555 010 0100", "OUTBOUND", "COMPLETED", "Alex",
                startedAt, startedAt.plusSeconds(60)), response.getFirst());
        assertEquals("call-1", record.getCallId());
        assertEquals("+1 555 010 0100", record.getCallerNumber());
        assertEquals("OUTBOUND", record.getDirection());
        assertEquals("COMPLETED", record.getStatus());
        assertEquals("Alex", record.getAgentName());
        assertEquals(startedAt, record.getStartedAt());
        assertEquals(startedAt.plusSeconds(60), record.getEndedAt());
    }
}
