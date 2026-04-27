package org.tkit.onecx.test.domain.clients;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Encoded;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

@Path("/")
public interface SpringBootAdminClient {

    @GET
    @Path("swagger-ui/index.html")
    Response getSwaggerUi();

    @GET
    @Path("swagger-ui/swagger-initializer.js")
    Response getSwaggerInitializer();

    /**
     * Springdoc standard endpoint – returns JSON with the actual OpenAPI spec URL(s).
     * Example: {"configUrl":"/v3/api-docs/swagger-config","url":"/v3/api-docs","urls":[...]}
     */
    @GET
    @Path("v3/api-docs/swagger-config")
    @Consumes("application/json")
    Response getSwaggerConfig();

    /**
     * Fetches any resource by resolved path (used after discovering the real OpenAPI URL from swagger-config).
     */
    @GET
    @Path("{path: .+}")
    Response getResource(@Encoded @PathParam("path") String path);
}
