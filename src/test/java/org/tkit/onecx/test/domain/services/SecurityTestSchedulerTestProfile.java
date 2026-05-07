package org.tkit.onecx.test.domain.services;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class SecurityTestSchedulerTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "onecx.test.scheduler.services[0].url", "https://private.example",
                "onecx.test.scheduler.services[0].services", "svc-p1,svc-p2",
                "onecx.test.scheduler.services[1].url", "https://public.example",
                "onecx.test.scheduler.services[1].services", "svc-u1,svc-u2",
                "onecx.test.scheduler.services[2].url", "https://public.example",
                "onecx.test.scheduler.services[2].services", "",
                "onecx.test.scheduler.services[3].url", "",
                "onecx.test.scheduler.services[3].services", "svc-skipped");
    }
}
