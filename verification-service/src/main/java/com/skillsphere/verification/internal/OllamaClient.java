package com.skillsphere.verification.internal;

import com.skillsphere.shared.error.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;

/**
 * A thin, direct line to a local Ollama server — no abstraction layer between
 * this and the HTTP call, because there is exactly one caller
 * ({@link VivaQuestionGenerator}) and a generic "LLM provider" interface would
 * be speculative generality for a project with no second provider.
 *
 * <p>Every prompt this client sends asks for {@code format: "json"}, Ollama's
 * constrained-output mode. A 3B model asked for free-form prose about a
 * rubric will drift; asked for JSON matching a described shape, it reliably
 * produces parseable output. Callers still must not trust the JSON blindly —
 * {@code format: "json"} guarantees syntactic validity, not that the fields
 * the caller expects are actually present — which is why every caller here
 * parses defensively and has a fallback for a response that doesn't shape up.
 */
@Slf4j
@Component
@EnableConfigurationProperties(OllamaProperties.class)
public class OllamaClient {

    private final RestClient restClient;
    private final OllamaProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OllamaClient(OllamaProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * Thrown when Ollama is unreachable or returns something unusable.
     *
     * <p>Extends {@link ServiceUnavailableException} rather than a plain
     * {@code RuntimeException} so {@link com.skillsphere.shared.error.GlobalExceptionHandler}
     * routes it through the {@code ApiException} branch and this message —
     * "is Ollama running?" — actually reaches whoever is presenting a demo,
     * instead of being swallowed into a generic "something went wrong" and
     * logged where only the developer can see it.
     */
    public static class OllamaUnavailableException extends ServiceUnavailableException {
        public OllamaUnavailableException(String message, Throwable cause) {
            super("OLLAMA_UNAVAILABLE", message, cause);
        }
    }

    /**
     * Sends one prompt, gets back the raw JSON text the model produced.
     * Callers parse it themselves — this method's job ends at "the HTTP call
     * to Ollama succeeded and returned some text."
     */
    private static final String CB_NAME = "ollama";

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "generateJsonUnavailable")
    @Retry(name = CB_NAME)
    public String generateJson(String systemPrompt, String userPrompt) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.model());
        body.put("system", systemPrompt);
        body.put("prompt", userPrompt);
        body.put("format", "json");
        body.put("stream", false);
        ObjectNode options = body.putObject("options");
        // Low temperature: this is grading and question generation against a
        // rubric, not creative writing — consistency matters more than variety.
        options.put("temperature", 0.3);

        try {
            String raw = restClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);
            var responseNode = objectMapper.readTree(raw);
            String response = responseNode.path("response").asString("");
            if (response.isBlank()) {
                throw new OllamaUnavailableException("Ollama returned an empty response", null);
            }
            return response;
        } catch (RestClientException e) {
            log.error("Ollama call failed", e);
            throw new OllamaUnavailableException(
                    "Could not reach the local model at " + properties.baseUrl()
                            + ". Is Ollama running? (ollama serve)", e);
        }
    }

    public String model() {
        return properties.model();
    }

    @SuppressWarnings("unused")
    private String generateJsonUnavailable(String systemPrompt, String userPrompt, Throwable cause) {
        log.warn("Ollama circuit breaker open or call failed: {}", cause.toString());
        throw new OllamaUnavailableException(
                "Could not reach the local model at " + properties.baseUrl()
                        + ". Is Ollama running? (ollama serve)", cause);
    }
}
