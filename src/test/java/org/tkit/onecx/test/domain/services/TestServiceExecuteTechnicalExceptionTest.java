package org.tkit.onecx.test.domain.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.microprofile.openapi.OASFactory.createOpenAPI;
import static org.eclipse.microprofile.openapi.OASFactory.createOperation;
import static org.eclipse.microprofile.openapi.OASFactory.createPathItem;
import static org.eclipse.microprofile.openapi.OASFactory.createPaths;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.tkit.onecx.test.domain.models.ProxyConfiguration;
import org.tkit.onecx.test.domain.models.TestExecution;
import org.tkit.onecx.test.domain.models.TestRequest;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusTest;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.WebClient;
import io.vertx.mutiny.uritemplate.UriTemplate;

@QuarkusTest
class TestServiceExecuteTechnicalExceptionTest {

    @Test
    void execute_publicPath_addsErrorExecution_whenWebClientRequestCreationFails() {
        TestService service = new TestService();
        service.k8sService = mock(K8sService.class);
        service.k8sExecService = mock(K8sExecService.class);
        service.nginxService = mock(NginxService.class);
        service.quarkusService = mock(QuarkusService.class);
        service.springBootService = mock(SpringBootService.class);
        service.vertx = mock(Vertx.class);
        service.objectMapper = new ObjectMapper();

        when(service.k8sService.findServiceSelector("svc")).thenReturn(Map.of("app", "svc"));
        when(service.k8sService.findPodsBySelector(Map.of("app", "svc"))).thenReturn(List.of("pod-1"));
        when(service.k8sExecService.execCommandOnPod("pod-1", TestService.CMD_CONFIG)).thenReturn("nginx-config");
        when(service.nginxService.getProxyPassLocation("nginx-config"))
                .thenReturn(List.of(new ProxyConfiguration("/mfe/test/api", "http://bff-host", "/test", "/test")));

        when(service.quarkusService.invokeGeneric2xxEndpoint("http://bff-host")).thenReturn(200);
        when(service.quarkusService.resolveOpenApiPath("http://bff-host")).thenReturn("/q/openapi");
        when(service.quarkusService.getOpenApi("http://bff-host")).thenReturn(createOpenAPI().paths(createPaths()
                .addPathItem("/request-create-exception", createPathItem().GET(createOperation().operationId("op")))));

        WebClient webClient = mock(WebClient.class);
        try (MockedStatic<WebClient> webClientMock = mockStatic(WebClient.class)) {
            webClientMock.when(() -> WebClient.create(any(), any())).thenReturn(webClient);
            when(webClient.requestAbs(any(io.vertx.core.http.HttpMethod.class), anyString()))
                    .thenThrow(new RuntimeException("generic-request-create-failed"));
            when(webClient.requestAbs(any(io.vertx.core.http.HttpMethod.class), any(UriTemplate.class)))
                    .thenThrow(new RuntimeException("request-abs-failed"));

            TestRequest request = new TestRequest();
            request.setId("123");
            request.setService("svc");
            request.setUrl("https://domain.example");

            var result = service.execute(request);

            var openApiExecution = result.getExecutions().stream()
                    .filter(x -> "/request-create-exception".equals(x.getPath()))
                    .findFirst();

            assertThat(openApiExecution).isPresent();
            assertThat(openApiExecution.orElseThrow().getStatus()).isEqualTo(TestExecution.Status.ERROR);
            assertThat(openApiExecution.orElseThrow().getDetailedStatus()).contains("request-abs-failed");
        }
    }
}
