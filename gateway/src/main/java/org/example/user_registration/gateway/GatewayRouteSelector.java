package org.example.user_registration.gateway;

final class GatewayRouteSelector {
    private GatewayRouteSelector() {
    }

    static Route select(String path) {
        if ("/gateway-health".equals(path)) {
            return Route.HEALTH;
        }
        if ("/api".equals(path)) {
            return Route.API_REDIRECT;
        }
        if (path.startsWith("/api/")
                || path.startsWith("/actuator/")
                || "/swagger-ui.html".equals(path)
                || path.startsWith("/swagger-ui/")
                || "/v3/api-docs".equals(path)
                || path.startsWith("/v3/api-docs/")) {
            return Route.API;
        }
        return Route.UI;
    }

    enum Route {
        HEALTH,
        API_REDIRECT,
        API,
        UI
    }
}
