package org.example.user_registration.gateway;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GatewayRouteSelectorTest {
    @Test
    void routesApiAndDocumentationPathsToApi() {
        assertEquals(GatewayRouteSelector.Route.API, GatewayRouteSelector.select("/api/v1/users"));
        assertEquals(GatewayRouteSelector.Route.API, GatewayRouteSelector.select("/actuator/health"));
        assertEquals(GatewayRouteSelector.Route.API, GatewayRouteSelector.select("/swagger-ui.html"));
        assertEquals(GatewayRouteSelector.Route.API, GatewayRouteSelector.select("/v3/api-docs"));
    }

    @Test
    void keepsHealthAndApiRootRedirectSeparate() {
        assertEquals(GatewayRouteSelector.Route.HEALTH, GatewayRouteSelector.select("/gateway-health"));
        assertEquals(GatewayRouteSelector.Route.API_REDIRECT, GatewayRouteSelector.select("/api"));
    }

    @Test
    void routesRemainingPathsToUi() {
        assertEquals(GatewayRouteSelector.Route.UI, GatewayRouteSelector.select("/"));
        assertEquals(GatewayRouteSelector.Route.UI, GatewayRouteSelector.select("/account"));
    }
}
