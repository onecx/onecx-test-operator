package org.tkit.onecx.test.operator.rs.v1.controllers;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static jakarta.ws.rs.core.Response.Status.OK;
import static jakarta.ws.rs.core.Response.Status.UNAUTHORIZED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.microprofile.openapi.OASFactory.createOpenAPI;
import static org.eclipse.microprofile.openapi.OASFactory.createOperation;
import static org.eclipse.microprofile.openapi.OASFactory.createParameter;
import static org.eclipse.microprofile.openapi.OASFactory.createPathItem;
import static org.eclipse.microprofile.openapi.OASFactory.createPaths;
import static org.eclipse.microprofile.openapi.OASFactory.createServer;
import static org.mockito.ArgumentMatchers.any;

import java.util.Map;
import java.util.UUID;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.openapi.models.parameters.Parameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.tkit.onecx.test.domain.services.K8sExecService;
import org.tkit.onecx.test.operator.AbstractTest;
import org.tkit.onecx.test.operator.rs.v1.mappers.ExceptionMapper;
import org.tkit.quarkus.security.test.GenerateKeycloakClient;

import gen.org.tkit.onecx.test.operator.rs.v1.model.ExecutionStatusDTO;
import gen.org.tkit.onecx.test.operator.rs.v1.model.ProblemDetailResponseDTO;
import gen.org.tkit.onecx.test.operator.rs.v1.model.SecurityTestRequestDTO;
import gen.org.tkit.onecx.test.operator.rs.v1.model.SecurityTestResponseDTO;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Execable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.TtyExecErrorChannelable;
import io.fabric8.kubernetes.client.dsl.TtyExecErrorable;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;

@QuarkusTest
@WithKubernetesTestServer
@TestHTTPEndpoint(TestRestController.class)
@GenerateKeycloakClient(clientName = "admin", scopes = { "ocx-ts-sec:exec" })
class BaseRestControllerTest extends AbstractTest {

    // Why?: Mock, KubernetesMockServer does not support Vertx WebSocket
    // ref: https://github.com/fabric8io/kubernetes-client/discussions/5364
    @InjectMock
    K8sExecService k8sExecService;

    @BeforeEach
    void resetExpectation() {
        clearExpectation(mockServerClient);
    }

    @Test
    void runBadRequestTest() {

        var dto = given().when()
                .auth().oauth2(keycloakClient.getClientAccessToken(ADMIN))
                .contentType(APPLICATION_JSON)
                .post()
                .then()

                .statusCode(BAD_REQUEST.getStatusCode())
                .extract().as(ProblemDetailResponseDTO.class);

        assertThat(dto).isNotNull();

        assertThat(dto.getErrorCode()).isEqualTo(ExceptionMapper.ErrorCodes.CONSTRAINT_VIOLATIONS.name());
    }

    @Test
    void runTest() {
        var id = UUID.randomUUID().toString();
        var service = "test1-service-ui";
        var pod = "test1-ui";
        var path = "/mfe/test/api";
        var apiPath = "/test";

        var mockUrl = ConfigProvider.getConfig().getValue("quarkus.mockserver.endpoint",
                String.class);

        createServiceAndPod(service, pod);
        mockQuarkusEndpoints(path);
        Mockito.when(k8sExecService.execCommandOnPod(pod, CMD_CONFIG))
                .thenReturn(createNginxConfig(path, mockUrl));

        createOpenApiMock(createOpenAPI().addServer(createServer().url("http://localhost:8080"))
                .paths(createPaths()
                        .addPathItem(apiPath,
                                createPathItem()
                                        .GET(createOperation()
                                                .addParameter(createParameter().in(Parameter.In.PATH).name("id"))
                                                .addParameter(createParameter().in(null).name("a"))
                                                .addParameter(createParameter().in(Parameter.In.QUERY).name("q"))))));

        createResponse(path, apiPath, UNAUTHORIZED);

        var request = new SecurityTestRequestDTO()
                .id(id)
                .service(service)
                .url(mockUrl);

        var dto = given().when()
                .auth().oauth2(keycloakClient.getClientAccessToken(ADMIN))
                .body(request).contentType(APPLICATION_JSON)
                .post()
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(SecurityTestResponseDTO.class);

        assertThat(dto).isNotNull();

        assertThat(dto.getId()).isEqualTo(request.getId());
        assertThat(dto.getStatus()).isEqualTo(ExecutionStatusDTO.OK);
        assertThat(dto.getExecutions()).isNotNull();
        var e = dto.getExecutions().get(0);
        assertThat(e).isNotNull();
        assertThat(e.getStatus()).isEqualTo(ExecutionStatusDTO.OK);
    }

