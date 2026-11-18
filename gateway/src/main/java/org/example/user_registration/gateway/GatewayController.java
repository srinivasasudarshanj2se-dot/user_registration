package org.example.user_registration.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@RestController
class GatewayController {
    private static final Logger logger = LoggerFactory.getLogger(GatewayController.class);
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "proxy-connection", "te", "trailer", "transfer-encoding", "upgrade",
            "host", "content-length");

    private final WebClient webClient;
    private final URI apiUpstream;
    private final URI uiUpstream;

    GatewayController(
            WebClient gatewayWebClient,
            @Value("${gateway.api-upstream}") String apiUpstream,
            @Value("${gateway.ui-upstream}") String uiUpstream) {
        this.webClient = gatewayWebClient;
        this.apiUpstream = validateUpstream(apiUpstream, "gateway.api-upstream");
        this.uiUpstream = validateUpstream(uiUpstream, "gateway.ui-upstream");
    }

    @RequestMapping("/**")
    Mono<Void> proxy(ServerWebExchange exchange) {
        ServerHttpRequest incomingRequest = exchange.getRequest();
        String path = incomingRequest.getURI().getRawPath();
        return switch (GatewayRouteSelector.select(path)) {
            case HEALTH -> writeHealth(exchange.getResponse());
            case API_REDIRECT -> redirectToApiRoot(exchange);
            case API -> forward(exchange, apiUpstream);
            case UI -> forward(exchange, uiUpstream);
        };
    }

    private Mono<Void> writeHealth(ServerHttpResponse response) {
        byte[] body = "ok\n".getBytes(StandardCharsets.UTF_8);
        DataBufferFactory bufferFactory = response.bufferFactory();
        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().setContentType(MediaType.TEXT_PLAIN);
        response.getHeaders().setContentLength(body.length);
        return response.writeWith(Mono.just(bufferFactory.wrap(body)));
    }

    private Mono<Void> redirectToApiRoot(ServerWebExchange exchange) {
        URI requestUri = exchange.getRequest().getURI();
        String location = "/api/" + (requestUri.getRawQuery() == null ? "" : "?" + requestUri.getRawQuery());
        exchange.getResponse().setStatusCode(HttpStatus.PERMANENT_REDIRECT);
        exchange.getResponse().getHeaders().setLocation(URI.create(location));
        return exchange.getResponse().setComplete();
    }

    private Mono<Void> forward(ServerWebExchange exchange, URI upstream) {
        ServerHttpRequest incomingRequest = exchange.getRequest();
        URI targetUri = targetUri(upstream, incomingRequest.getURI());
        HttpMethod method = incomingRequest.getMethod();
        if (method == null) {
            return Mono.error(new IllegalStateException("Incoming request has no HTTP method"));
        }

        return webClient.method(method)
                .uri(targetUri)
                .headers(headers -> copyRequestHeaders(incomingRequest, headers))
                .body(BodyInserters.fromDataBuffers(incomingRequest.getBody()))
                .exchangeToMono(upstreamResponse -> {
                    ServerHttpResponse outgoingResponse = exchange.getResponse();
                    outgoingResponse.setStatusCode(upstreamResponse.statusCode());
                    copyResponseHeaders(upstreamResponse.headers().asHttpHeaders(), outgoingResponse.getHeaders());
                    return outgoingResponse.writeWith(upstreamResponse.bodyToFlux(DataBuffer.class));
                })
                .onErrorResume(WebClientRequestException.class, exception -> {
                    logger.warn("Gateway upstream request failed for {} {}", method, targetUri, exception);
                    ServerHttpResponse response = exchange.getResponse();
                    response.setStatusCode(HttpStatus.BAD_GATEWAY);
                    response.getHeaders().setContentType(MediaType.TEXT_PLAIN);
                    byte[] body = "Upstream service unavailable\n".getBytes(StandardCharsets.UTF_8);
                    response.getHeaders().setContentLength(body.length);
                    return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
                });
    }

    private void copyRequestHeaders(ServerHttpRequest request, HttpHeaders target) {
        HttpHeaders source = request.getHeaders();
        Set<String> excluded = excludedHeaders(source);
        source.forEach((name, values) -> {
            if (!excluded.contains(name.toLowerCase(Locale.ROOT))) {
                target.put(name, new ArrayList<>(values));
            }
        });

        String remoteAddress = request.getRemoteAddress() == null
                || request.getRemoteAddress().getAddress() == null
                ? null
                : request.getRemoteAddress().getAddress().getHostAddress();
        target.remove("X-Forwarded-For");
        if (remoteAddress != null) {
            target.set("X-Forwarded-For", remoteAddress);
        }
        if (request.getURI().getScheme() != null) {
            target.set("X-Forwarded-Proto", request.getURI().getScheme());
        }
        String originalHost = source.getFirst(HttpHeaders.HOST);
        if (originalHost != null) {
            target.set("X-Forwarded-Host", originalHost);
        }
    }

    private void copyResponseHeaders(HttpHeaders source, HttpHeaders target) {
        Set<String> excluded = excludedHeaders(source);
        source.forEach((name, values) -> {
            if (!excluded.contains(name.toLowerCase(Locale.ROOT))) {
                target.put(name, new ArrayList<>(values));
            }
        });
    }

    private Set<String> excludedHeaders(HttpHeaders headers) {
        Set<String> excluded = new HashSet<>(HOP_BY_HOP_HEADERS);
        headers.getOrEmpty(HttpHeaders.CONNECTION).stream()
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(value -> !value.isEmpty())
                .forEach(excluded::add);
        return excluded;
    }

    private URI targetUri(URI upstream, URI requestUri) {
        String pathAndQuery = requestUri.getRawPath();
        if (requestUri.getRawQuery() != null) {
            pathAndQuery += "?" + requestUri.getRawQuery();
        }
        return URI.create(upstream.toASCIIString() + pathAndQuery);
    }

    private URI validateUpstream(String value, String propertyName) {
        URI uri = URI.create(value);
        String scheme = uri.getScheme();
        if (uri.getHost() == null
                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || uri.getRawUserInfo() != null
                || (uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !"/".equals(uri.getRawPath()))
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw new IllegalStateException(propertyName + " must be an http(s) origin without credentials, path, query, or fragment");
        }
        return URI.create(uri.getScheme() + "://" + uri.getRawAuthority());
    }
}
