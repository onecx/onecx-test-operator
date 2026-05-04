package org.tkit.onecx.test.domain.services;

import java.net.URI;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tkit.onecx.test.domain.clients.QuarkusAdminClient;
import org.tkit.onecx.test.domain.models.ServiceException;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

@ApplicationScoped
public class QuarkusService implements BackendService {

    private static final Logger log = LoggerFactory.getLogger(QuarkusService.class);

    public int invokeGeneric2xxEndpoint(String url) {
        log.info("Testing Quarkus 2xx endpoint {}", url);
        var client = createClient(url);
        try (var response = client.getHealth()) {
            return response.getStatus();
        } catch (Exception e) {
            log.error("Quarkus health check failed with url {}", url);
            return 500;
        }
    }

    public OpenAPI getOpenApi(String url) {
        try {
            var data = getOpenApiSchema(url);
            log.debug(data);
            return parse(data);
        } catch (Exception ex) {
            throw new ServiceException(ex);
        }
    }

    public String getOpenApiSchema(String url) {
        log.info("Get openapi schema from URL: {}", url);
        var client = createClient(url);
        try (var response = client.getOpenApiYaml()) {
            return response.readEntity(String.class);
        }
    }

    @Override
    public String resolveOpenApiPath(String url) {
        return "/q/openapi";
    }

    private QuarkusAdminClient createClient(String url) {
        return QuarkusRestClientBuilder
                .newBuilder()
                .baseUri(URI.create(url))
                .build(QuarkusAdminClient.class);
    }
}