    @Test
    void runTestWithMultipleProxyConfiguration() {
        var id = UUID.randomUUID().toString();
        var service = "test12-service-ui";
        var pod = "test12-ui";
        var path = "/mfe/test/api";
        var apiPathPrefix = "/cool-prefix-rs";
        var apiPath = "/cool-prefix-rs/test";
        var mockserverApiPath = "/test";

        var mockUrl = ConfigProvider.getConfig().getValue("quarkus.mockserver.endpoint",
                String.class);

        createServiceAndPod(service, pod);
        mockQuarkusEndpoints(path);

        Mockito.when(k8sExecService.execCommandOnPod(pod, CMD_CONFIG))
                .thenReturn(createNginxConfigWithMultipleConfigs(path, mockUrl));

        createOpenApiMock(createOpenAPI().addServer(createServer().url("http://localhost:8080"))
                .paths(createPaths()
                        .addPathItem(apiPath,
                                createPathItem()
                                        .GET(createOperation()
                                                .addParameter(createParameter().in(Parameter.In.PATH).name("id"))
                                                .addParameter(createParameter().in(null).name("a"))))
                        .addPathItem(apiPath + "/public", createPathItem().GET(createOperation())
                                .addExtension("x-onecx", Map.of("security", "none")))
                        .addPathItem(apiPath + "/public-dummy1", createPathItem().GET(createOperation())
                                .addExtension("x-onecx", "dummy1"))
                        .addPathItem(apiPath + "/public-dummy2", createPathItem().GET(createOperation())
                                .addExtension("x-onecx", Map.of("dummy", "dummy2")))
                        .addPathItem(apiPath + "/public-dummy3", createPathItem().GET(createOperation())
                                .addExtension("x-dummy-extension", "dummy3"))
                        .addPathItem(apiPath + "/public-top-secret", createPathItem().GET(createOperation())
                                .addExtension("x-onecx", Map.of("security", "top-secret")))));

        createResponse(path, mockserverApiPath, UNAUTHORIZED);
        createResponse(path, mockserverApiPath + "/public-top-secret", UNAUTHORIZED);
        createResponse(path, mockserverApiPath + "/public-dummy3", UNAUTHORIZED);
        createResponse(path, mockserverApiPath + "/public-dummy2", UNAUTHORIZED);
        createResponse(path, mockserverApiPath + "/public-dummy1", UNAUTHORIZED);

        var request = new SecurityTestRequestDTO()
                .id(id)
                .service(service)
                .url(mockUrl);

        var dto = given().when()
                .auth().oauth2(keycloakClient.getClientAccessToken(ADMIN))
                .body(request).contentType(APPLICATION_JSON)
                .post()
                .then()

                .statusCode(OK.getStatusCode())
                .extract().as(SecurityTestResponseDTO.class);

        assertThat(dto).isNotNull();

        assertThat(dto.getId()).isEqualTo(request.getId());
        assertThat(dto.getStatus()).isEqualTo(ExecutionStatusDTO.OK);
        assertThat(dto.getExecutions()).isNotNull();
        assertThat(dto.getExecutions()).size().isEqualTo(9);
        var e = dto.getExecutions().get(4);
        assertThat(e).isNotNull();
        assertThat(e.getStatus()).isEqualTo(ExecutionStatusDTO.OK);
        assertThat(e.getUrl()).doesNotContain(apiPathPrefix);
    }

