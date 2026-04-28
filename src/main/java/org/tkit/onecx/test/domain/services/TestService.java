package org.tkit.onecx.test.domain.services;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.parameters.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.tkit.onecx.test.domain.models.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.smallrye.mutiny.TimeoutException;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpRequest;
import io.vertx.mutiny.ext.web.client.HttpResponse;
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
    SpringBootService springBootService;

    @Inject
    Vertx vertx;

    @Inject
    ObjectMapper objectMapper;

    private record EndpointCheck(String name, String path) {
    }

    private record ExecutionOutcome(TestExecution.Status status, int code, Level logLevel) {
    }

    private static final String ENDPOINT_HEALTH = "health";
    private static final String ENDPOINT_METRICS = "metrics";
    private static final String ENDPOINT_SWAGGER_UI = "swagger-ui";

    private static final List<EndpointCheck> GENERIC_BFF_ENDPOINTS = List.of(
            new EndpointCheck(ENDPOINT_HEALTH, "/q/health"),
            new EndpointCheck(ENDPOINT_HEALTH, "/actuator/health"),
            new EndpointCheck(ENDPOINT_METRICS, "/q/metrics"),
            new EndpointCheck(ENDPOINT_METRICS, "/actuator/metrics"),
            new EndpointCheck(ENDPOINT_SWAGGER_UI, "/q/swagger-ui"),
            new EndpointCheck(ENDPOINT_SWAGGER_UI, "/swagger-ui.html"),
            new EndpointCheck(ENDPOINT_SWAGGER_UI, "/swagger-ui/index.html"));

    public TestResponse execute(TestRequest request) throws SecurityException {

        var proxyPassLocations = loadProxyPassLocations(request.getService());
        final var url = url(request);
        var bffRoutesByHost = resolveBffRoutesByHost(proxyPassLocations);

        var result = initTestResponse(request);
        bffRoutesByHost.forEach((host, routes) -> executeForHostRoutes(result, url, host, routes));

        resolveOverallStatus(result);

        String jsonResult = toJson(result);
        log.info("Security test result {}", jsonResult);

        return result;
    }

    private List<ProxyConfiguration> loadProxyPassLocations(String service) {
        var selector = k8sService.findServiceSelector(service);
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
        return proxyPassLocations;
    }

    private Map<String, Map<String, ProxyConfiguration>> resolveBffRoutesByHost(List<ProxyConfiguration> proxyPassLocations) {
        Map<String, Map<String, ProxyConfiguration>> routesByHost = groupRoutesByHost(proxyPassLocations);
        Map<String, Map<String, ProxyConfiguration>> bffRoutesByHost = findBffRoutesByHost(routesByHost);

        if (bffRoutesByHost.isEmpty()) {
            log.error("No BFF proxy configuration found");
            throw new ServiceException("No BFF proxy configuration found");
        }
        return bffRoutesByHost;
    }

    private TestResponse initTestResponse(TestRequest request) {
        var result = new TestResponse();
        result.setId(request.getId());
        result.setService(request.getService());
        result.setUrl(request.getUrl());
        result.setExecutions(new ArrayList<>());
        result.setWhitelistedPaths(new ArrayList<>());
        return result;
    }

    @SuppressWarnings("java:S3655")
    private void executeForHostRoutes(TestResponse result, String url, String host, Map<String, ProxyConfiguration> routes) {
        //null safe here, we already check service type is not null during detection if service is BFF
        var serviceType = deteremineServiceType(host);
        log.info("Determined service type {} for host {}", serviceType, host);
        //there must be at least one route so no null check necessary
        ProxyConfiguration fallbackPC = routes.values().stream().findFirst().get();
        log.info("Proxy pass locations found for host {} and serviceType {} -> {}", host, serviceType, routes.keySet());

        testGenericBffEndpoints(result, url, host, routes, fallbackPC, serviceType);
        executeOpenApiTestsSafely(result, url, host, routes, fallbackPC, serviceType);
    }

    private void testGenericBffEndpoints(TestResponse result, String url, String host,
            Map<String, ProxyConfiguration> routes, ProxyConfiguration fallbackPC, ServiceBFFTechnology serviceType) {
        GENERIC_BFF_ENDPOINTS.forEach(check -> testInaccessibilityOfGenericBffEndpoints(check.name(), result, url,
                resolveProxyConfiguration(routes, fallbackPC, check.path()), check.path()));

        String openApiPath = resolveOpenApiPath(host, serviceType);
        testInaccessibilityOfGenericBffEndpoints("openapi", result, url,
                resolveProxyConfiguration(routes, fallbackPC, openApiPath), openApiPath);
    }

    private String resolveOpenApiPath(String host, ServiceBFFTechnology serviceType) {
        return ServiceBFFTechnology.QUARKUS == serviceType
                ? quarkusService.resolveOpenApiPath(host)
                : springBootService.resolveOpenApiPath(host);
    }

    private OpenAPI getOpenApi(String host, ServiceBFFTechnology serviceType) {
        return ServiceBFFTechnology.QUARKUS == serviceType
                ? quarkusService.getOpenApi(host)
                : springBootService.getOpenApi(host);
    }

    private void executeOpenApiTestsSafely(TestResponse result, String url, String host,
            Map<String, ProxyConfiguration> routes, ProxyConfiguration fallbackPC, ServiceBFFTechnology serviceType) {
        try {
            log.info("OpenAPI location path: {}, proxy host: {}, proxy path: {}",
                    fallbackPC.getLocation(),
                    fallbackPC.getProxyHost(), fallbackPC.getProxyPath());

            var openapi = getOpenApi(host, serviceType);
            testOpenApi(result, url, routes, fallbackPC, openapi);
        } catch (Exception ex) {
            log.error("Error execute test for {} - {}, error: {}", fallbackPC.getProxyHost(),
                    fallbackPC.getLocation(), ex.getMessage(), ex);
            result.getExecutions().add(createExecutionError(fallbackPC.getProxyHost(),
                    fallbackPC.getLocation(), url, ex.getMessage()));
        }
    }

    private void resolveOverallStatus(TestResponse result) {
        boolean failed = result.getExecutions().stream().anyMatch(x -> x.getStatus() == TestExecution.Status.FAILED);
        boolean hasErrors = result.getExecutions().stream().anyMatch(x -> x.getStatus() == TestExecution.Status.ERROR);
        boolean hasWarnings = result.getExecutions().stream()
                .anyMatch(x -> x.getStatus() == TestExecution.Status.WARNING);

        if (failed) {
            result.setStatus(TestResponse.Status.FAILED);
        } else if (hasErrors) {
            result.setStatus(TestResponse.Status.ERROR);
        } else if (hasWarnings) {
            result.setStatus(TestResponse.Status.WARNING);
        } else {
            result.setStatus(TestResponse.Status.OK);
        }
    }

    private Map<String, Map<String, ProxyConfiguration>> groupRoutesByHost(List<ProxyConfiguration> proxyPassLocations) {
        var result = new LinkedHashMap<String, Map<String, ProxyConfiguration>>();
        proxyPassLocations.stream()
                .filter(pc -> pc.getProxyHost() != null)
                .forEach(pc -> {
                    var routeKey = pc.getServicePathKey() != null ? pc.getServicePathKey() : pc.getLocation();
                    result.computeIfAbsent(pc.getProxyHost(), key -> new LinkedHashMap<>())
                            .putIfAbsent(routeKey, pc);
                });
        return result;
    }

    private Map<String, Map<String, ProxyConfiguration>> findBffRoutesByHost(
            Map<String, Map<String, ProxyConfiguration>> routesByHost) {
        var result = new LinkedHashMap<String, Map<String, ProxyConfiguration>>();

        routesByHost.forEach((host, routes) -> {
            var filtered = new LinkedHashMap<String, ProxyConfiguration>();
            routes.forEach((key, route) -> {
                if (isProxyHostBff(route.getProxyHost())) {
                    filtered.put(key, route);
                }
            });

            if (!filtered.isEmpty()) {
                result.put(host, filtered);
            }
        });

        return result;
    }

    private ServiceBFFTechnology deteremineServiceType(String hostURI) {
        if (quarkusService.invokeGeneric2xxEndpoint(hostURI) == Response.Status.OK.getStatusCode()) {
            return ServiceBFFTechnology.QUARKUS;
        }
        if (springBootService.invokeGeneric2xxEndpoint(hostURI) == Response.Status.OK.getStatusCode()) {
            return ServiceBFFTechnology.SPRINGBOOT;
        }
        return null;
    }

    private boolean isProxyHostBff(String hostURI) {
        return deteremineServiceType(hostURI) != null;
    }

    private void testInaccessibilityOfGenericBffEndpoints(String name, TestResponse result, String domain,
            ProxyConfiguration pc, String path) {
        var uri = createUri(domain, pc, path);
        try {
            log.info("{} path: {} proxy: {} uri: {}", name, path, pc, uri);
            var request = WebClient
                    .create(vertx, new WebClientOptions().setVerifyHost(false).setFollowRedirects(false).setTrustAll(true))
                    .requestAbs(HttpMethod.GET, uri);

            var response = request.send().await().atMost(Duration.ofSeconds(5));
            var code = response.statusCode();

            var status = code >= Response.Status.BAD_REQUEST.getStatusCode() ? TestExecution.Status.OK
                    : TestExecution.Status.FAILED;

            log.info("{}-test {} {} {}", name, uri, code, status);

            result.getExecutions().add(createExecution(path, pc.getLocation(), status, null, uri, code));

        } catch (Exception ex) {
            log.error("Error execute {} test for {} - {}, error: {}", name, path, pc.getLocation(), ex.getMessage(), ex);
            result.getExecutions().add(createExecution(path, pc.getLocation(), TestExecution.Status.OK, uri, ex.getMessage()));
        }
    }

    private void testOpenApi(TestResponse result, String domain, Map<String, ProxyConfiguration> routes,
            ProxyConfiguration fallbackProxyConfiguration, OpenAPI openapi) {
        if (openapi.getPaths() == null) {
            log.warn("No paths found in OpenAPI definition");
            return;
        }
        openapi.getPaths().getPathItems().forEach((path, item) -> {
            var proxyConfiguration = resolveProxyConfiguration(routes, fallbackProxyConfiguration, path);
            var uri = createUri(domain, proxyConfiguration, path);
            item.getOperations()
                    .forEach((method, op) -> {
                        log.info("Test operation {} for path {}", op.getOperationId(), path);
                        execute(result, uri, path, proxyConfiguration.getLocation(), method, op);
                    }

                    );
        });
    }

    private ProxyConfiguration resolveProxyConfiguration(Map<String, ProxyConfiguration> routes,
            ProxyConfiguration fallbackProxyConfiguration, String path) {
        return routes.values().stream()
                .filter(pc -> path.startsWith(normalizeServicePathKey(pc.getServicePathKey())))
                .max(Comparator.comparingInt(pc -> normalizeServicePathKey(pc.getServicePathKey()).length()))
                .orElse(fallbackProxyConfiguration);
    }

    private String normalizeServicePathKey(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        if (key.startsWith("/")) {
            return key;
        }
        return "/" + key;
    }

    private boolean hasXonecxNoSecurity(Operation op, String path) {
        if (op.getExtensions() != null
                && op.getExtensions().containsKey("x-onecx")) {

            Object xOnecx = op.getExtensions().get("x-onecx");
            if (xOnecx instanceof Map) {
                // x-onecx is a Map
                Map<String, String> xOnecxExtensions = (Map<String, String>) xOnecx;
                if (xOnecxExtensions.containsKey("security")
                        && "none".equalsIgnoreCase(xOnecxExtensions.get("security"))) {
                    log.info("Ignored operation: {} for path {} due to x-onecx security: none configuration",
                            op.getOperationId(), path);
                    return true;
                }
            }
        }
        return false;
    }

    private void execute(TestResponse result, String uri, String path, String proxyPath, PathItem.HttpMethod method,
            Operation op) {
        try {
            if (hasXonecxNoSecurity(op, path)) {
                result.getWhitelistedPaths().add(path);
                return;
            }

            var request = WebClient.create(vertx, new WebClientOptions().setVerifyHost(false).setTrustAll(true))
                    .requestAbs(io.vertx.core.http.HttpMethod.valueOf(method.name()), UriTemplate.of(uri));

            if (op.getParameters() != null) {
                op.getParameters().forEach(p -> {
                    if (Parameter.In.PATH == p.getIn()) {
                        request.setTemplateParam(p.getName(), result.getId());
                    }
                });
            }

            ExecutionOutcome outcome = executeRequest(result, request, path, proxyPath, uri);
            log.atLevel(outcome.logLevel()).log("Security test result:{}  method: {} uri: {}  httpCode: {}", outcome.status(),
                    method, uri, outcome.code());

        } catch (Exception ex) {
            result.getExecutions().add(createExecutionError(path, proxyPath, uri, ex.getMessage()));
        }
    }

    private ExecutionOutcome executeRequest(TestResponse result, HttpRequest<Buffer> request, String path, String proxyPath,
            String uri) {
        TestExecution.Status status;
        Level resultLogLevel = Level.INFO;
        int code = -1;
        try {
            HttpResponse<Buffer> response = request.send().await().atMost(Duration.ofSeconds(5));
            code = response.statusCode();

            String detailedStatus = null;
            switch (code) {
                case 401:
                    status = TestExecution.Status.OK;
                    break;
                case 403:
                    status = TestExecution.Status.WARNING;
                    detailedStatus = "Expected 401 response but got 403. This may indicate that the endpoint is protected but not properly configured to return 401 for unauthorized access.";
                    break;
                default:
                    status = TestExecution.Status.FAILED;
                    detailedStatus = "Expected 401 response but got " + code;
                    resultLogLevel = Level.ERROR;
                    break;
            }
            result.getExecutions().add(createExecution(path, proxyPath, status, detailedStatus, uri, code));
        } catch (TimeoutException ex) {
            status = TestExecution.Status.ERROR;
            resultLogLevel = Level.WARN;
            result.getExecutions().add(createExecutionError(path, proxyPath, uri, "Request timed out: " + ex.getMessage()));
        } catch (Exception ex) {
            status = TestExecution.Status.ERROR;
            resultLogLevel = Level.ERROR;
            result.getExecutions().add(createExecutionError(path, proxyPath, uri, ex.getMessage()));
        }
        return new ExecutionOutcome(status, code, resultLogLevel);
    }

    private TestExecution createExecutionError(String path, String proxyPath, String uri, String error) {
        var e = new TestExecution();
        e.setPath(path);
        e.setProxy(proxyPath);
        e.setDetailedStatus(error);
        e.setUrl(uri);
        e.setStatus(TestExecution.Status.ERROR);
        return e;
    }

    private TestExecution createExecution(String path, String proxyPath, TestExecution.Status status, String detailedStatus,
            String uri, int code) {
        var e = new TestExecution();
        e.setPath(path);
        e.setProxy(proxyPath);
        e.setStatus(status);
        e.setUrl(uri);
        e.setCode(code);
        e.setDetailedStatus(detailedStatus);
        return e;
    }

    private TestExecution createExecution(String path, String proxyPath, TestExecution.Status status, String uri,
            String detailedStatus) {
        var e = new TestExecution();
        e.setPath(path);
        e.setProxy(proxyPath);
        e.setStatus(status);
        e.setUrl(uri);
        e.setDetailedStatus(detailedStatus);
        return e;
    }

    private String url(TestRequest dto) {
        var tmp = dto.getUrl();
        if (tmp.endsWith("/")) {
            tmp = tmp.substring(0, tmp.length() - 1);
        }
        return tmp;
    }

    private String createUri(String domain, ProxyConfiguration pc, String path) {
        String mergedPath = removePathPrefix(pc.getProxyPath(), path);
        String uri = domain + pc.getLocation() + mergedPath;
        return TestServiceUriUtil.normalizeUri(uri);
    }

    /**
     * Remove common path prefix from openapi path to avoid duplicate url path
     */
    String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize object to JSON: {}", ex.getMessage());
            return String.valueOf(value);
        }
    }

    private String removePathPrefix(String proxyPassFull, String openApiPath) {
        return openApiPath.substring(TestServiceUriUtil.findOverlapLength(proxyPassFull, openApiPath));
    }
}
