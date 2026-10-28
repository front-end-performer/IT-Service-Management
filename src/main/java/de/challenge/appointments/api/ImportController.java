package de.challenge.appointments.api;

import de.challenge.appointments.api.Dtos.ImportReportDto;
import de.challenge.appointments.common.InvalidRequestException;
import de.challenge.appointments.imports.ImportService;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * CSV import of legacy appointments – an extension of rest-api-spec.yml. The provider comes from
 * the path, so a file is always unambiguously assigned to exactly one provider.
 */
@RestController
@RequestMapping("/it-services/{serviceId}/appointment-imports")
class ImportController {

    private final ImportService imports;

    ImportController(ImportService imports) {
        this.imports = imports;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ImportReportDto> importAppointments(@PathVariable String serviceId,
                                                       @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new InvalidRequestException("Uploaded file is empty");
        }
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            var batch = imports.importCsv(serviceId, file.getOriginalFilename(), reader);
            return ResponseEntity.created(URI.create("/it-services/%s/appointment-imports/%s"
                            .formatted(serviceId, batch.getId())))
                    .body(ImportReportDto.of(batch, true));
        }
    }

    @GetMapping
    List<ImportReportDto> getImports(@PathVariable String serviceId) {
        return imports.list(serviceId).stream().map(b -> ImportReportDto.of(b, false)).toList();
    }

    @GetMapping("/{importId}")
    ImportReportDto getImport(@PathVariable String serviceId, @PathVariable String importId) {
        return ImportReportDto.of(imports.get(serviceId, importId), true);
    }
}