    @Test
    void runTestWithNoQuarkusProxyConfiguration() {
        var id = UUID.randomUUID().toString();
        var service = "test13-service-ui";
        var pod = "test14-ui";
        var path = "/mfe/test/api";
        var apiPath = "/test";

        var mockUrl = ConfigProvider.getConfig().getValue("quarkus.mockserver.endpoint",
                String.class);

        createServiceAndPod(service, pod);
        Mockito.when(k8sExecService.execCommandOnPod(pod, CMD_CONFIG))
                .thenReturn(createNginxConfig(path, mockUrl));

        createOpenApiMock(createOpenAPI().addServer(createServer().url("http://localhost:8080"))
                .paths(createPaths()
                        .addPathItem(apiPath,
                                createPathItem()
                                        .GET(createOperation()
                                                .addParameter(createParameter().in(Parameter.In.PATH).name("id"))
                                                .addParameter(createParameter().in(null).name("a"))
                                                .addParameter(createParameter().in(Parameter.In.QUERY).name("q"))))));

        createResponse(path, apiPath, UNAUTHORIZED);

        var request = new SecurityTestRequestDTO()
                .id(id)
                .service(service)
                .url(mockUrl);

        var dto = given().when()
                .auth().oauth2(keycloakClient.getClientAccessToken(ADMIN))
                .body(request).contentType(APPLICATION_JSON)
                .post()
                .then()
                .statusCode(BAD_REQUEST.getStatusCode())
                .extract().as(ProblemDetailResponseDTO.class);

        assertThat(dto).isNotNull();

        assertThat(dto.getErrorCode()).isEqualTo(ExceptionMapper.ErrorCodes.SERVICE_ERROR.name());
        assertThat(dto.getDetail()).isEqualTo("No Quarkus proxy configuration found");
    }

    @Test
    void runFailedTest() {
        var id = UUID.randomUUID().toString();
        var service = "test91-service-ui";
        var pod = "test91-ui";
        var path = "/mfe/test/api";
        var apiPath = "/test/{id}";

        createServiceAndPod(service, pod);
        mockQuarkusEndpoints(path);

        var mockUrl = ConfigProvider.getConfig().getValue("quarkus.mockserver.endpoint",
                String.class);

        Mockito.when(k8sExecService.execCommandOnPod(pod, CMD_CONFIG))
                .thenReturn(createNginxConfig(path, mockUrl));

        createOpenApiMock(createOpenAPI().addServer(createServer().url("http://localhost:8080"))
                .paths(createPaths()
                        .addPathItem(apiPath,
                                createPathItem()
                                        .GET(createOperation()
                                                .addParameter(createParameter().in(Parameter.In.PATH).name("id"))
                                                .addParameter(createParameter().in(null).name("a"))
                                                .addParameter(createParameter().in(Parameter.In.QUERY).name("q"))))
                        .addPathItem("/failed", createPathItem().GET(createOperation()))));

        createResponse(path, "/test/" + id, FORBIDDEN);
        createResponse(path, "/failed", OK);

        var request = new SecurityTestRequestDTO()
                .id(id)
                .service(service)
                .url(mockUrl);

        var dto = given().when()
                .auth().oauth2(keycloakClient.getClientAccessToken(ADMIN))
                .body(request).contentType(APPLICATION_JSON)
                .post()
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(SecurityTestResponseDTO.class);

        assertThat(dto).isNotNull();

        assertThat(dto.getId()).isEqualTo(request.getId());
        assertThat(dto.getStatus()).isEqualTo(ExecutionStatusDTO.FAILED);
        assertThat(dto.getExecutions()).isNotNull();
    }

