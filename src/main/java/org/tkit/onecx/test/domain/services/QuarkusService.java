package org.tkit.onecx.test.domain.services;

import java.io.ByteArrayInputStream;
import java.net.URI;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tkit.onecx.test.domain.clients.QuarkusAdminClient;
import org.tkit.onecx.test.domain.models.ServiceException;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.smallrye.openapi.runtime.io.Format;
import io.smallrye.openapi.runtime.io.OpenApiParser;

@ApplicationScoped
public class QuarkusService {

    private static final Logger log = LoggerFactory.getLogger(QuarkusService.class);

    public int testQuarkusEndpoint(String url) {
        log.info("Testing Quarkus endpoint {}", url);
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
            return OpenApiParser.parse(new ByteArrayInputStream(data.getBytes()), Format.YAML);
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

    private QuarkusAdminClient createClient(String url) {
        return QuarkusRestClientBuilder
                .newBuilder()
                .baseUri(URI.create(url))
                .build(QuarkusAdminClient.class);
    }
}
