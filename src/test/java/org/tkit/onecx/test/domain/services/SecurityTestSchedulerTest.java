package org.tkit.onecx.test.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tkit.onecx.test.domain.metrics.SecurityTestMetrics;
import org.tkit.onecx.test.domain.models.ServiceException;
import org.tkit.onecx.test.domain.models.TestRequest;
import org.tkit.onecx.test.domain.models.TestResponse;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(SecurityTestSchedulerTestProfile.class)
class SecurityTestSchedulerTest {

    @Inject
    SecurityTestScheduler scheduler;

    @InjectMock
    TestService testService;

    @InjectMock
    SecurityTestMetrics metrics;

    @Test
    void executeScheduledTests_runsPrivateAndPublicServicesAndBuildsRequests() {
        //given
        var okResponse = new TestResponse();
        okResponse.setStatus(TestResponse.Status.OK);
        when(testService.execute(any(TestRequest.class))).thenReturn(okResponse);

        //when
        scheduler.executeScheduledTests();

        //then
        ArgumentCaptor<TestRequest> requestCaptor = ArgumentCaptor.forClass(TestRequest.class);
        verify(testService, times(4)).execute(requestCaptor.capture());

        List<TestRequest> requests = requestCaptor.getAllValues();
        assertThat(requests)
                .allSatisfy(r -> {
                    assertThat(r.getId()).isNotBlank();
                    assertThat(r.getUrl()).isNotBlank();
                    assertThat(r.getService()).isNotBlank();
                });

        verify(metrics).incrementRequest("svc-p1", "OK");
        verify(metrics).incrementRequest("svc-p2", "OK");
        verify(metrics).incrementRequest("svc-u1", "OK");
        verify(metrics).incrementRequest("svc-u2", "OK");
    }

    @Test
    void executeScheduledTests_recordsErrorStatusWhenExecutionThrowsServiceException() {
        //given
        when(testService.execute(any(TestRequest.class))).thenThrow(new ServiceException("test error"));

        //when
        scheduler.executeScheduledTests();

        //then
        verify(testService, times(4)).execute(any(TestRequest.class));
        verify(metrics).incrementRequest("svc-p1", "ERROR");
        verify(metrics).incrementRequest("svc-p2", "ERROR");
        verify(metrics).incrementRequest("svc-u1", "ERROR");
        verify(metrics).incrementRequest("svc-u2", "ERROR");
    }
}