    @Test
    void runNoServiceFoundTest() {
        var service = "test011-service-ui";
        var pod = "test011-ui";

        var mockUrl = ConfigProvider.getConfig().getValue("quarkus.mockserver.endpoint",
                String.class);

        createServiceAndPod(service, pod, false);

        var request = new SecurityTestRequestDTO()
                .id(UUID.randomUUID().toString())
                .service("does-not-exists")
                .url(mockUrl);

        var dto = given().when()
                .auth().oauth2(keycloakClient.getClientAccessToken(ADMIN))
                .body(request).contentType(APPLICATION_JSON)
                .post()
                .then()
                .statusCode(BAD_REQUEST.getStatusCode())
                .extract().as(ProblemDetailResponseDTO.class);

        assertThat(dto).isNotNull();

        assertThat(dto.getErrorCode()).isEqualTo(ExceptionMapper.ErrorCodes.SERVICE_ERROR.name());
        assertThat(dto.getDetail()).isEqualTo("no service found");
    }

    @Test
    void runNoPodFoundTest() {
        var service = "test01-service-ui";
        var pod = "test01-ui";

        var mockUrl = ConfigProvider.getConfig().getValue("quarkus.mockserver.endpoint",
                String.class);

        createServiceAndPod(service, pod, false);

        var request = new SecurityTestRequestDTO()
                .id(UUID.randomUUID().toString())
                .service(service)
                .url(mockUrl);

        var dto = given().when()
                .auth().oauth2(keycloakClient.getClientAccessToken(ADMIN))
                .body(request).contentType(APPLICATION_JSON)
                .post()
                .then()
                .statusCode(BAD_REQUEST.getStatusCode())
                .extract().as(ProblemDetailResponseDTO.class);

        assertThat(dto).isNotNull();

        assertThat(dto.getErrorCode()).isEqualTo(ExceptionMapper.ErrorCodes.SERVICE_ERROR.name());
        assertThat(dto.getDetail()).isEqualTo("no pods found");
    }

    @Test
    void runWrongPathTest() {
        var service = "test2-service-ui";
        var pod = "test2-ui";
        var path = "wrong/path";
        var apiPath = "/test";

        createServiceAndPod(service, pod);
        mockQuarkusEndpoints(path);

        var mockUrl = ConfigProvider.getConfig().getValue("quarkus.mockserver.endpoint",
                String.class);

        Mockito.when(k8sExecService.execCommandOnPod(pod, CMD_CONFIG))
                .thenReturn(createNginxConfig(path, mockUrl));

        createOpenApiMock(createOpenAPI().addServer(createServer().url("http://localhost:8080"))
                .paths(createPaths()
                        .addPathItem(apiPath,
                                createPathItem()
                                        .GET(createOperation()))));

        var request = new SecurityTestRequestDTO()
                .id(UUID.randomUUID().toString())
                .service(service)
                .quarkus(false)
                .url(mockUrl);

        var dto = given().when()
                .auth().oauth2(keycloakClient.getClientAccessToken(ADMIN))
                .body(request).contentType(APPLICATION_JSON)
                .post()
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(SecurityTestResponseDTO.class);

        assertThat(dto).isNotNull();

        assertThat(dto.getId()).isEqualTo(request.getId());
        assertThat(dto.getStatus()).isEqualTo(ExecutionStatusDTO.FAILED);
        assertThat(dto.getExecutions()).isNotNull();
        var e = dto.getExecutions().get(4);
        assertThat(e).isNotNull();
        assertThat(e.getStatus()).isEqualTo(ExecutionStatusDTO.ERROR);
    }

    @Test
    void runNoLocationTest() {
        var service = "test3-service-ui";
        var pod = "test3-ui";
        var path = "/mfe/test/api";
        var apiPath = "/test";

        createServiceAndPod(service, pod);
        mockQuarkusEndpoints(path);
        Mockito.when(k8sExecService.execCommandOnPod(pod, CMD_CONFIG))
                .thenReturn(createNginxConfigNoLocation());

        var mockUrl = ConfigProvider.getConfig().getValue("quarkus.mockserver.endpoint",
                String.class);

        createOpenApiMock(createOpenAPI().addServer(createServer().url("http://localhost:8080"))
                .paths(createPaths()
                        .addPathItem(apiPath,
                                createPathItem()
                                        .GET(createOperation()))));

        createResponse(path, apiPath, FORBIDDEN);

        var request = new SecurityTestRequestDTO()
                .id(UUID.randomUUID().toString())
                .service(service)
                .url(mockUrl);

        var dto = given().when()
                .auth().oauth2(keycloakClient.getClientAccessToken(ADMIN))
                .body(request).contentType(APPLICATION_JSON)
                .post()
                .then()
                .statusCode(BAD_REQUEST.getStatusCode())
                .extract().as(ProblemDetailResponseDTO.class);

        assertThat(dto).isNotNull();

        assertThat(dto.getErrorCode()).isEqualTo(ExceptionMapper.ErrorCodes.SERVICE_ERROR.name());
        assertThat(dto.getDetail()).isEqualTo("no proxy pass locations found");
    }

