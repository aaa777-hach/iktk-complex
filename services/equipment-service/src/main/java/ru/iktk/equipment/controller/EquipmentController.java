package ru.iktk.equipment.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.iktk.equipment.model.EquipmentGapAnalysis;
import ru.iktk.equipment.model.EquipmentRequirement;
import ru.iktk.equipment.model.EquipmentStatus;
import ru.iktk.equipment.model.EquipmentTooling;
import ru.iktk.equipment.service.EquipmentService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/equipment")
public class EquipmentController {

    private static final Logger log = LoggerFactory.getLogger(EquipmentController.class);

    private final EquipmentService equipmentService;

    public EquipmentController(EquipmentService equipmentService) {
        this.equipmentService = equipmentService;
    }

    // ------------------------------------------------------------------
    // Health и проверка доступности метода
    // ------------------------------------------------------------------

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "equipment-service"
        ));
    }

    @GetMapping(value = "/is-method-available")
    public boolean isMethodAvailable(
            @RequestParam("projectId") String projectId,
            @RequestParam("method") String method) {
        log.info("isMethodAvailable: projectId={}, method={}", projectId, method);
        return equipmentService.isMethodAvailable(method);
    }

    // ------------------------------------------------------------------
    // Статус и загрузка
    // ------------------------------------------------------------------

    @GetMapping("/{equipmentId}/status")
    public ResponseEntity<EquipmentStatus> getStatus(@PathVariable String equipmentId) {
        return equipmentService.findStatus(equipmentId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{equipmentId}/status")
    public ResponseEntity<EquipmentStatus> updateStatus(
            @PathVariable String equipmentId,
            @RequestBody EquipmentStatus payload) {
        try {
            EquipmentStatus saved = equipmentService.saveStatus(equipmentId, payload);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ------------------------------------------------------------------
    // Совместимость с оснасткой
    // ------------------------------------------------------------------

    @GetMapping("/{equipmentId}/tooling")
    public ResponseEntity<java.util.List<EquipmentTooling>> listTooling(
            @PathVariable String equipmentId) {
        return ResponseEntity.ok(equipmentService.listToolingByEquipment(equipmentId));
    }

    @PostMapping("/{equipmentId}/tooling")
    public ResponseEntity<EquipmentTooling> addTooling(
            @PathVariable String equipmentId,
            @RequestBody ToolingRequest req) {
        try {
            EquipmentTooling saved = equipmentService.addTooling(
                    equipmentId, req.toolingId(), req.compatibility(), req.notes());
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{equipmentId}/tooling/{toolingId}")
    public ResponseEntity<Void> removeTooling(
            @PathVariable String equipmentId,
            @PathVariable String toolingId) {
        return equipmentService.removeTooling(equipmentId, toolingId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    // ------------------------------------------------------------------
    // Требования к оборудованию
    // ------------------------------------------------------------------

    @GetMapping("/requirements")
    public ResponseEntity<java.util.List<EquipmentRequirement>> listRequirements(
            @RequestParam("projectId") String projectId) {
        return ResponseEntity.ok(equipmentService.listRequirements(projectId));
    }

    @PostMapping("/requirements")
    public ResponseEntity<EquipmentRequirement> createRequirement(
            @RequestBody EquipmentRequirement payload) {
        EquipmentRequirement saved = equipmentService.saveRequirement(payload);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @DeleteMapping("/requirements/{id}")
    public ResponseEntity<Void> deleteRequirement(@PathVariable String id) {
        return equipmentService.deleteRequirement(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    // ------------------------------------------------------------------
    // Gap-анализ
    // ------------------------------------------------------------------

    @GetMapping("/gap-analysis")
    public ResponseEntity<java.util.List<EquipmentGapAnalysis>> listGapAnalyses(
            @RequestParam("projectId") String projectId) {
        return ResponseEntity.ok(equipmentService.listGapAnalyses(projectId));
    }

    @PostMapping("/gap-analysis")
    public ResponseEntity<EquipmentGapAnalysis> startGapAnalysis(
            @RequestBody GapAnalysisRequest req) {
        EquipmentGapAnalysis saved = equipmentService.startGapAnalysis(
                req.projectId(), req.strategy(), req.requestedBy());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PostMapping("/gap-analysis/{id}/complete")
    public ResponseEntity<EquipmentGapAnalysis> completeGapAnalysis(
            @PathVariable String id,
            @RequestBody(required = false) CompleteGapRequest req) {
        String status = req == null ? null : req.status();
        String error  = req == null ? null : req.error();
        return equipmentService.completeGapAnalysis(id, status, error)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ------------------------------------------------------------------
    // DTO-рекорды
    // ------------------------------------------------------------------

    public record ToolingRequest(String toolingId, String compatibility, String notes) {}
    public record GapAnalysisRequest(String projectId, String strategy, String requestedBy) {}
    public record CompleteGapRequest(String status, String error) {}
}