package org.tkit.onecx.test.operator.rs.v1.controllers;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.tkit.onecx.test.operator.rs.v1.controllers.TestRestController.CMD_CONFIG;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.JsonBody;
import org.mockserver.model.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tkit.onecx.test.domain.services.K8sExecService;
import org.tkit.onecx.test.operator.AbstractTest;

import gen.org.tkit.onecx.test.operator.rs.v1.model.SecurityTestRequestDTO;
import gen.org.tkit.onecx.test.operator.rs.v1.model.SecurityTestResponseDTO;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkiverse.mockserver.test.InjectMockServerClient;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;
import io.smallrye.openapi.api.models.OpenAPIImpl;
import io.smallrye.openapi.api.models.OperationImpl;
import io.smallrye.openapi.api.models.PathItemImpl;
import io.smallrye.openapi.api.models.PathsImpl;
import io.smallrye.openapi.api.models.servers.ServerImpl;

@QuarkusTest
@WithKubernetesTestServer
@TestHTTPEndpoint(TestRestController.class)
class TestRestControllerTest extends AbstractTest {

    private static final Logger log = LoggerFactory.getLogger(TestRestControllerTest.class);

    @Inject
    KubernetesClient client;

    @InjectMockServerClient
    MockServerClient mockServerClient;

    @InjectMock
    K8sExecService k8sExecService;

    private String mockServer() {
        return ConfigProvider.getConfig().getValue("quarkus.mockserver.endpoint", String.class);
    }

    @BeforeEach
    void before() {
        var selector = Map.of("app", "test1-ui");

        var service = new ServiceBuilder().withNewMetadata().withName("test1-ui").endMetadata()
                .withNewSpec().withSelector(selector).addNewPort()
                .withName("test-port")
                .withProtocol("TCP")
                .withPort(80)
                .withTargetPort(new IntOrString(80))
                .endPort()
                .withType("LoadBalancer")
                .endSpec()
                .build();

        var pod = new PodBuilder()
                .withNewMetadata().withName("test1-ui").withLabels(selector).endMetadata()
                .withSpec(
                        new PodSpecBuilder()
                                .withContainers(
                                        new ContainerBuilder()
                                                .withName("test1-ui")
                                                .withImage("nginx")
                                                .withPorts(List.of(new ContainerPortBuilder().withContainerPort(80).build()))
                                                .build())
                                .build())
                .build();

        var s = client.resource(service).create();
        log.info("Created service with name {}", s.getMetadata().getName());
        var p = client.resource(pod).create();
        log.info("Created pod with name {}", p.getMetadata().getName());

        Mockito.when(k8sExecService.execCommandOnPod(s.getMetadata().getName(), CMD_CONFIG))
                .thenReturn(createNginxConfig("/mfe/test/api", mockServer()));
    }

    private String createNginxConfig(String path, String proxy) {
        var tmp = """
                location = /error/ {
                alias    /usr/share/nginx/html/static/;
                    try_files $uri $uri/ = 404;
                }
                # configuration file /etc/nginx/conf.d/locations/locations.conf:
                location ${TEST_PATH} {
                    proxy_pass ${TEST_PROXY};
                    proxy_set_header Host            $host;
                    proxy_set_header X-Forwarded-For $remote_addr;
                }
                """;
        tmp = tmp.replace("${TEST_PATH}", path);
        tmp = tmp.replace("${TEST_PROXY}", proxy);
        return tmp;
    }

    @Test
    void runTest() {

        var a = new OpenAPIImpl();
        a.addServer(new ServerImpl().url("http://localhost:8080"))
                .paths(new PathsImpl().addPathItem("/test",
                        new PathItemImpl().GET(new OperationImpl().operationId("test-get"))));

        mockServerClient.when(request().withPath("/q/openapi").withMethod(HttpMethod.GET))
                .respond(httpRequest -> response().withStatusCode(Response.Status.OK.getStatusCode())
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody(JsonBody.json(a)));

        mockServerClient.when(request().withPath("/mfe/test/api/test").withMethod(HttpMethod.GET))
                .respond(httpRequest -> response().withStatusCode(Response.Status.FORBIDDEN.getStatusCode()));

        var request = new SecurityTestRequestDTO()
                .id(UUID.randomUUID().toString())
                .service("test1-ui")
                .url(mockServer());

        var response = given().when()
                .auth().oauth2(keycloakClient.getAccessToken(ADMIN))
                .header(APM_HEADER_PARAM, ADMIN)
                .body(request).contentType(APPLICATION_JSON)
                .post()
                .then()
                .log().all()
                .statusCode(OK.getStatusCode())
                .extract().as(SecurityTestResponseDTO.class);

    }
}