    @Test
    void runNoProxyPassTest() {
        var service = "test4-service-ui";
        var pod = "test4-ui";
        var path = "/mfe/test/api";
        var apiPath = "/test";

        createServiceAndPod(service, pod);
        mockQuarkusEndpoints(path);
        Mockito.when(k8sExecService.execCommandOnPod(pod, CMD_CONFIG))
                .thenReturn(createNginxConfigNoProxyPass(path));

        var mockUrl = ConfigProvider.getConfig().getValue("quarkus.mockserver.endpoint",
                String.class);

        createOpenApiMock(createOpenAPI().addServer(createServer().url("http://localhost:8080"))
                .paths(createPaths()
                        .addPathItem(apiPath,
                                createPathItem()
                                        .GET(createOperation()))));

        createResponse(path, apiPath, FORBIDDEN);

        var request = new SecurityTestRequestDTO()
                .id(UUID.randomUUID().toString())
                .service(service)
                .url(mockUrl);

        var dto = given().when()
                .auth().oauth2(keycloakClient.getClientAccessToken(ADMIN))
                .body(request).contentType(APPLICATION_JSON)
                .post()
                .then()
                .statusCode(BAD_REQUEST.getStatusCode())
                .extract().as(ProblemDetailResponseDTO.class);

        assertThat(dto).isNotNull();

        assertThat(dto.getErrorCode()).isEqualTo(ExceptionMapper.ErrorCodes.SERVICE_ERROR.name());
        assertThat(dto.getDetail()).isEqualTo("no proxy pass locations found");
    }

    @Test
    void runEmptyNginxConfigTest() {
        var service = "test10-service-ui";
        var pod = "test10-ui";

        var mockUrl = ConfigProvider.getConfig().getValue("quarkus.mockserver.endpoint",
                String.class);

        createServiceAndPod(service, pod);
        Mockito.when(k8sExecService.execCommandOnPod(pod, CMD_CONFIG))
                .thenReturn("");

        var request = new SecurityTestRequestDTO()
                .id(UUID.randomUUID().toString())
                .service(service)
                .url(mockUrl);

        var dto = given().when()
                .auth().oauth2(keycloakClient.getClientAccessToken(ADMIN))
                .body(request).contentType(APPLICATION_JSON)
                .post()
                .then()
                .statusCode(BAD_REQUEST.getStatusCode())
                .extract().as(ProblemDetailResponseDTO.class);

        assertThat(dto).isNotNull();

        assertThat(dto.getErrorCode()).isEqualTo(ExceptionMapper.ErrorCodes.SERVICE_ERROR.name());
        assertThat(dto.getDetail()).isEqualTo("no nginx config found");
    }

    @Test
    void runNullNginxConfigTest() {
        var service = "test11-service-ui";
        var pod = "test11-ui";

        var mockUrl = ConfigProvider.getConfig().getValue("quarkus.mockserver.endpoint",
                String.class);

        createServiceAndPod(service, pod);
        Mockito.when(k8sExecService.execCommandOnPod(pod, CMD_CONFIG))
                .thenReturn(null);

        var request = new SecurityTestRequestDTO()
                .id(UUID.randomUUID().toString())
                .service(service)
                .url(mockUrl);

        var dto = given().when()
                .auth().oauth2(keycloakClient.getClientAccessToken(ADMIN))
                .body(request).contentType(APPLICATION_JSON)
                .post()
                .then()
                .statusCode(BAD_REQUEST.getStatusCode())
                .extract().as(ProblemDetailResponseDTO.class);

        assertThat(dto).isNotNull();

        assertThat(dto.getErrorCode()).isEqualTo(ExceptionMapper.ErrorCodes.SERVICE_ERROR.name());
        assertThat(dto.getDetail()).isEqualTo("no nginx config found");
    }

