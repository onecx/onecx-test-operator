package org.tkit.onecx.test.domain.services;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class NginxServiceTest {

    @Inject
    NginxService nginxService;

    @Test
    void getProxyPassLocation_extractsForwardedLocationsAndServicePathKeys() {
        var config = """
                server {
                location /test-ui/api/a4ResourceOrderBff/v1 {
                proxy_pass http://test-bff-app/api/a4ResourceOrderBff/v1;
                }
                location /test-ui/party/v3/organizations {
                proxy_pass http://test-bff-app/party/v3/organizations;
                }
                location /test-ui/api/bff-proxy/portal-menu {
                proxy_pass http://test-bff-app/api/bff-proxy/portal-menu;
                }
                location /test-ui/bff/api/bff-proxy/applications/test-ui/permissions {
                proxy_pass http://test-bff-app/api/bff-proxy/applications/test-ui/permissions;
                }
                location /test-ui/web-resources {
                proxy_pass http://test-bff-app/web-resources;
                }
                location /test-ui/ {
                try_files $uri $uri/ /test-ui/index.html;
                }
                location /health {
                return 200 "healthy\\n";
                }
                location /metrics {
                return 200 "no metrics implemented yet\\n";
                }
                location / {
                return 308 /test-ui/;
                }
                location @redirect_permanent {
                return 308 /test-ui/;
                }
                }
                """;

        var result = nginxService.getProxyPassLocation(config);

        assertThat(result).hasSize(5);
        assertThat(result).allMatch(pc -> !pc.getLocation().startsWith("@"));
        assertThat(result).allMatch(pc -> "http://test-bff-app".equals(pc.getProxyHost()));

        assertThat(result)
                .filteredOn(pc -> "/test-ui/web-resources".equals(pc.getLocation()))
                .singleElement()
                .extracting("servicePathKey")
                .isEqualTo("/web-resources");

        assertThat(result)
                .filteredOn(pc -> "/test-ui/api/bff-proxy/portal-menu".equals(pc.getLocation()))
                .singleElement()
                .extracting("servicePathKey")
                .isEqualTo("/api/bff-proxy/portal-menu");
    }
}
