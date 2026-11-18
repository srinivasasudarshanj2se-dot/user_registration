package org.example.user_registration.calls;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "call_events")
public class CallRecord {
    @Id
    @Column(name = "call_id", nullable = false, length = 128)
    private String callId;

    @Column(name = "caller_number", nullable = false, length = 32)
    private String callerNumber;

    @Column(nullable = false, length = 16)
    private String direction;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "agent_name", length = 100)
    private String agentName;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected CallRecord() {
    }

    CallRecord(
            String callId,
            String callerNumber,
            String direction,
            String status,
            String agentName,
            Instant startedAt,
            Instant endedAt) {
        this.callId = callId;
        this.callerNumber = callerNumber;
        this.direction = direction;
        this.status = status;
        this.agentName = agentName;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
    }

    public String getCallId() {
        return callId;
    }

    public String getCallerNumber() {
        return callerNumber;
    }

    public String getDirection() {
        return direction;
    }

    public String getStatus() {
        return status;
    }

    public String getAgentName() {
        return agentName;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }
}
