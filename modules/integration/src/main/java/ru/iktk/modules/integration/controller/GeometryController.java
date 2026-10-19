package ru.iktk.modules.integration.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.iktk.modules.integration.service.GeometryService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/geometry")
public class GeometryController {

    private static final Logger log = LoggerFactory.getLogger(GeometryController.class);
    private final GeometryService geometryService;

    public GeometryController(GeometryService geometryService) {
        this.geometryService = geometryService;
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<Map<String, Object>> getGeometry(
            @PathVariable String projectId,
            @RequestParam(required = false) String objectRef) {
        log.info("GET /api/v1/geometry/{}?objectRef={}", projectId, objectRef);

        Map<String, Object> body = geometryService.buildGeometry(projectId, objectRef);
        if (body == null) {
            log.warn("GET /api/v1/geometry/{}?objectRef={} -> 404 (not found)",
                    projectId, objectRef);
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(body);
    }
}