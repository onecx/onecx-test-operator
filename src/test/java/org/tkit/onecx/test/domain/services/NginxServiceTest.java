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
                location /test-ui/api/testBff/v1 {
                proxy_pass http://test-bff-app/api/testBff/v1;
                }
                location /test-ui/testp/v3/testo {
                proxy_pass http://test-bff-app/testp/v3/testo;
                }
                location /test-ui/api/bff-proxy/p-menu {
                proxy_pass http://test-bff-app/api/bff-proxy/p-menu;
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
                .filteredOn(pc -> "/test-ui/api/bff-proxy/p-menu".equals(pc.getLocation()))
                .singleElement()
                .extracting("servicePathKey")
                .isEqualTo("/api/bff-proxy/p-menu");
    }
}
