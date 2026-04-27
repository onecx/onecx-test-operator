package org.tkit.onecx.test.domain.services;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.openapi.models.OpenAPI;

import io.smallrye.openapi.api.OpenApiConfig;
import io.smallrye.openapi.runtime.io.Format;
import io.smallrye.openapi.runtime.io.IOContext;
import io.smallrye.openapi.runtime.io.JsonIO;
import io.smallrye.openapi.runtime.io.OpenAPIDefinitionIO;

public interface BackendService {

    int invokeGeneric2xxEndpoint(String url);

    OpenAPI getOpenApi(String url);

    String getOpenApiSchema(String url);

    default OpenAPI parse(String stream) {
        var format = stream.stripLeading().startsWith("{") ? Format.JSON : Format.YAML;
        var jsonio = JsonIO.newInstance(OpenApiConfig.fromConfig(ConfigProvider.getConfig()));
        var context = IOContext.forJson(jsonio);
        return new OpenAPIDefinitionIO<>(context).readValue(jsonio.fromString(stream, format));
    }

    String resolveOpenApiPath(String url);
}
