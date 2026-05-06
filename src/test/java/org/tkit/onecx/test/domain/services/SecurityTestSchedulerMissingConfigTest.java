package org.tkit.onecx.test.domain.services;

import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.tkit.onecx.test.domain.metrics.SecurityTestMetrics;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class SecurityTestSchedulerMissingConfigTest {

    @Inject
    SecurityTestScheduler scheduler;

    @InjectMock
    TestService testService;

    @InjectMock
    SecurityTestMetrics metrics;

    @Test
    void executeScheduledTests_skipsExecutionWhenRequiredConfigIsMissing() {
        // given - no URL configured, no test profiles enabled

        // when
        scheduler.executeScheduledTests();

        // then
        verifyNoInteractions(testService);
        verifyNoInteractions(metrics);
    }
}
