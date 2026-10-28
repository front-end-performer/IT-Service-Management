package de.challenge.appointments.api;

import static org.assertj.core.api.Assertions.assertThat;

import de.challenge.appointments.IntegrationTest;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

/**
 * Keeps the implementation and rest-api-spec.yml (Swagger 2.0, the source of truth) in sync:
 * every documented operation exists, nothing undocumented is exposed, and the JSON field names
 * of the DTOs are exactly the documented properties.
 */
class SpecContractTest extends IntegrationTest {

    private static Map<String, Object> spec;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void loadSpec() throws Exception {
        try (InputStream in = Files.newInputStream(Path.of("rest-api-spec.yml"))) {
            spec = new Yaml().load(in);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void implementedOperationsMatchTheSpec() {
        Set<String> documented = new TreeSet<>();
        ((Map<String, Map<String, Object>>) spec.get("paths")).forEach((path, operations) ->
                operations.keySet().forEach(method -> documented.add(method.toUpperCase() + " " + path)));

        Set<String> implemented = new TreeSet<>();
        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            if (method.getBeanType().getPackageName().equals(SpecContractTest.class.getPackageName())) {
                info.getPatternValues().forEach(path -> info.getMethodsCondition().getMethods()
                        .forEach(m -> implemented.add(m.name() + " " + path)));
            }
        });

        assertThat(implemented).isEqualTo(documented);
    }

    @Test
    void dtoFieldsMatchTheSpecDefinitions() {
        assertFields("itService", Dtos.ItServiceDto.class);
        assertFields("repairService", Dtos.RepairServiceDto.class);
        assertFields("appointment", Dtos.AppointmentDto.class);
        assertFields("appointmentRequest", Dtos.AppointmentRequest.class);
        assertFields("appointmentSuggestion", Dtos.AppointmentSuggestionDto.class);
        assertFields("importReport", Dtos.ImportReportDto.class);
        assertFields("importRejection", Dtos.RejectionDto.class);
    }

    @SuppressWarnings("unchecked")
    private static void assertFields(String definition, Class<? extends Record> dto) {
        var definitions = (Map<String, Map<String, Object>>) spec.get("definitions");
        Set<String> documented = ((Map<String, Object>) definitions.get(definition).get("properties")).keySet();
        List<String> required = (List<String>) definitions.get(definition).getOrDefault("required", List.of());
        Set<String> fields = new TreeSet<>(Arrays.stream(dto.getRecordComponents()).map(c -> c.getName()).toList());

        assertThat(fields).as(definition).isEqualTo(new TreeSet<>(documented));
        assertThat(fields).as(definition + " required").containsAll(required);
    }
}
