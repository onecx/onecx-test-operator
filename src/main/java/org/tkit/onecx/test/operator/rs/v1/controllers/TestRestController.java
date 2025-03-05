package org.tkit.onecx.test.operator.rs.v1.controllers;

import java.time.Duration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem.HttpMethod;
import org.eclipse.microprofile.openapi.models.parameters.Parameter;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tkit.onecx.test.domain.models.ServiceException;
import org.tkit.onecx.test.domain.services.K8sExecService;
import org.tkit.onecx.test.domain.services.K8sService;
import org.tkit.onecx.test.domain.services.NginxService;
import org.tkit.onecx.test.domain.services.QuarkusService;
import org.tkit.onecx.test.operator.rs.v1.mappers.ExceptionMapper;
import org.tkit.onecx.test.operator.rs.v1.mappers.TestMapper;

import gen.org.tkit.onecx.test.operator.rs.v1.TestApiService;
import gen.org.tkit.onecx.test.operator.rs.v1.model.*;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.WebClient;
import io.vertx.mutiny.uritemplate.UriTemplate;

@ApplicationScoped
@Transactional(value = Transactional.TxType.NOT_SUPPORTED)
public class TestRestController implements TestApiService {

    private static final Logger log = LoggerFactory.getLogger(TestRestController.class);

    static final String[] CMD_CONFIG = { "nginx", "-T" };

    @Inject
    K8sService k8sService;

    @Inject
    K8sExecService k8sExecService;

    @Inject
    NginxService nginxService;

    @Inject
    QuarkusService quarkusService;

    @Inject
    Vertx vertx;

    @Inject
    ExceptionMapper exceptionMapper;

    @Inject
    TestMapper testMapper;

    @Override
    public Response executeSecurityTest(SecurityTestRequestDTO dto) {

        var pods = k8sService.findPodsForService(dto.getService());
        if (pods.isEmpty()) {
            throw new ServiceException("no pods found");
        }
        var pod = pods.get(0);

        var nginxConfig = k8sExecService.execCommandOnPod(pod, CMD_CONFIG);
        if (nginxConfig == null || nginxConfig.isEmpty()) {
            throw new ServiceException("no nginx config found");
        }
        var proxyPassLocations = nginxService.getProxyPassLocation(nginxConfig);
        if (proxyPassLocations.isEmpty()) {
            throw new ServiceException("no proxy pass locations found");
        }

        final var url = testMapper.url(dto);

        var result = testMapper.response(dto);

        proxyPassLocations.forEach((proxyPath, path) -> {
            ;
            try {
                log.info("Path: {} proxy: {}", path, proxyPath);
                var openapi = quarkusService.getOpenApi(path);

                testOpenApi(result, url, proxyPath, openapi);
            } catch (Exception ex) {
                log.error("Error execute test for {} - {}, error: {}", path, proxyPath, ex.getMessage(), ex);
                result.addExecutionsItem(testMapper.createExecutionError(path, proxyPath, ex.getMessage()));
            }
        });

        var failed = result.getExecutions().stream().anyMatch(x -> x.getStatus() != ExecutionStatusDTO.OK);
        result.setStatus(failed ? ExecutionStatusDTO.FAILED : ExecutionStatusDTO.OK);

        return Response.ok(result).build();
    }

    private void testOpenApi(SecurityTestResponseDTO result, String domain, String proxyPath, OpenAPI openapi) {
        if (openapi.getPaths() == null || openapi.getPaths().getPathItems() == null) {
            return;
        }

        openapi.getPaths().getPathItems().forEach((path, item) -> {
            var uri = domain + proxyPath + path;
            item.getOperations().forEach((method, op) -> {
                execute(result, uri, path, proxyPath, method, op);
            });
        });
    }

    private void execute(SecurityTestResponseDTO result, String uri, String path, String proxyPath, HttpMethod method,
            Operation op) {
        log.info("Test {} {}", method, uri);

        try {
            var request = WebClient.create(vertx, new WebClientOptions().setVerifyHost(false).setTrustAll(true))
                    .requestAbs(io.vertx.core.http.HttpMethod.valueOf(method.name()), UriTemplate.of(uri));

            if (op.getParameters() != null) {
                op.getParameters().forEach(p -> {
                    if (Parameter.In.PATH == p.getIn()) {
                        request.setTemplateParam(p.getName(), result.getId());
                    }
                });
            }

            var response = request.send().await().atMost(Duration.ofSeconds(5));
            var code = response.statusCode();
            var status = code == Response.Status.UNAUTHORIZED.getStatusCode() ? ExecutionStatusDTO.OK
                    : ExecutionStatusDTO.FAILED;
            log.error("Test {} {} {} {}", method, uri, code, status);

            result.addExecutionsItem(testMapper.createExecution(path, proxyPath, status, uri, code));
        } catch (Exception ex) {
            result.addExecutionsItem(testMapper.createExecutionError(path, proxyPath, ex.getMessage()).url(uri));
        }
    }

    @ServerExceptionMapper
    public RestResponse<ProblemDetailResponseDTO> constraint(ConstraintViolationException ex) {
        return exceptionMapper.constraint(ex);
    }

    @ServerExceptionMapper
    public RestResponse<ProblemDetailResponseDTO> serviceException(ServiceException ex) {
        return exceptionMapper.service(ex);
    }
}
