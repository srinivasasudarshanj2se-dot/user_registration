package org.example.user_registration.calls;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.concurrent.CompletionStage;

@RestController
@RequestMapping("/api/v1/calls")
public class CallEventController {
    private final CallEventService callEventService;

    public CallEventController(CallEventService callEventService) {
        this.callEventService = callEventService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CompletionStage<CallEventService.CallAcceptedResponse> publish(
            @Valid @RequestBody CallEventRequest event) {
        return callEventService.publish(event);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<CallEventService.CallRecordResponse> recent(
            @RequestParam(defaultValue = "50") int limit) {
        if (limit < 1 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 100");
        }
        return callEventService.recent(limit);
    }
}
