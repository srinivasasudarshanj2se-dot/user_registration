package org.example.user_registration.gateway;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GatewayControllerTest {
    private DisposableServer apiServer;
    private DisposableServer uiServer;
    private WebTestClient client;
    private final AtomicReference<String> apiRequestUri = new AtomicReference<>();
    private final AtomicReference<String> apiRequestMethod = new AtomicReference<>();
    private final AtomicReference<String> forwardedProto = new AtomicReference<>();
    private final AtomicReference<String> forwardedHost = new AtomicReference<>();
    private final AtomicReference<String> forwardedFor = new AtomicReference<>();
    private final AtomicReference<String> requestHeader = new AtomicReference<>();
    private final AtomicReference<String> removedRequestHeader = new AtomicReference<>();

    @BeforeEach
    void startUpstreams() {
        apiServer = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> {
                    apiRequestUri.set(request.uri());
                    apiRequestMethod.set(request.method().name());
                    forwardedProto.set(request.requestHeaders().get("X-Forwarded-Proto"));
                    forwardedHost.set(request.requestHeaders().get("X-Forwarded-Host"));
                    forwardedFor.set(request.requestHeaders().get("X-Forwarded-For"));
                    requestHeader.set(request.requestHeaders().get("X-Test-Header"));
                    removedRequestHeader.set(request.requestHeaders().get("X-Remove-Request"));
                    return response.status(HttpStatus.CREATED.value())
                            .header("X-Upstream", "api")
                            .header(HttpHeaders.CONNECTION, "X-Remove-Response")
                            .header("X-Remove-Response", "secret")
                            .sendString(Mono.just("api response"));
                })
                .bindNow();
        uiServer = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> response.sendString(Mono.just("ui response")))
                .bindNow();
        client = createClient(apiServer, uiServer);
    }

    @AfterEach
    void stopUpstreams() {
        apiServer.disposeNow();
        uiServer.disposeNow();
    }

    @Test
    void respondsToHealthAndRedirectsApiRootWithQueryString() {
        client.get().uri("/gateway-health").exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.TEXT_PLAIN)
                .expectBody(String.class).isEqualTo("ok\n");

        client.get().uri("/api?source=docs").exchange()
                .expectStatus().isPermanentRedirect()
                .expectHeader().valueEquals(HttpHeaders.LOCATION, "/api/?source=docs");
    }

    @Test
    void forwardsApiRequestsAndFiltersHopByHopHeaders() {
        client.post().uri(URI.create("http://localhost/api/v1/users?email=alice%40example.com"))
                .header(HttpHeaders.HOST, "localhost")
                .header("X-Test-Header", "forwarded")
                .header(HttpHeaders.CONNECTION, "X-Remove-Request")
                .header("X-Remove-Request", "secret")
                .bodyValue("request body")
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals("X-Upstream", "api")
                .expectHeader().doesNotExist("X-Remove-Response")
                .expectBody(String.class).isEqualTo("api response");

        assertEquals("POST", apiRequestMethod.get());
        assertEquals("/api/v1/users?email=alice%40example.com", apiRequestUri.get());
        assertEquals("forwarded", requestHeader.get());
        assertEquals("http", forwardedProto.get());
        assertEquals("localhost", forwardedHost.get());
        assertNull(removedRequestHeader.get());
        assertNull(forwardedFor.get());
    }

    @Test
    void forwardsNonApiRoutesToTheUi() {
        client.get().uri("/account").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("ui response");
    }

    @Test
    void returnsBadGatewayWhenTheApiUpstreamCannotBeReached() {
        apiServer.disposeNow();

        client.get().uri("/api/v1/users").exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY)
                .expectHeader().contentType(MediaType.TEXT_PLAIN)
                .expectBody(String.class).isEqualTo("Upstream service unavailable\n");
    }

    @Test
    void rejectsUpstreamsThatAreNotHttpOrigins() {
        WebClient webClient = new WebClientConfiguration().gatewayWebClient();
        assertThrows(IllegalStateException.class,
                () -> new GatewayController(webClient, "file://api", "http://ui"));
        assertThrows(IllegalStateException.class,
                () -> new GatewayController(webClient, "http://user:pass@api", "http://ui"));
        assertThrows(IllegalStateException.class,
                () -> new GatewayController(webClient, "http://api/path", "http://ui"));
        assertThrows(IllegalStateException.class,
                () -> new GatewayController(webClient, "http://api?query=value", "http://ui"));
        assertThrows(IllegalStateException.class,
                () -> new GatewayController(webClient, "http://api#fragment", "http://ui"));
    }

    private static WebTestClient createClient(DisposableServer api, DisposableServer ui) {
        WebClient webClient = new WebClientConfiguration().gatewayWebClient();
        GatewayController controller = new GatewayController(webClient,
                "http://127.0.0.1:" + api.port(),
                "http://127.0.0.1:" + ui.port());
        return WebTestClient.bindToController(controller).build();
    }
}
