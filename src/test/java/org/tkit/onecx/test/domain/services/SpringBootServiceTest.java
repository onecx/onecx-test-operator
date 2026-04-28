package org.tkit.onecx.test.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import jakarta.inject.Inject;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.model.MediaType;
import org.tkit.onecx.test.domain.models.ServiceException;
import org.tkit.onecx.test.operator.AbstractTest;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class SpringBootServiceTest extends AbstractTest {

    @Inject
    SpringBootService springBootService;

    @BeforeEach
    void resetExpectation() {
        clearExpectation(mockServerClient);
    }

    // ─── resolveOpenApiPath: strategy 1 via urls[0].url ──────────────────────

    @Test
    void resolveOpenApiPath_resolvedFromApiDocsSwaggerConfigViaUrlsList() {
        var mockUrl = mockUrl();

        addExpectation(mockServerClient
                .when(request().withPath("/api-docs/swagger-config").withMethod(HttpMethod.GET))
                .respond(response()
                        .withStatusCode(Response.Status.OK.getStatusCode())
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody("{\"urls\":[{\"url\":\"/v3/api-docs/my-spec.yaml\"}]}")));

        var result = springBootService.resolveOpenApiPath(mockUrl);

        assertThat(result).isEqualTo("v3/api-docs/my-spec.yaml");
    }

    // ─── resolveOpenApiPath: strategy 1 via url field ────────────────────────

    @Test
    void resolveOpenApiPath_resolvedFromApiDocsSwaggerConfigViaUrlField() {
        var mockUrl = mockUrl();

        addExpectation(mockServerClient
                .when(request().withPath("/api-docs/swagger-config").withMethod(HttpMethod.GET))
                .respond(response()
                        .withStatusCode(Response.Status.OK.getStatusCode())
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody("{\"url\":\"/v3/api-docs\"}")));

        var result = springBootService.resolveOpenApiPath(mockUrl);

        assertThat(result).isEqualTo("v3/api-docs");
    }

    // ─── resolveOpenApiPath: strategy 2a via swagger-initializer.js → configUrl ──

    @Test
    void resolveOpenApiPath_resolvedFromSwaggerInitializerViaConfigUrl() {
        var mockUrl = mockUrl();

        // Strategy 1 fails
        addExpectation(mockServerClient
                .when(request().withPath("/api-docs/swagger-config").withMethod(HttpMethod.GET))
                .respond(response().withStatusCode(Response.Status.NOT_FOUND.getStatusCode())));

        // Strategy 2: initializer carries configUrl
        addExpectation(mockServerClient
                .when(request().withPath("/swagger-ui/swagger-initializer.js").withMethod(HttpMethod.GET))
                .respond(response()
                        .withStatusCode(Response.Status.OK.getStatusCode())
                        .withBody(
                                "window.onload = function() { SwaggerUIBundle({ configUrl: \"/v3/api-docs/swagger-config\" }); }")));

        // configUrl resolves to actual spec
        addExpectation(mockServerClient
                .when(request().withPath("/v3/api-docs/swagger-config").withMethod(HttpMethod.GET))
                .respond(response()
                        .withStatusCode(Response.Status.OK.getStatusCode())
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody("{\"url\":\"/v3/api-docs\"}")));

        var result = springBootService.resolveOpenApiPath(mockUrl);

        assertThat(result).isEqualTo("v3/api-docs");
    }

    // ─── resolveOpenApiPath: strategy 2b via swagger-initializer.js → direct url ──

    @Test
    void resolveOpenApiPath_resolvedFromSwaggerInitializerViaDirectUrl() {
        var mockUrl = mockUrl();

        // Strategy 1 fails
        addExpectation(mockServerClient
                .when(request().withPath("/api-docs/swagger-config").withMethod(HttpMethod.GET))
                .respond(response().withStatusCode(Response.Status.NOT_FOUND.getStatusCode())));

        // Strategy 2: initializer has direct url (no configUrl)
        addExpectation(mockServerClient
                .when(request().withPath("/swagger-ui/swagger-initializer.js").withMethod(HttpMethod.GET))
                .respond(response()
                        .withStatusCode(Response.Status.OK.getStatusCode())
                        .withBody("window.onload = function() { SwaggerUIBundle({ url: \"/custom/api-docs.yaml\" }); }")));

        var result = springBootService.resolveOpenApiPath(mockUrl);

        assertThat(result).isEqualTo("custom/api-docs.yaml");
    }

    // ─── resolveOpenApiPath: strategy 3 via /v3/api-docs/swagger-config ─────

    @Test
    void resolveOpenApiPath_resolvedFromV3ApiDocsSwaggerConfig() {
        var mockUrl = mockUrl();

        // Strategies 1 and 2 fail
        addExpectation(mockServerClient
                .when(request().withPath("/api-docs/swagger-config").withMethod(HttpMethod.GET))
                .respond(response().withStatusCode(Response.Status.NOT_FOUND.getStatusCode())));

        addExpectation(mockServerClient
                .when(request().withPath("/swagger-ui/swagger-initializer.js").withMethod(HttpMethod.GET))
                .respond(response().withStatusCode(Response.Status.NOT_FOUND.getStatusCode())));

        // Strategy 3 succeeds
        addExpectation(mockServerClient
                .when(request().withPath("/v3/api-docs/swagger-config").withMethod(HttpMethod.GET))
                .respond(response()
                        .withStatusCode(Response.Status.OK.getStatusCode())
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody("{\"url\":\"/v3/api-docs\"}")));

        var result = springBootService.resolveOpenApiPath(mockUrl);

        assertThat(result).isEqualTo("v3/api-docs");
    }

    // ─── resolveOpenApiPath: strategy 4 — static fallback ───────────────────

    @Test
    void resolveOpenApiPath_fallsBackToDefaultPath() {
        var mockUrl = mockUrl();

        // All three strategies fail
        addExpectation(mockServerClient
                .when(request().withPath("/api-docs/swagger-config").withMethod(HttpMethod.GET))
                .respond(response().withStatusCode(Response.Status.NOT_FOUND.getStatusCode())));

        addExpectation(mockServerClient
                .when(request().withPath("/swagger-ui/swagger-initializer.js").withMethod(HttpMethod.GET))
                .respond(response().withStatusCode(Response.Status.NOT_FOUND.getStatusCode())));

        addExpectation(mockServerClient
                .when(request().withPath("/v3/api-docs/swagger-config").withMethod(HttpMethod.GET))
                .respond(response().withStatusCode(Response.Status.NOT_FOUND.getStatusCode())));

        var result = springBootService.resolveOpenApiPath(mockUrl);

        assertThat(result).isEqualTo("v3/api-docs");
    }

    // ─── getOpenApi: ServiceException on parse failure ───────────────────────

    @Test
    void getOpenApi_throwsServiceExceptionWhenResponseIsUnparseable() {
        var mockUrl = mockUrl();

        // All swagger-config strategies fail → falls back to v3/api-docs
        addExpectation(mockServerClient
                .when(request().withPath("/api-docs/swagger-config").withMethod(HttpMethod.GET))
                .respond(response().withStatusCode(Response.Status.NOT_FOUND.getStatusCode())));

        addExpectation(mockServerClient
                .when(request().withPath("/swagger-ui/swagger-initializer.js").withMethod(HttpMethod.GET))
                .respond(response().withStatusCode(Response.Status.NOT_FOUND.getStatusCode())));

        addExpectation(mockServerClient
                .when(request().withPath("/v3/api-docs/swagger-config").withMethod(HttpMethod.GET))
                .respond(response().withStatusCode(Response.Status.NOT_FOUND.getStatusCode())));

        // v3/api-docs returns malformed JSON that causes parse() to throw
        addExpectation(mockServerClient
                .when(request().withPath("/v3/api-docs").withMethod(HttpMethod.GET))
                .respond(response()
                        .withStatusCode(Response.Status.OK.getStatusCode())
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody("{this is not valid json!!!")));

        assertThatThrownBy(() -> springBootService.getOpenApi(mockUrl))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(mockUrl);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private String mockUrl() {
        return ConfigProvider.getConfig().getValue("quarkus.mockserver.endpoint", String.class);
    }
}
