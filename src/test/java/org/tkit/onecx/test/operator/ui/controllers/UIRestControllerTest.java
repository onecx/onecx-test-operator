package org.tkit.onecx.test.operator.ui.controllers;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.*;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.assertj.core.api.Assertions.assertThat;

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
@TestHTTPEndpoint(UIController.class)
class UIRestControllerTest extends AbstractTest {

    @InjectMock
    K8sExecService k8sExecService;

    @BeforeEach
    void resetExpectation() {
        clearExpectation(mockServerClient);
    }

    @Test
    void uiBadRequestTest() {

        var dto = given().when()
                .auth().oauth2(keycloakClient.getAccessToken(ADMIN))
                .header(APM_HEADER_PARAM, ADMIN)
                .contentType(APPLICATION_JSON)
                .post("")
                .then()

                .statusCode(BAD_REQUEST.getStatusCode())
                .extract().as(ProblemDetailResponseDTO.class);

        assertThat(dto).isNotNull();

        assertThat(dto.getErrorCode()).isEqualTo(ExceptionMapper.ErrorCodes.CONSTRAINT_VIOLATIONS.name());
    }

    @Test
    void runNoServiceFoundTest() {
        var service = "ui-test-service-1";
        var pod = "ui-test-1";

        createServiceAndPod(service, pod, false);

        var request = new SecurityTestRequestDTO()
                .id(UUID.randomUUID().toString())
                .service("does-not-exists")
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
        assertThat(dto.getDetail()).isEqualTo("no service found");
    }

    @Test
    void runFailedTest() {
        var id = UUID.randomUUID().toString();
        var service = "ui-test-service-ui-3";
        var pod = "ui-test-3";
        var path = "/mfe/test-ui/api";
        var apiPath = "/test-ui/{id}";

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
}
