package org.tkit.onecx.test.domain.services;

import java.util.List;
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

@ApplicationScoped
public class SecurityTestScheduler {

    private static final Logger log = LoggerFactory.getLogger(SecurityTestScheduler.class);

    @Inject
    TestService testService;

    @Inject
    SecurityTestMetrics securityTestMetrics;

    @Inject
    TestRunConfig config;

    @Scheduled(every = "{onecx.test.scheduler.timer}")
    void executeScheduledTests() {
        log.info("Starting scheduled security tests");

        config.services().forEach(this::executeEnvironment);

        log.info("Scheduled security tests completed");
    }

    private void executeEnvironment(TestRunConfig.UrlServices environment) {
        var url = environment.url();
        var services = environment.services();

        if (url.isEmpty() || services.isEmpty() || services.get().isEmpty()) {
            log.warn("Skipping environment due to missing url or services");
            return;
        }

        executeServices(url.get(), services.get());
    }

    private void executeServices(String url, List<String> services) {
        services.forEach(service -> executeTestForService(url, service));
    }

    private void executeTestForService(String url, String service) {
        var request = new TestRequest();
        request.setId(UUID.randomUUID().toString());
        request.setUrl(url);
        request.setService(service);

        try {
            var response = testService.execute(request);
            securityTestMetrics.incrementRequest(service, response.getStatus().name());
            log.info("Security test for service '{}': {}", service, response.getStatus());
        } catch (ServiceException ex) {
            securityTestMetrics.incrementRequest(service, "ERROR");
            log.error("Security test failed for service '{}': {}", service, ex.getMessage());
        }
    }
}
