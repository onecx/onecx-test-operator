package org.tkit.onecx.test.operator.rs.v1.controllers;

import java.time.Duration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.parameters.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tkit.onecx.test.domain.services.K8sService;
import org.tkit.onecx.test.domain.services.NginxService;
import org.tkit.onecx.test.domain.services.QuarkusService;

import gen.org.tkit.onecx.test.operator.rs.v1.TestApiService;
import gen.org.tkit.onecx.test.operator.rs.v1.model.ExecuteSecurityTestRequestDTO;
import io.vertx.core.http.HttpMethod;
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
    NginxService nginxService;

    @Inject
    QuarkusService quarkusService;

    @Inject
    Vertx vertx;

    @Override
    public Response executeSecurityTest(ExecuteSecurityTestRequestDTO dto) {

        var pods = k8sService.findPodsForService(dto.getService());
        if (pods.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("no pods found").build();
        }
        var nginxConfig = k8sService.execCommandOnPod(pods.get(0), CMD_CONFIG);
        if (nginxConfig == null || nginxConfig.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("no nginx config found").build();
        }
        var proxyPassLocations = nginxService.getProxyPassLocation(nginxConfig);
        if (proxyPassLocations.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("no proxy pass locations found").build();
        }

        var url = dto.getUrl();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        for (var e : proxyPassLocations.entrySet()) {
            var path = e.getValue();
            var proxyPath = e.getKey();

            log.info("Path: {} proxy: {}", path, proxyPath);
            var openapi = quarkusService.getOpenApi(path);

            testOpenApi(dto.getId(), url, proxyPath, openapi);
        }
        return Response.ok(proxyPassLocations).build();
    }

    private void testOpenApi(String uuid, String domain, String proxyPath, OpenAPI openapi) {
        if (openapi == null) {
            return;
        }
        if (openapi.getPaths() == null || openapi.getPaths().getPathItems() == null) {
            return;
        }

        for (var pathItem : openapi.getPaths().getPathItems().entrySet()) {
            var path = pathItem.getKey();
            var item = pathItem.getValue();
            if (item.getOperations() == null) {
                continue;
            }

            for (var tmp : item.getOperations().entrySet()) {
                var op = tmp.getValue();
                var method = tmp.getKey().name();

                var uri = domain + proxyPath + path;
                log.info("Test {} {}", method, uri);

                var request = WebClient.create(vertx, new WebClientOptions().setVerifyHost(false).setTrustAll(true))
                        .requestAbs(HttpMethod.valueOf(method.toUpperCase()), UriTemplate.of(uri));

                if (op.getParameters() != null) {
                    op.getParameters().stream()
                            .filter(p -> p.getIn() == Parameter.In.PATH)
                            .forEach(p -> request.setTemplateParam(p.getName(), uuid));
                }

                var response = request.send().await().atMost(Duration.ofSeconds(5));
                var code = response.statusCode();
                if (code != 401) {
                    log.error("Test failed {} {} {} {} {}", method, domain, proxyPath, path, code);
                }

            }
        }
    }

}
