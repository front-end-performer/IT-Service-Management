package de.challenge.appointments;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.HandlerMapping;
import org.yaml.snakeyaml.Yaml;

/**
 * Checks every response produced through MockMvc in any integration test against rest-api-spec.yml:
 * the operation must be documented, its status code declared, and the API headers it sends
 * ({@link #CHECKED_HEADERS}) declared for that status. A violation fails the test that produced the
 * response, so the spec cannot silently drift from the implementation.
 *
 * <p>Complements {@code SpecContractTest}, which compares paths and field names statically.
 */
@TestConfiguration(proxyBeanMethods = false)
public class SpecConformance {

    static final List<String> CHECKED_HEADERS = List.of("Location", "X-Total-Count", "Retry-After");

    private static final Map<String, Map<String, Map<String, Object>>> PATHS = loadPaths();

    @Bean
    MockMvcBuilderCustomizer specConformanceCheck() {
        return builder -> builder.alwaysDo(SpecConformance::check);
    }

    @SuppressWarnings("unchecked")
    static void check(MvcResult result) {
        String path = (String) result.getRequest().getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (path == null || !path.startsWith("/it-services")) {
            return; // static frontend resources are not part of the API
        }
        String method = result.getRequest().getMethod().toLowerCase();
        String operation = method.toUpperCase() + " " + path;
        int status = result.getResponse().getStatus();

        Map<String, Object> spec = PATHS.getOrDefault(path, Map.of()).get(method);
        if (spec == null) {
            throw new AssertionError("Undocumented operation " + operation + " – add it to rest-api-spec.yml");
        }
        var responses = (Map<Object, Map<String, Object>>) spec.get("responses");
        Map<String, Object> response = responses.get(String.valueOf(status));
        if (response == null) {
            response = responses.get(status); // YAML may parse unquoted codes as integers
        }
        if (response == null) {
            throw new AssertionError("%s answered %d, which rest-api-spec.yml does not declare (declared: %s)"
                    .formatted(operation, status, responses.keySet()));
        }
        var declaredHeaders = (Map<String, Object>) response.getOrDefault("headers", Map.of());
        for (String header : CHECKED_HEADERS) {
            if (result.getResponse().containsHeader(header) && !declaredHeaders.containsKey(header)) {
                throw new AssertionError("%s %d sends header %s, which rest-api-spec.yml does not declare for it"
                        .formatted(operation, status, header));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Map<String, Object>>> loadPaths() {
        try (InputStream in = Files.newInputStream(Path.of("rest-api-spec.yml"))) {
            return (Map<String, Map<String, Map<String, Object>>>) new Yaml().<Map<String, Object>>load(in).get("paths");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
