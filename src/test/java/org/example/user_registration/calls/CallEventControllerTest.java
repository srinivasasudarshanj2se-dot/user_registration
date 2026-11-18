package org.example.user_registration.calls;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CallEventControllerTest {
    private final CallEventService service = mock(CallEventService.class);
    private final CallEventController controller = new CallEventController(service);

    @Test
    void returnsRecentCallsForValidLimits() {
        List<CallEventService.CallRecordResponse> calls = List.of();
        when(service.recent(100)).thenReturn(calls);

        assertEquals(calls, controller.recent(100));
        verify(service).recent(100);
    }

    @Test
    void rejectsLimitsOutsideTheSupportedRange() {
        assertThrows(ResponseStatusException.class, () -> controller.recent(0));
        assertThrows(ResponseStatusException.class, () -> controller.recent(101));
    }

    @Test
    void forwardsEventsForAsynchronousPublishing() {
        CallEventRequest request = new CallEventRequest(
                "call-1", "+1 555 010 0100", "INBOUND", "MISSED", null,
                java.time.Instant.parse("2026-01-02T03:04:05Z"), null);
        CompletableFuture<CallEventService.CallAcceptedResponse> response =
                CompletableFuture.completedFuture(new CallEventService.CallAcceptedResponse(
                        "call-1", "message-1", "Call event queued"));
        when(service.publish(request)).thenReturn(response);

        assertEquals(response, controller.publish(request));
        verify(service).publish(request);
    }
}
