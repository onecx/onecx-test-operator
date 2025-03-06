package org.tkit.onecx.test.operator.rs.v1.controllers;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.tkit.onecx.test.operator.rs.v1.controllers.TestRestController.CMD_CONFIG;

import java.util.UUID;

import org.eclipse.microprofile.openapi.models.parameters.Parameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.tkit.onecx.test.domain.services.K8sExecService;
import org.tkit.onecx.test.operator.AbstractTest;
import org.tkit.onecx.test.operator.rs.v1.mappers.ExceptionMapper;

import gen.org.tkit.onecx.test.operator.rs.v1.model.ExecutionStatusDTO;
import gen.org.tkit.onecx.test.operator.rs.v1.model.ProblemDetailResponseDTO;
import gen.org.tkit.onecx.test.operator.rs.v1.model.SecurityTestRequestDTO;
import gen.org.tkit.onecx.test.operator.rs.v1.model.SecurityTestResponseDTO;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.*;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;
import io.smallrye.openapi.api.models.OperationImpl;
import io.smallrye.openapi.api.models.PathItemImpl;
import io.smallrye.openapi.api.models.PathsImpl;
import io.smallrye.openapi.api.models.parameters.ParameterImpl;

@QuarkusTest
@WithKubernetesTestServer
@TestHTTPEndpoint(TestRestController.class)
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
                .auth().oauth2(keycloakClient.getAccessToken(ADMIN))
                .header(APM_HEADER_PARAM, ADMIN)
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

        createServiceAndPod(service, pod);
        Mockito.when(k8sExecService.execCommandOnPod(pod, CMD_CONFIG))
                .thenReturn(createNginxConfig(path));

        createOpenApiMock(createOpenApi()
                .paths(new PathsImpl()
                        .addPathItem(apiPath, new PathItemImpl()
                                .GET(new OperationImpl()
                                        .addParameter(new ParameterImpl().in(Parameter.In.PATH).name("id"))
                                        .addParameter(new ParameterImpl().in(null).name("a"))
                                        .addParameter(new ParameterImpl().in(Parameter.In.QUERY).name("q"))))));

        createResponse(path, apiPath, UNAUTHORIZED);

        var request = new SecurityTestRequestDTO()
                .id(id)
                .service(service)
                .url(MOCK_SERVER_ENDPOINT);

        var dto = given().when()
                .auth().oauth2(keycloakClient.getAccessToken(ADMIN))
                .header(APM_HEADER_PARAM, ADMIN)
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
    void runFailedTest() {
        var id = UUID.randomUUID().toString();
        var service = "test91-service-ui";
        var pod = "test91-ui";
        var path = "/mfe/test/api";
        var apiPath = "/test/{id}";

        createServiceAndPod(service, pod);
        Mockito.when(k8sExecService.execCommandOnPod(pod, CMD_CONFIG))
                .thenReturn(createNginxConfig(path));

        createOpenApiMock(createOpenApi()
                .paths(new PathsImpl()
                        .addPathItem(apiPath, new PathItemImpl()
                                .GET(new OperationImpl()
                                        .addParameter(new ParameterImpl().in(Parameter.In.PATH).name("id"))
                                        .addParameter(new ParameterImpl().name("a"))
                                        .addParameter(new ParameterImpl().in(Parameter.In.QUERY).name("q"))))
                        .addPathItem("/failed", new PathItemImpl().GET(new OperationImpl()))));

        createResponse(path, "/test/" + id, FORBIDDEN);
        createResponse(path, "/failed", OK);

        var request = new SecurityTestRequestDTO()
                .id(id)
                .service(service)
                .url(MOCK_SERVER_ENDPOINT);

        var dto = given().when()
                .auth().oauth2(keycloakClient.getAccessToken(ADMIN))
                .header(APM_HEADER_PARAM, ADMIN)
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
    void runNoPodFoundTest() {
        var service = "test01-service-ui";
        var pod = "test01-ui";
        var path = "/mfe/test/api";
        var apiPath = "/test";

        createServiceAndPod(service, pod, false);
        Mockito.when(k8sExecService.execCommandOnPod(pod, CMD_CONFIG))
                .thenReturn(createNginxConfig(path));

        createOpenApiMock(createOpenApi().paths(new PathsImpl().addPathItem(apiPath,
                new PathItemImpl().GET(new OperationImpl()))));

        createResponse(path, apiPath, FORBIDDEN);

        var request = new SecurityTestRequestDTO()
                .id(UUID.randomUUID().toString())
                .service(service)
                .url(MOCK_SERVER_ENDPOINT);

        var dto = given().when()
                .auth().oauth2(keycloakClient.getAccessToken(ADMIN))
                .header(APM_HEADER_PARAM, ADMIN)
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
        Mockito.when(k8sExecService.execCommandOnPod(pod, CMD_CONFIG))
                .thenReturn(createNginxConfig(path));

        createOpenApiMock(createOpenApi().paths(new PathsImpl().addPathItem(apiPath,
                new PathItemImpl().GET(new OperationImpl()))));

        createResponse(path, apiPath, FORBIDDEN);

        var request = new SecurityTestRequestDTO()
                .id(UUID.randomUUID().toString())
                .service(service)
                .url(MOCK_SERVER_ENDPOINT);

        var dto = given().when()
                .auth().oauth2(keycloakClient.getAccessToken(ADMIN))
                .header(APM_HEADER_PARAM, ADMIN)
                .body(request).contentType(APPLICATION_JSON)
                .post()
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(SecurityTestResponseDTO.class);

        assertThat(dto).isNotNull();

        assertThat(dto.getId()).isEqualTo(request.getId());
        assertThat(dto.getStatus()).isEqualTo(ExecutionStatusDTO.FAILED);
        assertThat(dto.getExecutions()).isNotNull();
        var e = dto.getExecutions().get(0);
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
        Mockito.when(k8sExecService.execCommandOnPod(pod, CMD_CONFIG))
                .thenReturn(createNginxConfigNoLocation(path));

        createOpenApiMock(createOpenApi().paths(new PathsImpl().addPathItem(apiPath,
                new PathItemImpl().GET(new OperationImpl()))));

        createResponse(path, apiPath, FORBIDDEN);

        var request = new SecurityTestRequestDTO()
                .id(UUID.randomUUID().toString())
                .service(service)
                .url(MOCK_SERVER_ENDPOINT);

        var dto = given().when()
                .auth().oauth2(keycloakClient.getAccessToken(ADMIN))
                .header(APM_HEADER_PARAM, ADMIN)
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
        Mockito.when(k8sExecService.execCommandOnPod(pod, CMD_CONFIG))
                .thenReturn(createNginxConfigNoProxyPass(path));

        createOpenApiMock(createOpenApi().paths(new PathsImpl().addPathItem(apiPath,
                new PathItemImpl().GET(new OperationImpl()))));

        createResponse(path, apiPath, FORBIDDEN);

        var request = new SecurityTestRequestDTO()
                .id(UUID.randomUUID().toString())
                .service(service)
                .url(MOCK_SERVER_ENDPOINT);

        var dto = given().when()
                .auth().oauth2(keycloakClient.getAccessToken(ADMIN))
                .header(APM_HEADER_PARAM, ADMIN)
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

        createServiceAndPod(service, pod);
        Mockito.when(k8sExecService.execCommandOnPod(pod, CMD_CONFIG))
                .thenReturn("");

        var request = new SecurityTestRequestDTO()
                .id(UUID.randomUUID().toString())
                .service(service)
                .url(MOCK_SERVER_ENDPOINT);

        var dto = given().when()
                .auth().oauth2(keycloakClient.getAccessToken(ADMIN))
                .header(APM_HEADER_PARAM, ADMIN)
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

        createServiceAndPod(service, pod);
        Mockito.when(k8sExecService.execCommandOnPod(pod, CMD_CONFIG))
                .thenReturn(null);

        var request = new SecurityTestRequestDTO()
                .id(UUID.randomUUID().toString())
                .service(service)
                .url(MOCK_SERVER_ENDPOINT);

        var dto = given().when()
                .auth().oauth2(keycloakClient.getAccessToken(ADMIN))
                .header(APM_HEADER_PARAM, ADMIN)
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
        Mockito.when(k8sExecService.execCommandOnPod(pod, CMD_CONFIG))
                .thenReturn(createNginxConfig(path));

        createResponse(path, apiPath, FORBIDDEN);

        var request = new SecurityTestRequestDTO()
                .id(UUID.randomUUID().toString())
                .service(service)
                .url(MOCK_SERVER_ENDPOINT);

        var dto = given().when()
                .auth().oauth2(keycloakClient.getAccessToken(ADMIN))
                .header(APM_HEADER_PARAM, ADMIN)
                .body(request).contentType(APPLICATION_JSON)
                .post()
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(SecurityTestResponseDTO.class);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(request.getId());
        assertThat(dto.getStatus()).isEqualTo(ExecutionStatusDTO.FAILED);
        assertThat(dto.getExecutions()).isNotNull();
        var e = dto.getExecutions().get(0);
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
        Mockito.when(k8sExecService.execCommandOnPod(pod, CMD_CONFIG))
                .thenReturn(createNginxConfig(path));

        createOpenApiMock("""
                    openapi: 3.0.0
                    error:
                """);

        createResponse(path, apiPath, FORBIDDEN);

        var request = new SecurityTestRequestDTO()
                .id(UUID.randomUUID().toString())
                .service(service)
                .url(MOCK_SERVER_ENDPOINT);

        var dto = given().when()
                .auth().oauth2(keycloakClient.getAccessToken(ADMIN))
                .header(APM_HEADER_PARAM, ADMIN)
                .body(request).contentType(APPLICATION_JSON)
                .post()
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(SecurityTestResponseDTO.class);

        assertThat(dto).isNotNull();

        assertThat(dto.getId()).isEqualTo(request.getId());
        assertThat(dto.getStatus()).isEqualTo(ExecutionStatusDTO.OK);
        assertThat(dto.getExecutions()).isNotNull().isEmpty();
    }

    @Test
    void runOpenApiNoPathsErrorTest() {
        var service = "test61-service-ui";
        var pod = "test61-ui";
        var path = "/mfe/test/api";
        var apiPath = "/test";

        createServiceAndPod(service, pod);
        Mockito.when(k8sExecService.execCommandOnPod(pod, CMD_CONFIG))
                .thenReturn(createNginxConfig(path));

        createOpenApiMock(createOpenApi());

        createResponse(path, apiPath, FORBIDDEN);

        var request = new SecurityTestRequestDTO()
                .id(UUID.randomUUID().toString())
                .service(service)
                .url(MOCK_SERVER_ENDPOINT);

        var dto = given().when()
                .auth().oauth2(keycloakClient.getAccessToken(ADMIN))
                .header(APM_HEADER_PARAM, ADMIN)
                .body(request).contentType(APPLICATION_JSON)
                .post()
                .then()
                .statusCode(OK.getStatusCode())
                .extract().as(SecurityTestResponseDTO.class);

        assertThat(dto).isNotNull();

        assertThat(dto.getId()).isEqualTo(request.getId());
        assertThat(dto.getStatus()).isEqualTo(ExecutionStatusDTO.OK);
        assertThat(dto.getExecutions()).isNotNull().isEmpty();
    }

    @Test
    void runOpenApiNoPathItemsErrorTest() {
        var service = "test62-service-ui";
        var pod = "test62-ui";
        var path = "/mfe/test/api";
        var apiPath = "/test";

        createServiceAndPod(service, pod);
        Mockito.when(k8sExecService.execCommandOnPod(pod, CMD_CONFIG))
                .thenReturn(createNginxConfig(path));

        createOpenApiMock(createOpenApi().paths(new PathsImpl()));

        createResponse(path, apiPath, FORBIDDEN);

        var request = new SecurityTestRequestDTO()
                .id(UUID.randomUUID().toString())
                .service(service)
                .url(MOCK_SERVER_ENDPOINT);

        var dto = given().when()
                .auth().oauth2(keycloakClient.getAccessToken(ADMIN))
                .header(APM_HEADER_PARAM, ADMIN)
                .body(request).contentType(APPLICATION_JSON)
                .post()
                .then()

                .statusCode(OK.getStatusCode())
                .extract().as(SecurityTestResponseDTO.class);

        assertThat(dto).isNotNull();

        assertThat(dto.getId()).isEqualTo(request.getId());
        assertThat(dto.getStatus()).isEqualTo(ExecutionStatusDTO.OK);
        assertThat(dto.getExecutions()).isNotNull().isEmpty();
    }

    @Test
    void runOpenApiNoOperationsErrorTest() {
        var service = "test63-service-ui";
        var pod = "test63-ui";
        var path = "/mfe/test/api";
        var apiPath = "/test";

        createServiceAndPod(service, pod);
        Mockito.when(k8sExecService.execCommandOnPod(pod, CMD_CONFIG))
                .thenReturn(createNginxConfig(path));

        createOpenApiMock(createOpenApi().paths(new PathsImpl().addPathItem(apiPath,
                new PathItemImpl())));

        createResponse(path, apiPath, FORBIDDEN);

        var request = new SecurityTestRequestDTO()
                .id(UUID.randomUUID().toString())
                .service(service)
                .url(MOCK_SERVER_ENDPOINT);

        var dto = given().when()
                .auth().oauth2(keycloakClient.getAccessToken(ADMIN))
                .header(APM_HEADER_PARAM, ADMIN)
                .body(request).contentType(APPLICATION_JSON)
                .post()
                .then()

                .statusCode(OK.getStatusCode())
                .extract().as(SecurityTestResponseDTO.class);

        assertThat(dto).isNotNull();

        assertThat(dto.getId()).isEqualTo(request.getId());
        assertThat(dto.getStatus()).isEqualTo(ExecutionStatusDTO.OK);
        assertThat(dto.getExecutions()).isNotNull().isEmpty();
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

        var k8sExecService = createService(tee);

        assertThat(k8sExecService.execCommandOnPod("does-not-exists", "nginx", "-T"))
                .isNull();

        k8sExecService.execCommandOnPod("name", "nginx", "-T");

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

        var k8sExecService = createService(tee);

        assertThatThrownBy(() -> k8sExecService.execCommandOnPod("name", "nginx", "-T"))
                .isNotNull();

    }

}
