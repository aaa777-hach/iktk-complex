package ru.iktk.modules.integration.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.iktk.modules.integration.dto.RawBomDto;
import ru.iktk.modules.integration.model.Geometry;
import ru.iktk.modules.integration.parser.StepAssemblyParser;
import ru.iktk.modules.integration.service.GeometryEstimateService;
import ru.iktk.modules.integration.service.GeometryService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/v1/geometry")
public class GeometryController {

    private static final Logger log = LoggerFactory.getLogger(GeometryController.class);

    private final GeometryService geometryService;
    private final GeometryEstimateService estimateService;
    private final StepAssemblyParser parser;
    private final Path cadDir;

    public GeometryController(GeometryService geometryService,
                              GeometryEstimateService estimateService,
                              StepAssemblyParser parser,
                              @Value("${integration.cad.directory}") String cadDir) {
        this.geometryService = geometryService;
        this.estimateService = estimateService;
        this.parser = parser;
        this.cadDir = Path.of(cadDir);
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<Map<String, Object>> getGeometry(
            @PathVariable String projectId,
            @RequestParam(required = false) String objectRef) {
        log.info("GET /api/v1/geometry/{}?objectRef={}", projectId, objectRef);

        Map<String, Object> body = geometryService.buildGeometry(projectId, stripExtension(objectRef));
        if (body == null) {
            log.warn("GET /api/v1/geometry/{}?objectRef={} -> 404 (not found)",
                    projectId, objectRef);
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(body);
    }

    /**
     * Сессия 33 (путь А): эвристическая оценка геометрии из STEP + сохранение в БД.
     *
     * <p>Резолвит {@code objectRef} -> файл в {@code integration.cad.directory},
     * парсит структуру, строит оценку, сохраняет {@code geometry}.
     * В ответе — все поля + {@code warnings[]} (что именно приблизительно).
     */
    @PostMapping("/from-step")
    public ResponseEntity<Map<String, Object>> fromStep(@RequestBody FromStepRequest request)
            throws IOException {

        if (request == null || request.getObjectRef() == null || request.getObjectRef().isBlank()) {
            return ResponseEntity.badRequest().body(errorBody("INVALID_REQUEST", "objectRef is required"));
        }
        log.info("POST /api/v1/geometry/from-step: objectRef={}, projectId={}, overwrite={}",
                request.getObjectRef(), request.getProjectId(), request.isOverwrite());

        Path file = resolve(request.getObjectRef());
        RawBomDto bom = parser.parse(file);
        GeometryEstimateService.EstimateResult estimate =
                estimateService.estimate(bom, request.getMaterial());

        String objectRefKey = stripExtension(request.getObjectRef());
        Geometry saved = geometryService.upsertFromStep(
                objectRefKey, request.getProjectId(), estimate, request.isOverwrite());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("objectRef", saved.getObjectRef());
        body.put("projectId", saved.getProjectId());
        body.put("material", saved.getMaterial());
        body.put("volume", saved.getVolume());
        body.put("mass", saved.getMass());
        body.put("minWallThickness", saved.getMinWallThickness());
        body.put("minFilletRadius", saved.getMinFilletRadius());
        body.put("minDraftAngle", saved.getMinDraftAngle());
        body.put("nominalDimensions", saved.getNominalDimensions());
        body.put("complexityScore", saved.getComplexityScore());
        body.put("undercuts", saved.isUndercuts());
        body.put("hotSpots", saved.isHotSpots());
        body.put("source", "STEP_ESTIMATE");
        body.put("rootId", bom.getRootId());
        body.put("itemsCount", bom.getItems() == null ? 0 : bom.getItems().size());
        body.put("warnings", estimate.getWarnings());

        log.info("POST /from-step done: objectRef={}, warnings={}", saved.getObjectRef(),
                estimate.getWarnings().size());
        return ResponseEntity.ok(body);
    }

    private static String stripExtension(String name) {
        if (name == null) return null;
        String lower = name.toLowerCase();
        if (lower.endsWith(".step") || lower.endsWith(".stp")) {
            return name.substring(0, name.lastIndexOf('.'));
        }
        return name;
    }

    // ===== resolve (копия из StepBomController, чтобы не менять 6 его тестов) =====
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
        try (Stream<Path> s = Files.list(cadDir)) {
            Path found = s.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        if (!n.endsWith(".step") && !n.endsWith(".stp")) return false;
                        String stem = n.substring(0, n.lastIndexOf('.'));
                        return stem.equalsIgnoreCase(base);
                    })
                    .findFirst().orElse(null);
            if (found != null) return found;
        }
        throw new NoSuchFileException("STEP не найден: " + objectRef + " в " + cadDir);
    }

    private Map<String, Object> errorBody(String code, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", code);
        m.put("message", message);
        return m;
    }

    @ExceptionHandler(NoSuchFileException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoSuchFileException e) {
        log.warn("404 /from-step: {}", e.getMessage());
        return ResponseEntity.status(404).body(errorBody("STEP_FILE_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        log.warn("400 /from-step: {}", e.getMessage());
        return ResponseEntity.badRequest().body(errorBody("INVALID_STEP", e.getMessage()));
    }

    @ExceptionHandler(GeometryService.AlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(GeometryService.AlreadyExistsException e) {
        log.warn("409 /from-step: {}", e.getMessage());
        return ResponseEntity.status(409).body(errorBody("ALREADY_EXISTS", e.getMessage()));
    }

    /** Тело запроса POST /from-step. */
    public static class FromStepRequest {
        private String objectRef;
        private String projectId;
        private String material;
        private boolean overwrite = true;

        public String getObjectRef() { return objectRef; }
        public void setObjectRef(String objectRef) { this.objectRef = objectRef; }
        public String getProjectId() { return projectId; }
        public void setProjectId(String projectId) { this.projectId = projectId; }
        public String getMaterial() { return material; }
        public void setMaterial(String material) { this.material = material; }
        public boolean isOverwrite() { return overwrite; }
        public void setOverwrite(boolean overwrite) { this.overwrite = overwrite; }
    }
}