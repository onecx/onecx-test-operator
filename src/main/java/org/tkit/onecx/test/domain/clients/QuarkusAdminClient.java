package org.tkit.onecx.test.domain.clients;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

@Path("/q")
public interface QuarkusAdminClient {

    @GET
    @Path("openapi")
    @Consumes("application/yaml")
    Response getOpenApiYaml();

    @GET
    @Path("metrics")
    Response getMetrics();

    @GET
    @Path("health")
    Response getHealth();

    @GET
    @Path("swagger-ui")
    Response getSwaggerUi();
}