    @Test
    void runGetOpenApiErrorTest() {
        var service = "test5-service-ui";
        var pod = "test5-ui";
        var path = "/mfe/test/api";
        var apiPath = "/test";

        createServiceAndPod(service, pod);
        mockQuarkusEndpoints(path);

        var mockUrl = ConfigProvider.getConfig().getValue("quarkus.mockserver.endpoint",
                String.class);

        Mockito.when(k8sExecService.execCommandOnPod(pod, CMD_CONFIG))
                .thenReturn(createNginxConfig(path, mockUrl));

        createResponse(path, apiPath, FORBIDDEN);

        var request = new SecurityTestRequestDTO()
                .id(UUID.randomUUID().toString())
                .service(service)
                .url(mockUrl);

        var dto = given().when()
                .auth().oauth2(keycloakClient.getClientAccessToken(ADMIN))
                .body(request).contentType(APPLICATION_JSON)
                .post()
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(SecurityTestResponseDTO.class);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(request.getId());
        assertThat(dto.getStatus()).isEqualTo(ExecutionStatusDTO.FAILED);
        assertThat(dto.getExecutions()).isNotNull();
        var e = dto.getExecutions().get(dto.getExecutions().size() - 1);
        assertThat(e).isNotNull();
        assertThat(e.getStatus()).isEqualTo(ExecutionStatusDTO.ERROR);
    }

    @Test
    void runOpenApiParseErrorTest() {
        var service = "test6-service-ui";
        var pod = "test6-ui";
        var path = "/mfe/test/api";
        var apiPath = "/test";

        createServiceAndPod(service, pod);
        mockQuarkusEndpoints(path);

        var mockUrl = ConfigProvider.getConfig().getValue("quarkus.mockserver.endpoint",
                String.class);

        Mockito.when(k8sExecService.execCommandOnPod(pod, CMD_CONFIG))
                .thenReturn(createNginxConfig(path, mockUrl));

        createOpenApiMock("""
                    openapi: 3.0.0
                    error:
                """);

        createResponse(path, apiPath, FORBIDDEN);

        var request = new SecurityTestRequestDTO()
                .id(UUID.randomUUID().toString())
                .service(service)
                .url(mockUrl);

        var dto = given().when()
                .auth().oauth2(keycloakClient.getClientAccessToken(ADMIN))

                .body(request).contentType(APPLICATION_JSON)
                .post()
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(SecurityTestResponseDTO.class);

        assertThat(dto).isNotNull();

        assertThat(dto.getId()).isEqualTo(request.getId());
        assertThat(dto.getStatus()).isEqualTo(ExecutionStatusDTO.OK);
        assertThat(dto.getExecutions()).isNotNull().size().isEqualTo(4);
    }

    @Test
    void runOpenApiNoPathsErrorTest() {
        var service = "test61-service-ui";
        var pod = "test61-ui";
        var path = "/mfe/test/api";
        var apiPath = "/test";

        createServiceAndPod(service, pod);
        mockQuarkusEndpoints(path);

        var mockUrl = ConfigProvider.getConfig().getValue("quarkus.mockserver.endpoint",
                String.class);

        Mockito.when(k8sExecService.execCommandOnPod(pod, CMD_CONFIG))
                .thenReturn(createNginxConfig(path, mockUrl));

        createOpenApiMock(createOpenAPI().addServer(createServer().url("http://localhost:8080")));

        createResponse(path, apiPath, FORBIDDEN);

        var request = new SecurityTestRequestDTO()
                .id(UUID.randomUUID().toString())
                .service(service)
                .url(mockUrl + "/");

        var dto = given().when()
                .auth().oauth2(keycloakClient.getClientAccessToken(ADMIN))

                .body(request).contentType(APPLICATION_JSON)
                .post()
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(SecurityTestResponseDTO.class);

        assertThat(dto).isNotNull();

        assertThat(dto.getId()).isEqualTo(request.getId());
        assertThat(dto.getStatus()).isEqualTo(ExecutionStatusDTO.OK);
        assertThat(dto.getExecutions()).size().isEqualTo(4);
    }

