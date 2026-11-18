package org.example.user_registration.calls;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;

import java.time.Instant;

public record CallEventRequest(
        @NotBlank @Size(max = 128) String callId,
        @NotBlank @Pattern(regexp = "^[+0-9() .-]{7,32}$") String callerNumber,
        @NotBlank @Pattern(regexp = "INBOUND|OUTBOUND") String direction,
        @NotBlank @Pattern(regexp = "RINGING|IN_PROGRESS|COMPLETED|MISSED") String status,
        @Size(max = 100) String agentName,
        @NotNull Instant startedAt,
        Instant endedAt
) {
    @AssertTrue(message = "endedAt cannot be earlier than startedAt")
    public boolean isEndAfterStart() {
        return startedAt == null || endedAt == null || !endedAt.isBefore(startedAt);
    }
}
