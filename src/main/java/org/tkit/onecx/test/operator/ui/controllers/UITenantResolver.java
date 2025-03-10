package org.tkit.onecx.test.operator.ui.controllers;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.oidc.TenantResolver;
import io.vertx.ext.web.RoutingContext;

@ApplicationScoped
public class UITenantResolver implements TenantResolver {

    /**
     * Split tenant security for rest-api and ui page.
     */
    @Override
    public String resolve(RoutingContext context) {
        String path = context.request().path();
        if (path.startsWith("/ui")) {
            return "ui";
        }
        return null;
    }
}
