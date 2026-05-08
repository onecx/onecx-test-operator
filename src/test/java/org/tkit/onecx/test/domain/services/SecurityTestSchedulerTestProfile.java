package org.tkit.onecx.test.domain.services;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class SecurityTestSchedulerTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "onecx.test.scheduler.cron", "0 0/30 * * * ?",
                "onecx.test.scheduler.services.private.url", "https://private.example",
                "onecx.test.scheduler.services.private.services", "svc-p1,svc-p2",
                "onecx.test.scheduler.services.public.url", "https://public.example",
                "onecx.test.scheduler.services.public.services", "svc-u1,svc-u2");
    }
}