    @Test
    void runOpenApiNoPathItemsErrorTest() {
        var service = "test62-service-ui";
        var pod = "test62-ui";
        var path = "/mfe/test/api";
        var apiPath = "/test";

        createServiceAndPod(service, pod);
        mockQuarkusEndpoints(path);

        var mockUrl = ConfigProvider.getConfig().getValue("quarkus.mockserver.endpoint",
                String.class);

        Mockito.when(k8sExecService.execCommandOnPod(pod, CMD_CONFIG))
                .thenReturn(createNginxConfig(path, mockUrl));

        createOpenApiMock(createOpenAPI().addServer(createServer().url("http://localhost:8080")).paths(createPaths()));

        createResponse(path, apiPath, FORBIDDEN);

        var request = new SecurityTestRequestDTO()
                .id(UUID.randomUUID().toString())
                .service(service)
                .url(mockUrl);

        var dto = given().when()
                .auth().oauth2(keycloakClient.getClientAccessToken(ADMIN))

                .body(request).contentType(APPLICATION_JSON)
                .post()
                .then()

                .statusCode(OK.getStatusCode())
                .extract().as(SecurityTestResponseDTO.class);

        assertThat(dto).isNotNull();

        assertThat(dto.getId()).isEqualTo(request.getId());
        assertThat(dto.getStatus()).isEqualTo(ExecutionStatusDTO.OK);
        assertThat(dto.getExecutions()).size().isEqualTo(4);
    }

    @Test
    void runOpenApiNoOperationsErrorTest() {
        var service = "test63-service-ui";
        var pod = "test63-ui";
        var path = "/mfe/test/api";
        var apiPath = "/test";

        createServiceAndPod(service, pod);

        var mockUrl = ConfigProvider.getConfig().getValue("quarkus.mockserver.endpoint",
                String.class);

        mockQuarkusEndpoints(path);
        Mockito.when(k8sExecService.execCommandOnPod(pod, CMD_CONFIG))
                .thenReturn(createNginxConfig(path, mockUrl));

        createOpenApiMock(createOpenAPI().addServer(createServer().url("http://localhost:8080"))
                .paths(createPaths().addPathItem(apiPath, createPathItem())));

        createResponse(path, apiPath, FORBIDDEN);

        var request = new SecurityTestRequestDTO()
                .id(UUID.randomUUID().toString())
                .service(service)
                .url(mockUrl);

        var dto = given().when()
                .auth().oauth2(keycloakClient.getClientAccessToken(ADMIN))

                .body(request).contentType(APPLICATION_JSON)
                .post()
                .then()

                .statusCode(OK.getStatusCode())
                .extract().as(SecurityTestResponseDTO.class);

        assertThat(dto).isNotNull();

        assertThat(dto.getId()).isEqualTo(request.getId());
        assertThat(dto.getStatus()).isEqualTo(ExecutionStatusDTO.OK);
        assertThat(dto.getExecutions()).size().isEqualTo(4);
    }

