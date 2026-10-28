package ru.iktk.modules.integration.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.iktk.modules.integration.dto.RawBomDto;
import ru.iktk.modules.integration.parser.StepAssemblyParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * BOM из реального STEP-файла (многоуровневая сборка).
 *
 * <p>Резолвит {@code objectRef} -> файл в {@code integration.cad.directory}
 * (регистронезависимо, расширения .STEP/.step/.stp), парсит через
 * {@link StepAssemblyParser}. Старый {@code /bom} (DB-only) не тронут.
 */
@RestController
@RequestMapping("/api/v1/integration")
public class StepBomController {

    private static final Logger log = LoggerFactory.getLogger(StepBomController.class);

    private final StepAssemblyParser parser;
    private final Path cadDir;

    public StepBomController(StepAssemblyParser parser,
                             @Value("${integration.cad.directory}") String cadDir) {
        this.parser = parser;
        this.cadDir = Path.of(cadDir);
    }

    @GetMapping("/bom/step")
    public ResponseEntity<RawBomDto> extractStepBom(
            @RequestParam String projectId,
            @RequestParam String objectRef) throws IOException {
        log.info("GET /bom/step: projectId={}, objectRef={}, cadDir={}", projectId, objectRef, cadDir);
        Path file = resolve(objectRef);
        RawBomDto bom = parser.parse(file);
        log.info("GET /bom/step: file={}, rootId={}, items={}",
                file.getFileName(), bom.getRootId(), bom.getItems() == null ? 0 : bom.getItems().size());
        return ResponseEntity.ok(bom);
    }

    private Path resolve(String objectRef) throws IOException {
        if (objectRef == null || objectRef.isBlank()) {
            throw new IllegalArgumentException("objectRef пуст");
        }
        String base = objectRef.trim();
        if (base.contains("..") || base.contains("/") || base.contains("\\")) {
            throw new IllegalArgumentException("objectRef содержит недопустимые символы: " + objectRef);
        }
        if (!Files.isDirectory(cadDir)) {
            throw new NoSuchFileException("cad.directory не существует: " + cadDir);
        }
        String lower = base.toLowerCase();
        boolean hasExt = lower.endsWith(".step") || lower.endsWith(".stp");
        if (hasExt) {
            Path direct = cadDir.resolve(base);
            if (Files.isRegularFile(direct)) return direct;
        } else {
            for (String ext : new String[]{".STEP", ".step", ".Step", ".stp", ".STP"}) {
                Path p = cadDir.resolve(base + ext);
                if (Files.isRegularFile(p)) return p;
            }
        }
        // case-insensitive fallback по имени без расширения
        try (Stream<Path> s = Files.list(cadDir)) {
            Path found = s.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        int dot = n.lastIndexOf('.');
                        String stem = dot < 0 ? n : n.substring(0, dot);
                        return stem.equalsIgnoreCase(base);
                    })
                    .findFirst().orElse(null);
            if (found != null) return found;
        }
        throw new NoSuchFileException(base + " в " + cadDir);
    }

    @ExceptionHandler(NoSuchFileException.class)
    public ResponseEntity<Map<String, Object>> onNotFound(NoSuchFileException e) {
        log.warn("GET /bom/step: 404 {}", e.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "STEP_FILE_NOT_FOUND");
        body.put("message", e.getMessage());
        return ResponseEntity.status(404).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> onBadRequest(IllegalArgumentException e) {
        log.warn("GET /bom/step: 400 {}", e.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "INVALID_STEP");
        body.put("message", e.getMessage());
        return ResponseEntity.badRequest().body(body);
    }
}