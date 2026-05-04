package org.tkit.onecx.test.domain.services;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tkit.onecx.test.domain.clients.SpringBootAdminClient;
import org.tkit.onecx.test.domain.models.ServiceException;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

@ApplicationScoped
public class SpringBootService implements BackendService {

    private static final Logger log = LoggerFactory.getLogger(SpringBootService.class);

    private static final String FALLBACK_OPENAPI_PATH = "v3/api-docs";

    /**
     * Matches the {@code configUrl:} assignment inside {@code swagger-initializer.js}, e.g.:
     *
     * <pre>
     * configUrl: "/api-docs/swagger-config"
     * </pre>
     */
    private static final Pattern INITIALIZER_CONFIG_URL_PATTERN = Pattern.compile(
            "[\"']?configUrl[\"']?\\s*:\\s*[\"']([^\"']+)[\"']");

    /**
     * Matches a direct {@code url:} assignment (fallback for setups that embed the spec URL
     * directly in the initializer rather than via a config endpoint).
     */
    private static final Pattern INITIALIZER_URL_PATTERN = Pattern.compile(
            "\\burl\\s*:\\s*[\"']([^\"']+)[\"']");

    @Inject
    ObjectMapper objectMapper;

    @Override
    public int invokeGeneric2xxEndpoint(String url) {
        log.info("Testing SpringBoot endpoint {}", url);
        var client = createClient(url);
        try (var response = client.getSwaggerUi()) {
            return response.getStatus();
        } catch (Exception e) {
            log.error("SpringBoot swagger-ui check failed with url {}", url);
            return 500;
        }
    }

    @Override
    public OpenAPI getOpenApi(String url) {
        try {
            var data = getOpenApiSchema(url);
            return parse(data);
        } catch (Exception ex) {
            throw new ServiceException("Exception parsing or getting openApi schema, url %s".formatted(url), ex);
        }
    }

    @Override
    public String getOpenApiSchema(String url) {
        log.info("Get openapi schema from URL: {}", url);
        var client = createClient(url);
        String openApiPath = resolveOpenApiPath(client, url);
        log.info("Resolved OpenAPI path: {}", openApiPath);
        try (var response = client.getResource(openApiPath)) {
            return response.readEntity(String.class);
        }
    }

    /**
     * Discovers the actual OpenAPI spec path using four strategies in order:
     * <ol>
     * <li><b>/api-docs/swagger-config directly</b> — first attempt; covers swagger-first setups
     * with dynamic paths like {@code /swagger-first/a4-resource-order-bff.yaml}.</li>
     * <li><b>swagger-initializer.js → configUrl → swagger-config JSON</b> — fetches the static
     * initializer JS, extracts {@code configUrl}, fetches that JSON endpoint, then reads
     * {@code urls[0].url} or {@code url} from it.</li>
     * <li><b>/v3/api-docs/swagger-config directly</b> — fallback for springdoc code-first
     * setups where the initializer is not present or has no {@code configUrl}.</li>
     * <li>Static fallback: {@value #FALLBACK_OPENAPI_PATH}</li>
     * </ol>
     */
    public String resolveOpenApiPath(String url) {
        var client = createClient(url);
        return resolveOpenApiPath(client, url);
    }

    // Package scope for deterministic tests without reflection.
    String resolveOpenApiPath(SpringBootAdminClient client, String baseUrl) {

        // 1. Try the well-known /api-docs/swagger-config endpoint directly (swagger-first setups).
        var resolved = resolveFromSwaggerConfig(client, "/api-docs/swagger-config");
        if (resolved != null) {
            return resolved;
        }

        // 2. Fetch swagger-initializer.js to find the configUrl (or a direct url as secondary).
        var resolvedFromInitializer = resolveFromInitializer(client, baseUrl);
        if (resolvedFromInitializer != null) {
            return resolvedFromInitializer;
        }

        // 3. Try the well-known springdoc swagger-config endpoint directly.
        var resolvedFromV3 = resolveFromSwaggerConfig(client, "/v3/api-docs/swagger-config");
        if (resolvedFromV3 != null) {
            return resolvedFromV3;
        }

        // 4. Static fallback.
        log.warn("Could not dynamically resolve OpenAPI path for {} — falling back to {}", baseUrl, FALLBACK_OPENAPI_PATH);
        return FALLBACK_OPENAPI_PATH;
    }

    private String resolveFromInitializer(SpringBootAdminClient client, String baseUrl) {
        Response response = null;
        try {
            response = client.getSwaggerInitializer();
            if (response.getStatus() != 200) {
                return null;
            }

            var js = response.readEntity(String.class);
            log.info("Fetched swagger-initializer.js from {} ({} )", baseUrl, js);

            var configMatcher = INITIALIZER_CONFIG_URL_PATTERN.matcher(js);
            if (configMatcher.find()) {
                var configUrl = configMatcher.group(1);
                log.info("Found configUrl in swagger-initializer.js: {}", configUrl);
                var resolvedFromInitializer = resolveFromSwaggerConfig(client, configUrl);
                if (resolvedFromInitializer != null) {
                    return resolvedFromInitializer;
                }
            } else {
                log.info("No configUrl found in swagger-initializer.js for {}", baseUrl);
            }

            var urlMatcher = INITIALIZER_URL_PATTERN.matcher(js);
            if (urlMatcher.find()) {
                var directUrlFromInitializer = stripLeadingSlash(urlMatcher.group(1));
                log.info("Resolved OpenAPI path from swagger-initializer.js (direct url): {}", directUrlFromInitializer);
                return directUrlFromInitializer;
            }
        } catch (Exception ex) {
            log.info("Could not fetch swagger-initializer.js for {}: {}", baseUrl, ex.getMessage());
        } finally {
            closeResponseQuietly(response, "swagger-initializer.js", baseUrl);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    // Package scope for deterministic tests without reflection.
    String resolveFromSwaggerConfig(SpringBootAdminClient client, String configUrl) {
        Response response = null;
        try {
            response = client.getResource(stripLeadingSlash(configUrl));
            if (response.getStatus() != 200) {
                return null;
            }
            Map<String, Object> config = objectMapper.readValue(response.readEntity(String.class), Map.class);

            var urls = (List<Map<String, String>>) config.get("urls");
            if (urls != null && !urls.isEmpty()) {
                var firstUrl = urls.get(0).get("url");
                if (firstUrl != null) {
                    log.info("Resolved OpenAPI path from {} (urls[0]): {}", configUrl, firstUrl);
                    return stripLeadingSlash(firstUrl);
                }
            }
            var specUrl = (String) config.get("url");
            if (specUrl != null) {
                log.info("Resolved OpenAPI path from {} (url): {}", configUrl, specUrl);
                return stripLeadingSlash(specUrl);
            }
        } catch (Exception ex) {
            log.debug("Could not resolve OpenAPI path from {}: {}", configUrl, ex.getMessage());
        } finally {
            closeResponseQuietly(response, "swagger-config", configUrl);
        }
        return null;
    }

    private static String stripLeadingSlash(String path) {
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private void closeResponseQuietly(Response response, String resourceName, String context) {
        if (response == null) {
            return;
        }
        try {
            response.close();
        } catch (Exception ex) {
            log.debug("Could not close {} response for {}: {}", resourceName, context, ex.getMessage());
        }
    }

    private SpringBootAdminClient createClient(String url) {
        return QuarkusRestClientBuilder
                .newBuilder()
                .baseUri(URI.create(url))
                .build(SpringBootAdminClient.class);
    }
}
