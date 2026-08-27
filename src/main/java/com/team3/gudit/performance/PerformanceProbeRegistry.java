package com.team3.gudit.performance;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Profile("performance")
public class PerformanceProbeRegistry {

    private final ConcurrentHashMap<String, RunState> runs = new ConcurrentHashMap<>();

    public void recordArrival(String runId, String requestId) {
        RunState state = runs.computeIfAbsent(runId, ignored -> new RunState());
        Instant now = Instant.now();
        state.firstSeenAt.compareAndSet(null, now);
        state.lastSeenAt.set(now);

        if (!state.requestIds.add(requestId)) {
            state.duplicateRequests.incrementAndGet();
        }
    }

    public void recordCompletion(String runId, String requestId) {
        RunState state = runs.get(runId);
        if (state != null) {
            state.completedRequestIds.add(requestId);
        }
    }

    public Snapshot snapshot(String runId) {
        RunState state = runs.get(runId);
        if (state == null) {
            return new Snapshot(runId, 0, 0, 0, 0, null, null);
        }

        return new Snapshot(
                runId,
                state.requestIds.size(),
                state.completedRequestIds.size(),
                Math.max(0, state.requestIds.size() - state.completedRequestIds.size()),
                state.duplicateRequests.get(),
                state.firstSeenAt.get(),
                state.lastSeenAt.get()
        );
    }

    public void reset(String runId) {
        runs.put(runId, new RunState());
    }

    public void remove(String runId) {
        runs.remove(runId);
    }

    public record Snapshot(
            String runId,
            long uniqueRequests,
            long completedRequests,
            long activeRequests,
            long duplicateRequests,
            Instant firstSeenAt,
            Instant lastSeenAt
    ) {
    }

    private static final class RunState {
        private final Set<String> requestIds = ConcurrentHashMap.newKeySet();
        private final Set<String> completedRequestIds = ConcurrentHashMap.newKeySet();
        private final AtomicLong duplicateRequests = new AtomicLong();
        private final AtomicReference<Instant> firstSeenAt = new AtomicReference<>();
        private final AtomicReference<Instant> lastSeenAt = new AtomicReference<>();
    }
}
