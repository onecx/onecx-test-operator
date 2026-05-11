package org.tkit.onecx.test.domain.services;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tkit.onecx.test.domain.metrics.SecurityTestMetrics;
import org.tkit.onecx.test.domain.models.ServiceException;
import org.tkit.onecx.test.domain.models.TestRequest;
import org.tkit.onecx.test.domain.models.TestRunConfig;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;

@ApplicationScoped
public class SecurityTestScheduler {

    private static final Logger log = LoggerFactory.getLogger(SecurityTestScheduler.class);

    @Inject
    TestService testService;

    @Inject
    SecurityTestMetrics securityTestMetrics;

    @Inject
    TestRunConfig config;

    @Scheduled(cron = "{onecx.test.scheduler.cron}", concurrentExecution = ConcurrentExecution.SKIP)
    void executeScheduledTests() {
        if (config.services().isEmpty()) {
            log.info("Skipping scheduled security tests because configuration is empty.");
            return;
        }
        log.info("Starting scheduled security tests.");
        config.services().forEach(this::executeTests);
        log.info("Scheduled security tests completed");
    }

    private void executeTests(String environmentKey, TestRunConfig.UrlServices environment) {
        environment.services().forEach(service -> {
            var request = new TestRequest();
            request.setId(UUID.randomUUID().toString());
            request.setUrl(environment.url());
            request.setService(service);

            try {
                var response = testService.execute(request);
                securityTestMetrics.incrementRequest(service, response.getStatus().name());
                log.info("Security test for environment '{}' and service '{}': {}", environmentKey, service,
                        response.getStatus());
            } catch (ServiceException ex) {
                securityTestMetrics.incrementRequest(service, "ERROR");
                log.error("Security test failed for environment '{}' and service '{}': {}", environmentKey, service,
                        ex.getMessage());
            } catch (Exception e) {
                securityTestMetrics.incrementRequest(service, "ERROR");
                log.error("Unexpected error during security test for environment '{}' and service '{}'", environmentKey,
                        service, e);
            }
        });
    }
}
