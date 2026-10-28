package ru.iktk.gateway.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("FallbackController — 503 при недоступном downstream")
class FallbackControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("GET /fallback/orchestrator → 503 + JSON")
    void fallback_orchestrator_503() {
        webTestClient.get().uri("/fallback/orchestrator")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().exists(FallbackController.HEADER_REQUEST_ID)
                .expectBody()
                .jsonPath("$.error").isEqualTo("SERVICE_UNAVAILABLE")
                .jsonPath("$.service").isEqualTo("orchestrator")
                .jsonPath("$.message").isNotEmpty()
                .jsonPath("$.requestId").isNotEmpty()
                .jsonPath("$.timestamp").isNotEmpty();
    }

    @Test
    @DisplayName("X-Request-Id пробрасывается в ответ")
    void fallback_propagatesRequestId() {
        String rid = "test-rid-12345";
        webTestClient.get().uri("/fallback/bom")
                .header(FallbackController.HEADER_REQUEST_ID, rid)
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().valueEquals(FallbackController.HEADER_REQUEST_ID, rid)
                .expectBody()
                .jsonPath("$.requestId").isEqualTo(rid)
                .jsonPath("$.service").isEqualTo("bom");
    }

    @Test
    @DisplayName("Без X-Request-Id → генерируется UUID")
    void fallback_generatesRequestId() {
        webTestClient.get().uri("/fallback/unknown-service")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.service").isEqualTo("unknown-service")
                .jsonPath("$.requestId").isNotEmpty();
    }
}