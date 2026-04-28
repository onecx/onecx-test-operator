package org.tkit.onecx.test.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockserver.model.MediaType;
import org.tkit.onecx.test.domain.clients.SpringBootAdminClient;
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

    // ─── Coverage: Line 111 - swagger-initializer.js returns non-200 status ─────

    @Test
    void resolveOpenApiPath_skipsInitializerWhenStatusNot200() {
        var mockUrl = mockUrl();

        // Strategy 1 fails
        addExpectation(mockServerClient
                .when(request().withPath("/api-docs/swagger-config").withMethod(HttpMethod.GET))
                .respond(response().withStatusCode(Response.Status.NOT_FOUND.getStatusCode())));

        // Strategy 2: initializer returns non-200 status (e.g., 404)
        addExpectation(mockServerClient
                .when(request().withPath("/swagger-ui/swagger-initializer.js").withMethod(HttpMethod.GET))
                .respond(response().withStatusCode(Response.Status.NOT_FOUND.getStatusCode())));

        // Skips strategy 2, proceeds to strategy 3
        addExpectation(mockServerClient
                .when(request().withPath("/v3/api-docs/swagger-config").withMethod(HttpMethod.GET))
                .respond(response()
                        .withStatusCode(Response.Status.OK.getStatusCode())
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody("{\"url\":\"/v3/api-docs\"}")));

        var result = springBootService.resolveOpenApiPath(mockUrl);

        assertThat(result).isEqualTo("v3/api-docs");
    }

    @Test
    void resolveOpenApiPath_skipsInitializerWhenStatus203() {
        var mockUrl = mockUrl();

        addExpectation(mockServerClient
                .when(request().withPath("/api-docs/swagger-config").withMethod(HttpMethod.GET))
                .respond(response().withStatusCode(Response.Status.NOT_FOUND.getStatusCode())));

        // Explicit non-200 branch for: if (response.getStatus() == 200)
        addExpectation(mockServerClient
                .when(request().withPath("/swagger-ui/swagger-initializer.js").withMethod(HttpMethod.GET))
                .respond(response().withStatusCode(203)));

        addExpectation(mockServerClient
                .when(request().withPath("/v3/api-docs/swagger-config").withMethod(HttpMethod.GET))
                .respond(response()
                        .withStatusCode(Response.Status.OK.getStatusCode())
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody("{\"url\":\"/v3/api-docs\"}")));

        var result = springBootService.resolveOpenApiPath(mockUrl);

        assertThat(result).isEqualTo("v3/api-docs");
    }

    // ─── Coverage: Line 121 - resolveFromInitializer returns null ───────────────

    @Test
    void resolveOpenApiPath_skipsInitializerConfigUrlWhenReturnsNull() {
        var mockUrl = mockUrl();

        // Strategy 1 fails
        addExpectation(mockServerClient
                .when(request().withPath("/api-docs/swagger-config").withMethod(HttpMethod.GET))
                .respond(response().withStatusCode(Response.Status.NOT_FOUND.getStatusCode())));

        // Strategy 2a: initializer has configUrl but config endpoint returns empty/invalid data
        addExpectation(mockServerClient
                .when(request().withPath("/swagger-ui/swagger-initializer.js").withMethod(HttpMethod.GET))
                .respond(response()
                        .withStatusCode(Response.Status.OK.getStatusCode())
                        .withBody(
                                "window.onload = function() { SwaggerUIBundle({ configUrl: \"/custom/swagger-config\" }); }")));

        // configUrl endpoint returns 200 but no valid url/urls data (returns null from resolveFromSwaggerConfig)
        addExpectation(mockServerClient
                .when(request().withPath("/custom/swagger-config").withMethod(HttpMethod.GET))
                .respond(response()
                        .withStatusCode(Response.Status.OK.getStatusCode())
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody("{}")));

        // Falls back to strategy 3 after resolvedFromInitializer is null
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

    // ─── Coverage: Line 129-131 - no direct URL pattern found in initializer ────

    @Test
    void resolveOpenApiPath_skipsDirectUrlWhenPatternNotFoundInInitializer() {
        var mockUrl = mockUrl();

        // Strategy 1 fails
        addExpectation(mockServerClient
                .when(request().withPath("/api-docs/swagger-config").withMethod(HttpMethod.GET))
                .respond(response().withStatusCode(Response.Status.NOT_FOUND.getStatusCode())));

        // Strategy 2b: initializer has no configUrl and no direct url pattern
        addExpectation(mockServerClient
                .when(request().withPath("/swagger-ui/swagger-initializer.js").withMethod(HttpMethod.GET))
                .respond(response()
                        .withStatusCode(Response.Status.OK.getStatusCode())
                        .withBody("window.onload = function() { SwaggerUIBundle({ }); }")));

        // Falls back to strategy 3 since no patterns were found
        addExpectation(mockServerClient
                .when(request().withPath("/v3/api-docs/swagger-config").withMethod(HttpMethod.GET))
                .respond(response()
                        .withStatusCode(Response.Status.OK.getStatusCode())
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody("{\"url\":\"/v3/api-docs\"}")));

        var result = springBootService.resolveOpenApiPath(mockUrl);

        assertThat(result).isEqualTo("v3/api-docs");
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

    // ─── Coverage: Line 157-158 - swagger config endpoint returns non-200 ────

    @Test
    void resolveOpenApiPath_skipsSwaggerConfigWhenStatusNot200() {
        var mockUrl = mockUrl();

        // Strategies 1 and 2 fail
        addExpectation(mockServerClient
                .when(request().withPath("/api-docs/swagger-config").withMethod(HttpMethod.GET))
                .respond(response().withStatusCode(Response.Status.NOT_FOUND.getStatusCode())));

        addExpectation(mockServerClient
                .when(request().withPath("/swagger-ui/swagger-initializer.js").withMethod(HttpMethod.GET))
                .respond(response().withStatusCode(Response.Status.NOT_FOUND.getStatusCode())));

        // Strategy 3: swagger config endpoint returns non-200 (e.g., 500)
        addExpectation(mockServerClient
                .when(request().withPath("/v3/api-docs/swagger-config").withMethod(HttpMethod.GET))
                .respond(response().withStatusCode(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode())));

        // Falls back to static fallback
        var result = springBootService.resolveOpenApiPath(mockUrl);

        assertThat(result).isEqualTo("v3/api-docs");
    }

    @Test
    void resolveOpenApiPath_skipsApiDocsSwaggerConfigWhenStatus203() {
        var mockUrl = mockUrl();

        // Strategy 1: explicit non-200 branch in resolveFromSwaggerConfig
        addExpectation(mockServerClient
                .when(request().withPath("/api-docs/swagger-config").withMethod(HttpMethod.GET))
                .respond(response().withStatusCode(203)));

        // Continue with next strategies
        addExpectation(mockServerClient
                .when(request().withPath("/swagger-ui/swagger-initializer.js").withMethod(HttpMethod.GET))
                .respond(response().withStatusCode(Response.Status.NOT_FOUND.getStatusCode())));

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

    @Test
    void resolveOpenApiPath_skipsInitializerConfigUrlWhenStatusNot200() {
        var mockUrl = mockUrl();

        addExpectation(mockServerClient
                .when(request().withPath("/api-docs/swagger-config").withMethod(HttpMethod.GET))
                .respond(response().withStatusCode(Response.Status.NOT_FOUND.getStatusCode())));

        addExpectation(mockServerClient
                .when(request().withPath("/swagger-ui/swagger-initializer.js").withMethod(HttpMethod.GET))
                .respond(response()
                        .withStatusCode(Response.Status.OK.getStatusCode())
                        .withBody(
                                "window.onload = function() { SwaggerUIBundle({ configUrl: \"/custom/swagger-config\" }); }")));

        addExpectation(mockServerClient
                .when(request().withPath("/custom/swagger-config").withMethod(HttpMethod.GET))
                .respond(response().withStatusCode(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode())));

        addExpectation(mockServerClient
                .when(request().withPath("/v3/api-docs/swagger-config").withMethod(HttpMethod.GET))
                .respond(response()
                        .withStatusCode(Response.Status.OK.getStatusCode())
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody("{\"url\":\"/v3/api-docs\"}")));

        var result = springBootService.resolveOpenApiPath(mockUrl);

        assertThat(result).isEqualTo("v3/api-docs");
    }

    @Test
    void resolveOpenApiPath_resolvedFromApiDocsSwaggerConfigWhenUrlsEmptyAndUrlPresent() {
        var mockUrl = mockUrl();

        addExpectation(mockServerClient
                .when(request().withPath("/api-docs/swagger-config").withMethod(HttpMethod.GET))
                .respond(response()
                        .withStatusCode(Response.Status.OK.getStatusCode())
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody("{\"urls\":[],\"url\":\"/v3/api-docs\"}")));

        var result = springBootService.resolveOpenApiPath(mockUrl);

        assertThat(result).isEqualTo("v3/api-docs");
    }

    @Test
    void resolveOpenApiPath_resolvedFromApiDocsSwaggerConfigWhenFirstUrlIsNull() {
        var mockUrl = mockUrl();

        addExpectation(mockServerClient
                .when(request().withPath("/api-docs/swagger-config").withMethod(HttpMethod.GET))
                .respond(response()
                        .withStatusCode(Response.Status.OK.getStatusCode())
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody("{\"urls\":[{}],\"url\":\"/v3/api-docs\"}")));

        var result = springBootService.resolveOpenApiPath(mockUrl);

        assertThat(result).isEqualTo("v3/api-docs");
    }

    @Test
    void resolveOpenApiPath_fallsThroughWhenSpecUrlIsNull() {
        var mockUrl = mockUrl();

        // Strategy 1 returns valid JSON but specUrl is null, so resolveFromSwaggerConfig returns null (no exception).
        addExpectation(mockServerClient
                .when(request().withPath("/api-docs/swagger-config").withMethod(HttpMethod.GET))
                .respond(response()
                        .withStatusCode(Response.Status.OK.getStatusCode())
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody("{\"urls\":[],\"url\":null}")));

        addExpectation(mockServerClient
                .when(request().withPath("/swagger-ui/swagger-initializer.js").withMethod(HttpMethod.GET))
                .respond(response().withStatusCode(Response.Status.NOT_FOUND.getStatusCode())));

        addExpectation(mockServerClient
                .when(request().withPath("/v3/api-docs/swagger-config").withMethod(HttpMethod.GET))
                .respond(response()
                        .withStatusCode(Response.Status.OK.getStatusCode())
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody("{\"url\":\"/v3/api-docs\"}")));

        var result = springBootService.resolveOpenApiPath(mockUrl);

        assertThat(result).isEqualTo("v3/api-docs");
    }

    @Test
    void resolveOpenApiPath_resolvedFromInitializerConfigUrlWithoutLeadingSlash() {
        var mockUrl = mockUrl();

        addExpectation(mockServerClient
                .when(request().withPath("/api-docs/swagger-config").withMethod(HttpMethod.GET))
                .respond(response().withStatusCode(Response.Status.NOT_FOUND.getStatusCode())));

        addExpectation(mockServerClient
                .when(request().withPath("/swagger-ui/swagger-initializer.js").withMethod(HttpMethod.GET))
                .respond(response()
                        .withStatusCode(Response.Status.OK.getStatusCode())
                        .withBody(
                                "window.onload = function() { SwaggerUIBundle({ configUrl: \"custom/swagger-config\" }); }")));

        addExpectation(mockServerClient
                .when(request().withPath("/custom/swagger-config").withMethod(HttpMethod.GET))
                .respond(response()
                        .withStatusCode(Response.Status.OK.getStatusCode())
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody("{\"url\":\"v3/api-docs\"}")));

        var result = springBootService.resolveOpenApiPath(mockUrl);

        assertThat(result).isEqualTo("v3/api-docs");
    }

    @Test
    void resolveOpenApiPath_catchesExceptionWhenFetchingInitializer_withoutReflection() {
        // Unreachable host triggers exceptions in initializer fetch path (and falls back).
        var unreachableUrl = "http://unknown-host.invalid";
        var result = springBootService.resolveOpenApiPath(unreachableUrl);

        assertThat(result).isEqualTo("v3/api-docs");
    }

    @Test
    void resolveOpenApiPath_catchesExceptionWhenSwaggerConfigEndpointIsUnreachable_withoutReflection() {
        // This drives resolveFromSwaggerConfig catch(Exception ex) via transport failure in getResource(...).
        var unreachableUrl = "http://unknown-host.invalid";

        var result = springBootService.resolveOpenApiPath(unreachableUrl);

        assertThat(result).isEqualTo("v3/api-docs");
    }

    @Test
    void resolveOpenApiPath_catchesExceptionWhenSwaggerConfigBodyIsInvalid_withoutReflection() {
        var mockUrl = mockUrl();

        // Invalid JSON triggers resolveFromSwaggerConfig catch(Exception ex) on strategy 1.
        addExpectation(mockServerClient
                .when(request().withPath("/api-docs/swagger-config").withMethod(HttpMethod.GET))
                .respond(response()
                        .withStatusCode(Response.Status.OK.getStatusCode())
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody("{ not-json }")));

        addExpectation(mockServerClient
                .when(request().withPath("/swagger-ui/swagger-initializer.js").withMethod(HttpMethod.GET))
                .respond(response().withStatusCode(Response.Status.NOT_FOUND.getStatusCode())));

        addExpectation(mockServerClient
                .when(request().withPath("/v3/api-docs/swagger-config").withMethod(HttpMethod.GET))
                .respond(response()
                        .withStatusCode(Response.Status.OK.getStatusCode())
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody("{\"url\":\"/v3/api-docs\"}")));

        var result = springBootService.resolveOpenApiPath(mockUrl);

        assertThat(result).isEqualTo("v3/api-docs");
    }

    @Test
    void resolveOpenApiPath_catchesExceptionWhenInitializerConfigUrlReturnsInvalidJson_withoutReflection() {
        var mockUrl = mockUrl();

        addExpectation(mockServerClient
                .when(request().withPath("/api-docs/swagger-config").withMethod(HttpMethod.GET))
                .respond(response().withStatusCode(Response.Status.NOT_FOUND.getStatusCode())));

        // Force strategy 2 to call resolveFromSwaggerConfig with a custom configUrl.
        addExpectation(mockServerClient
                .when(request().withPath("/swagger-ui/swagger-initializer.js").withMethod(HttpMethod.GET))
                .respond(response()
                        .withStatusCode(Response.Status.OK.getStatusCode())
                        .withBody(
                                "window.onload = function() { SwaggerUIBundle({ configUrl: \"/broken/swagger-config\" }); }")));

        // Invalid JSON deterministically triggers catch(Exception ex) in resolveFromSwaggerConfig.
        addExpectation(mockServerClient
                .when(request().withPath("/broken/swagger-config").withMethod(HttpMethod.GET))
                .respond(response()
                        .withStatusCode(Response.Status.OK.getStatusCode())
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody("}{")));

        addExpectation(mockServerClient
                .when(request().withPath("/v3/api-docs/swagger-config").withMethod(HttpMethod.GET))
                .respond(response()
                        .withStatusCode(Response.Status.OK.getStatusCode())
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody("{\"url\":\"/v3/api-docs\"}")));

        var result = springBootService.resolveOpenApiPath(mockUrl);

        assertThat(result).isEqualTo("v3/api-docs");
    }

    @Test
    void resolveOpenApiPath_reachesDirectUrlAssignmentAndExecutesCatch_withoutReflection() {
        var mockUrl = mockUrl();

        // 1) status=200 initializer with direct url -> hits directUrlFromInitializer = urlMatcher.group(1)
        addExpectation(mockServerClient
                .when(request().withPath("/api-docs/swagger-config").withMethod(HttpMethod.GET))
                .respond(response().withStatusCode(Response.Status.NOT_FOUND.getStatusCode())));

        addExpectation(mockServerClient
                .when(request().withPath("/swagger-ui/swagger-initializer.js").withMethod(HttpMethod.GET))
                .respond(response()
                        .withStatusCode(Response.Status.OK.getStatusCode())
                        .withBody("window.onload = function() { SwaggerUIBundle({ url: \"/custom/api-docs.yaml\" }); }")));

        var directUrlResult = springBootService.resolveOpenApiPath(mockUrl);
        assertThat(directUrlResult).isEqualTo("custom/api-docs.yaml");

        // 2) force initializer exception on unreachable host -> executes catch(Exception ex) path
        clearExpectation(mockServerClient);

        var unreachableUrl = "http://unknown-host.invalid";
        var catchResult = springBootService.resolveOpenApiPath(unreachableUrl);
        assertThat(catchResult).isEqualTo("v3/api-docs");
    }

    @Test
    void resolveOpenApiPath_catchesExceptionWhenGetSwaggerInitializerThrows_withoutReflection() {
        SpringBootAdminClient client = new SpringBootAdminClient() {
            @Override
            public Response getSwaggerUi() {
                return Response.ok().build();
            }

            @Override
            public Response getSwaggerInitializer() {
                throw new RuntimeException("initializer call failed");
            }

            @Override
            public Response getSwaggerConfig() {
                return Response.status(Response.Status.NOT_FOUND).build();
            }

            @Override
            public Response getResource(String path) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        };

        var result = springBootService.resolveOpenApiPath(client, "http://base-url");

        assertThat(result).isEqualTo("v3/api-docs");
    }

    @Test
    void resolveOpenApiPath_catchesExceptionWhenInitializerReadEntityThrows_withoutReflection() {
        Response initializerResponse = Mockito.mock(Response.class);
        Mockito.when(initializerResponse.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
        Mockito.when(initializerResponse.readEntity(String.class)).thenThrow(new RuntimeException("read failed"));

        SpringBootAdminClient client = new SpringBootAdminClient() {
            @Override
            public Response getSwaggerUi() {
                return Response.ok().build();
            }

            @Override
            public Response getSwaggerInitializer() {
                return initializerResponse;
            }

            @Override
            public Response getSwaggerConfig() {
                return Response.status(Response.Status.NOT_FOUND).build();
            }

            @Override
            public Response getResource(String path) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        };

        var result = springBootService.resolveOpenApiPath(client, "http://base-url");

        assertThat(result).isEqualTo("v3/api-docs");
    }

    @Test
    void resolveOpenApiPath_catchesExceptionWhenInitializerReadEntityAndCloseThrow_withoutReflection() {
        Response initializerResponse = Mockito.mock(Response.class);
        Mockito.when(initializerResponse.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
        Mockito.when(initializerResponse.readEntity(String.class)).thenThrow(new RuntimeException("read failed"));
        Mockito.doThrow(new RuntimeException("close failed")).when(initializerResponse).close();

        SpringBootAdminClient client = new SpringBootAdminClient() {
            @Override
            public Response getSwaggerUi() {
                return Response.ok().build();
            }

            @Override
            public Response getSwaggerInitializer() {
                return initializerResponse;
            }

            @Override
            public Response getSwaggerConfig() {
                return Response.status(Response.Status.NOT_FOUND).build();
            }

            @Override
            public Response getResource(String path) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        };

        var result = springBootService.resolveOpenApiPath(client, "http://base-url");

        assertThat(result).isEqualTo("v3/api-docs");
        Mockito.verify(initializerResponse).close();
    }

    @Test
    void resolveFromSwaggerConfig_catchesExceptionWhenGetResourceThrows_withoutReflection() {
        SpringBootAdminClient client = new SpringBootAdminClient() {
            @Override
            public Response getSwaggerUi() {
                return Response.ok().build();
            }

            @Override
            public Response getSwaggerInitializer() {
                return Response.ok().build();
            }

            @Override
            public Response getSwaggerConfig() {
                return Response.ok().build();
            }

            @Override
            public Response getResource(String path) {
                throw new RuntimeException("resource failed");
            }
        };

        var result = springBootService.resolveFromSwaggerConfig(client, "/broken/swagger-config");

        assertThat(result).isNull();
    }

    @Test
    void resolveFromSwaggerConfig_catchesExceptionWhenReadEntityThrows_withoutReflection() {
        Response configResponse = Mockito.mock(Response.class);
        Mockito.when(configResponse.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
        Mockito.when(configResponse.readEntity(String.class)).thenThrow(new RuntimeException("entity failed"));

        SpringBootAdminClient client = new SpringBootAdminClient() {
            @Override
            public Response getSwaggerUi() {
                return Response.ok().build();
            }

            @Override
            public Response getSwaggerInitializer() {
                return Response.ok().build();
            }

            @Override
            public Response getSwaggerConfig() {
                return Response.ok().build();
            }

            @Override
            public Response getResource(String path) {
                return configResponse;
            }
        };

        var result = springBootService.resolveFromSwaggerConfig(client, "/broken/swagger-config");

        assertThat(result).isNull();
    }

    @Test
    void resolveFromSwaggerConfig_closesResponseOnSuccess_withoutReflection() {
        Response configResponse = Mockito.mock(Response.class);
        Mockito.when(configResponse.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
        Mockito.when(configResponse.readEntity(String.class)).thenReturn("{\"url\":\"/v3/api-docs\"}");

        SpringBootAdminClient client = new SpringBootAdminClient() {
            @Override
            public Response getSwaggerUi() {
                return Response.ok().build();
            }

            @Override
            public Response getSwaggerInitializer() {
                return Response.ok().build();
            }

            @Override
            public Response getSwaggerConfig() {
                return Response.ok().build();
            }

            @Override
            public Response getResource(String path) {
                return configResponse;
            }
        };

        var result = springBootService.resolveFromSwaggerConfig(client, "/working/swagger-config");

        assertThat(result).isEqualTo("v3/api-docs");
        Mockito.verify(configResponse).close();
    }

    @Test
    void resolveFromSwaggerConfig_keepsResolvedValueWhenCloseThrows_withoutReflection() {
        Response configResponse = Mockito.mock(Response.class);
        Mockito.when(configResponse.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
        Mockito.when(configResponse.readEntity(String.class)).thenReturn("{\"url\":\"/v3/api-docs\"}");
        Mockito.doThrow(new RuntimeException("close failed")).when(configResponse).close();

        SpringBootAdminClient client = new SpringBootAdminClient() {
            @Override
            public Response getSwaggerUi() {
                return Response.ok().build();
            }

            @Override
            public Response getSwaggerInitializer() {
                return Response.ok().build();
            }

            @Override
            public Response getSwaggerConfig() {
                return Response.ok().build();
            }

            @Override
            public Response getResource(String path) {
                return configResponse;
            }
        };

        var result = springBootService.resolveFromSwaggerConfig(client, "/working/swagger-config");

        assertThat(result).isEqualTo("v3/api-docs");
        Mockito.verify(configResponse).close();
    }

    @SuppressWarnings("unchecked")
    @Test
    void resolveFromSwaggerConfig_catchesExceptionWhenObjectMapperThrows_withoutReflection() throws Exception {
        Response configResponse = Mockito.mock(Response.class);
        Mockito.when(configResponse.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
        Mockito.when(configResponse.readEntity(String.class)).thenReturn("{}");

        SpringBootAdminClient client = new SpringBootAdminClient() {
            @Override
            public Response getSwaggerUi() {
                return Response.ok().build();
            }

            @Override
            public Response getSwaggerInitializer() {
                return Response.ok().build();
            }

            @Override
            public Response getSwaggerConfig() {
                return Response.ok().build();
            }

            @Override
            public Response getResource(String path) {
                return configResponse;
            }
        };

        var originalMapper = springBootService.objectMapper;
        try {
            var failingMapper = Mockito.mock(com.fasterxml.jackson.databind.ObjectMapper.class);
            Mockito.when(failingMapper.readValue("{}", Map.class))
                    .thenThrow(new RuntimeException("mapper failed"));
            springBootService.objectMapper = failingMapper;

            var result = springBootService.resolveFromSwaggerConfig(client, "/broken/swagger-config");

            assertThat(result).isNull();
        } finally {
            springBootService.objectMapper = originalMapper;
        }
    }
}
