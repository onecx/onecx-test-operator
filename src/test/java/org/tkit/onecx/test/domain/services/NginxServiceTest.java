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

        assertThat(result)
                .hasSize(5)
                .allMatch(pc -> !pc.getLocation().startsWith("@"))
                .allMatch(pc -> "http://test-bff-app".equals(pc.getProxyHost()));

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

    @Test
    void getProxyPassLocation_skipsInvalidLocationBlocksForAllContinueBranches() {
        var config = """
                server {
                location /no-proxy {
                return 200 "ok\\n";
                }
                location /bad-path{
                proxy_pass http://test-bff-app/api/ignored;
                }
                location @named {
                proxy_pass http://test-bff-app/api/ignored;
                }
                location /missing-proxy-value {
                proxy_pass;
                }
                location /non-http-upstream {
                proxy_pass $upstream;
                }
                location /valid {
                proxy_pass http://test-bff-app/api/valid;
                }
                }
                """;

        var result = nginxService.getProxyPassLocation(config);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLocation()).isEqualTo("/valid");
        assertThat(result.get(0).getProxyHost()).isEqualTo("http://test-bff-app");
        assertThat(result.get(0).getProxyPath()).isEqualTo("/api/valid");
        assertThat(result.get(0).getServicePathKey()).isEqualTo("/api/valid");
    }

    @Test
    void getProxyPassLocation_ignoresUnclosedLocationBlock() {
        var config = """
                server {
                location /valid {
                proxy_pass http://test-bff-app/api/valid;
                }
                location /broken {
                proxy_pass http://test-bff-app/api/broken;
                """;

        var result = nginxService.getProxyPassLocation(config);

        // The unclosed block keeps depth > 0 until EOF and must be ignored.
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLocation()).isEqualTo("/valid");
    }

    @Test
    void getProxyPassLocation_normalizesRegexAndExactLocationModifiers() {
        var config = """
                server {
                location = /exact {
                proxy_pass http://test-bff-app/api/exact;
                }
                location ~ /regex {
                proxy_pass http://test-bff-app/api/regex;
                }
                location ~* /regex-insensitive {
                proxy_pass http://test-bff-app/api/regex-insensitive;
                }
                location ^~ /prefix {
                proxy_pass http://test-bff-app/api/prefix;
                }
                }
                """;

        var result = nginxService.getProxyPassLocation(config);

        assertThat(result).hasSize(4);
        assertThat(result).extracting("location")
                .containsExactlyInAnyOrder("/exact", "/regex", "/regex-insensitive", "/prefix");
    }

    @Test
    void getProxyPassLocation_handlesNestedBracesInsideLocationBlock() {
        var config = """
                server {
                location /nested {
                if ($request_method = OPTIONS) {
                add_header X-Test "yes";
                }
                proxy_pass http://test-bff-app/api/nested;
                }
                }
                """;

        var result = nginxService.getProxyPassLocation(config);

        // Verifies the inner closing brace (depth != 0) does not prematurely end the location block.
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLocation()).isEqualTo("/nested");
        assertThat(result.get(0).getProxyPath()).isEqualTo("/api/nested");
    }

    @Test
    void getProxyPassLocation_handlesModifierOnlyLocations() {
        var config = """
                server {
                location = {
                proxy_pass http://test-bff-app:80;
                }
                location ~ {
                proxy_pass https://test-bff-app:443/;
                }
                location ~* {
                proxy_pass http://test-bff-app:8080/api/port;
                }
                location ^~ {
                proxy_pass http://test-bff-app;
                }
                }
                """;

        var result = nginxService.getProxyPassLocation(config);

        assertThat(result).hasSize(4);
        assertThat(result).extracting("location")
                .containsExactlyInAnyOrder("=", "~", "~*", "^~");
        assertThat(result).filteredOn(pc -> "=".equals(pc.getLocation())).singleElement()
                .satisfies(pc -> {
                    assertThat(pc.getProxyHost()).isEqualTo("http://test-bff-app");
                    assertThat(pc.getProxyPath()).isEqualTo("");
                    assertThat(pc.getServicePathKey()).isNull();
                });
        assertThat(result).filteredOn(pc -> "~".equals(pc.getLocation())).singleElement()
                .satisfies(pc -> {
                    assertThat(pc.getProxyHost()).isEqualTo("https://test-bff-app");
                    assertThat(pc.getProxyPath()).isEqualTo("");
                    assertThat(pc.getServicePathKey()).isNull();
                });
        assertThat(result).filteredOn(pc -> "~*".equals(pc.getLocation())).singleElement()
                .satisfies(pc -> {
                    assertThat(pc.getProxyHost()).isEqualTo("http://test-bff-app:8080");
                    assertThat(pc.getProxyPath()).isEqualTo("/api/port");
                    assertThat(pc.getServicePathKey()).isEqualTo("/api/port");
                });
        assertThat(result).filteredOn(pc -> "^~".equals(pc.getLocation())).singleElement()
                .satisfies(pc -> {
                    assertThat(pc.getProxyHost()).isEqualTo("http://test-bff-app");
                    assertThat(pc.getProxyPath()).isEqualTo("");
                    assertThat(pc.getServicePathKey()).isNull();
                });
    }

    @Test
    void getProxyPassLocation_setsNullHostAndEmptyPathWhenUriIsMalformed() {
        // http://[invalid passes all regex guards (PATTERN_PROXY_PASS and PATTERN_LOCATION_PROXY_PASS both match)
        // but URI.create() throws on the unclosed IPv6 literal, exercising the catch branches
        // in both getProxyHost (→ null) and getProxyPath (→ "").
        var config = """
                server {
                location /badhost {
                proxy_pass http://[invalid;
                }
                }
                """;

        var result = nginxService.getProxyPassLocation(config);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLocation()).isEqualTo("/badhost");
        assertThat(result.get(0).getProxyHost()).isNull();
        assertThat(result.get(0).getProxyPath()).isEqualTo("");
        assertThat(result.get(0).getServicePathKey()).isNull();
    }
}
