package com.team3.gudit.performance;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PerformanceProbeRegistryTest {

    private final PerformanceProbeRegistry registry = new PerformanceProbeRegistry();

    @Test
    void countsUniqueAndDuplicateRequestIdsSeparately() {
        registry.reset("run-100");

        registry.recordArrival("run-100", "request-1");
        registry.recordArrival("run-100", "request-1");
        registry.recordArrival("run-100", "request-2");
        registry.recordCompletion("run-100", "request-1");

        PerformanceProbeRegistry.Snapshot snapshot = registry.snapshot("run-100");

        assertThat(snapshot.uniqueRequests()).isEqualTo(2);
        assertThat(snapshot.completedRequests()).isEqualTo(1);
        assertThat(snapshot.activeRequests()).isEqualTo(1);
        assertThat(snapshot.duplicateRequests()).isEqualTo(1);
        assertThat(snapshot.firstSeenAt()).isNotNull();
        assertThat(snapshot.lastSeenAt()).isNotNull();
    }

    @Test
    void resetRemovesPreviousCounts() {
        registry.recordArrival("run-100", "request-1");

        registry.reset("run-100");

        assertThat(registry.snapshot("run-100").uniqueRequests()).isZero();
    }
}