    @Test
    void runTestWithEmptyOpenapi() {
        var id = UUID.randomUUID().toString();
        var service = "test78-service-ui";
        var pod = "test78-ui";
        var path = "/mfe/test/api";

        var mockUrl = ConfigProvider.getConfig().getValue("quarkus.mockserver.endpoint",
                String.class);

        createServiceAndPod(service, pod);
        mockQuarkusEndpoints(path);

        Mockito.when(k8sExecService.execCommandOnPod(pod, CMD_CONFIG))
                .thenReturn(createNginxConfigWithMultipleConfigs(path, mockUrl));

        createOpenApiMock(createOpenAPI().addServer(createServer().url("http://localhost:8080")).paths(createPaths()));

        var request = new SecurityTestRequestDTO()
                .id(id)
                .service(service)
                .url(mockUrl);

        var dto = given().when()
                .auth().oauth2(keycloakClient.getClientAccessToken(ADMIN))
                .body(request).contentType(APPLICATION_JSON)
                .post()
                .then()

                .statusCode(OK.getStatusCode())
                .extract().as(SecurityTestResponseDTO.class);

        assertThat(dto).isNotNull();
        assertThat(dto.getExecutions()).size().isEqualTo(4);
        assertThat(dto.getStatus()).isEqualTo(ExecutionStatusDTO.OK);
    }

    // Why?: Mock, KubernetesMockServer does not support Vertx WebSocket
    // ref: https://github.com/fabric8io/kubernetes-client/discussions/5364
    private K8sExecService createService(TtyExecErrorChannelable tee) {
        ObjectMeta metadata = Mockito.mock(ObjectMeta.class);
        Mockito.when(metadata.getName()).thenReturn("name");
        Mockito.when(metadata.getNamespace()).thenReturn("default");

        Pod pod = Mockito.mock(Pod.class);
        Mockito.when(pod.getMetadata()).thenReturn(metadata);

        Execable exe = Mockito.mock(Execable.class);
        Mockito.when(exe.exec(any())).thenReturn(null);

        TtyExecErrorable te = Mockito.mock(TtyExecErrorable.class);
        Mockito.when(te.writingError(any())).thenReturn(tee);

        PodResource pr = Mockito.mock(PodResource.class);
        Mockito.when(pr.get()).thenReturn(pod);
        Mockito.when(pr.writingOutput(any())).thenReturn(te);

        PodResource pr2 = Mockito.mock(PodResource.class);
        Mockito.when(pr2.get()).thenReturn(null);

        NonNamespaceOperation<Pod, PodList, PodResource> nno = Mockito.mock(NonNamespaceOperation.class);
        Mockito.when(nno.withName("name")).thenReturn(pr);

        MixedOperation<Pod, PodList, PodResource> mo = Mockito.mock(MixedOperation.class);
        Mockito.when(mo.withName("name")).thenReturn(pr);
        Mockito.when(mo.withName("does-not-exists")).thenReturn(pr2);
        Mockito.when(mo.inNamespace("default")).thenReturn(nno);

        var client = Mockito.mock(KubernetesClient.class);
        Mockito.when(client.pods()).thenReturn(mo);

        return new K8sExecService(client);
    }

    @Test
    void executionDoneTest() {
        Execable exe = Mockito.mock(Execable.class);
        Mockito.when(exe.exec(any())).thenReturn(null);

        TtyExecErrorChannelable tee = Mockito.mock(TtyExecErrorChannelable.class);
        Mockito.when(tee.usingListener(any())).then(invocation -> {
            K8sExecService.SimpleListener s = invocation.getArgument(0);
            s.onOpen();
            s.onClose(100, "Done");
            return exe;
        });

        var k8sExecService2 = createService(tee);

        assertThat(k8sExecService2.execCommandOnPod("does-not-exists", "nginx", "-T"))
                .isNull();

        k8sExecService2.execCommandOnPod("name", "nginx", "-T");

    }

    @Test
    void executionErrorTest() {
        Execable exe = Mockito.mock(Execable.class);
        Mockito.when(exe.exec(any())).thenReturn(null);

        TtyExecErrorChannelable tee = Mockito.mock(TtyExecErrorChannelable.class);
        Mockito.when(tee.usingListener(any())).then(invocation -> {
            K8sExecService.SimpleListener s = invocation.getArgument(0);
            s.onOpen();
            s.onFailure(new RuntimeException("Error"), null);
            return exe;
        });

        var k8sExecService2 = createService(tee);

        assertThatThrownBy(() -> k8sExecService2.execCommandOnPod("name", "nginx", "-T"))
                .isNotNull();

    }

}
