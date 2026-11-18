package org.example.user_registration.calls;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallEventRequestTest {
    @Test
    void acceptsOptionalOrChronologicallyValidEndTimes() {
        Instant start = Instant.parse("2026-01-02T03:04:05Z");
        assertTrue(request(start, null).isEndAfterStart());
        assertTrue(request(start, start).isEndAfterStart());
        assertTrue(request(start, start.plusSeconds(60)).isEndAfterStart());
        assertFalse(request(start, start.minusSeconds(1)).isEndAfterStart());
        assertTrue(request(null, start).isEndAfterStart());
    }

    private CallEventRequest request(Instant start, Instant end) {
        return new CallEventRequest("call-1", "+1 555 010 0100", "INBOUND",
                "IN_PROGRESS", null, start, end);
    }
}
