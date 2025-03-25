package org.tkit.onecx.test.domain.services;

import java.time.Duration;
import java.util.ArrayList;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.parameters.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tkit.onecx.test.domain.models.ServiceException;
import org.tkit.onecx.test.domain.models.TestExecution;
import org.tkit.onecx.test.domain.models.TestRequest;
import org.tkit.onecx.test.domain.models.TestResponse;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.WebClient;
import io.vertx.mutiny.uritemplate.UriTemplate;

@ApplicationScoped
public class TestService {

    private static final Logger log = LoggerFactory.getLogger(TestService.class);

    protected static final String[] CMD_CONFIG = { "nginx", "-T" };

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

    public TestResponse execute(TestRequest request) throws SecurityException {

        var selector = k8sService.findServiceSelector(request.getService());
        if (selector.isEmpty()) {
            throw new ServiceException("no service found");
        }

        var pods = k8sService.findPodsBySelector(selector);
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

        final var url = url(request);

        var result = new TestResponse();
        result.setId(request.getId());
        result.setService(request.getService());
        result.setUrl(request.getUrl());
        result.setExecutions(new ArrayList<>());

        proxyPassLocations.forEach((proxyPath, path) -> {

            if (request.isQuarkus()) {
                testQuarkusQService("health", result, url, proxyPath, "/q/health");
                testQuarkusQService("metrics", result, url, proxyPath, "/q/metrics");
                testQuarkusQService("openapi", result, url, proxyPath, "/q/openapi");
                testQuarkusQService("swagger-ui", result, url, proxyPath, "/q/swagger-ui");
            } else {
                log.warn("Test quarkus Q-Services is disabled!");
            }

            try {
                log.info("OpenAPI path: {} proxy: {}", path, proxyPath);
                var openapi = quarkusService.getOpenApi(path);

                testOpenApi(result, url, proxyPath, openapi);
            } catch (Exception ex) {
                log.error("Error execute test for {} - {}, error: {}", path, proxyPath, ex.getMessage(), ex);
                result.getExecutions().add(createExecutionError(path, proxyPath, url, ex.getMessage()));
            }
        });

        var failed = result.getExecutions().stream().anyMatch(x -> x.getStatus() != TestExecution.Status.OK);
        result.setStatus(failed ? TestResponse.Status.FAILED : TestResponse.Status.OK);

        return result;
    }

    private void testQuarkusQService(String name, TestResponse result, String domain, String proxyPath, String path) {
        var uri = createUri(domain, proxyPath, path);
        try {
            log.info("{} path: {} proxy: {} uri: {}", name, path, proxyPath, uri);
            var request = WebClient.create(vertx, new WebClientOptions().setVerifyHost(false).setTrustAll(true))
                    .requestAbs(HttpMethod.GET, uri);

            var response = request.send().await().atMost(Duration.ofSeconds(5));
            var code = response.statusCode();

            var status = code >= Response.Status.BAD_REQUEST.getStatusCode() ? TestExecution.Status.OK
                    : TestExecution.Status.FAILED;

            log.error("{}-test {} {} {}", name, uri, code, status);

            result.getExecutions().add(createExecution(path, proxyPath, status, uri, code));

        } catch (Exception ex) {
            log.error("Error execute {} test for {} - {}, error: {}", name, path, proxyPath, ex.getMessage(), ex);
            result.getExecutions().add(createExecution(path, proxyPath, TestExecution.Status.OK, uri, ex.getMessage()));
        }
    }

    private void testOpenApi(TestResponse result, String domain, String proxyPath, OpenAPI openapi) {
        if (openapi.getPaths() == null || openapi.getPaths().getPathItems() == null) {
            return;
        }

        openapi.getPaths().getPathItems().forEach((path, item) -> {
            var uri = createUri(domain, proxyPath, path);
            item.getOperations().forEach((method, op) -> execute(result, uri, path, proxyPath, method, op));
        });
    }

    private void execute(TestResponse result, String uri, String path, String proxyPath, PathItem.HttpMethod method,
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
            var status = code == Response.Status.UNAUTHORIZED.getStatusCode() ? TestExecution.Status.OK
                    : TestExecution.Status.FAILED;
            log.error("Test {} {} {} {}", method, uri, code, status);

            result.getExecutions().add(createExecution(path, proxyPath, status, uri, code));
        } catch (Exception ex) {
            result.getExecutions().add(createExecutionError(path, proxyPath, uri, ex.getMessage()));
        }
    }

    private TestExecution createExecutionError(String path, String proxyPath, String uri, String error) {
        var e = new TestExecution();
        e.setPath(path);
        e.setProxy(proxyPath);
        e.setError(error);
        e.setUrl(uri);
        e.setStatus(TestExecution.Status.ERROR);
        return e;
    }

    private TestExecution createExecution(String path, String proxyPath, TestExecution.Status status, String uri, int code) {
        var e = new TestExecution();
        e.setPath(path);
        e.setProxy(proxyPath);
        e.setStatus(status);
        e.setUrl(uri);
        e.setCode(code);
        return e;
    }

    private TestExecution createExecution(String path, String proxyPath, TestExecution.Status status, String uri,
            String error) {
        var e = new TestExecution();
        e.setPath(path);
        e.setProxy(proxyPath);
        e.setStatus(status);
        e.setUrl(uri);
        e.setError(error);
        return e;
    }

    private String url(TestRequest dto) {
        var tmp = dto.getUrl();
        if (tmp.endsWith("/")) {
            tmp = tmp.substring(0, tmp.length() - 1);
        }
        return tmp;
    }

    private String createUri(String domain, String proxyPath, String path) {
        return domain + proxyPath + path;
    }
}
