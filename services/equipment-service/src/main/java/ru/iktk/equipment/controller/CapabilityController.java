package ru.iktk.equipment.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.iktk.equipment.service.CapabilityMatchingService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/equipment")
public class CapabilityController {

    private final CapabilityMatchingService matchingService;

    public CapabilityController(CapabilityMatchingService matchingService) {
        this.matchingService = matchingService;
    }

    @PostMapping("/capabilities/matching")
    public ResponseEntity<Map<String, Object>> findMatching(
            @RequestParam(value = "operationType", required = false) String operationType,
            @RequestParam(value = "requiredAccuracy", required = false) String requiredAccuracy,
            @RequestParam(value = "maxDimensions", required = false) String maxDimensions,
            @RequestParam(value = "material", required = false) String material,
            @RequestParam(value = "requiredCnc", required = false) String requiredCnc,
            @RequestParam(value = "requiredPowerKw", required = false) java.math.BigDecimal requiredPowerKw,
            @RequestParam(value = "requiredForceKn", required = false) String requiredForceKn) {
        java.util.Map<String, Object> requirement = new java.util.LinkedHashMap<>();
        requirement.put("operationType", operationType);
        requirement.put("requiredAccuracy", requiredAccuracy);
        requirement.put("maxDimensions", maxDimensions);
        requirement.put("material", material);
        requirement.put("requiredCnc", requiredCnc);
        requirement.put("requiredPowerKw", requiredPowerKw);
        requirement.put("requiredForceKn", requiredForceKn);
        Optional<Map<String, Object>> result = matchingService.findMatching(requirement);
        return result.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/capabilities/alternatives")
    public ResponseEntity<List<Map<String, Object>>> findAlternatives(
            @RequestParam("operationType") String operationType,
            @RequestParam(value = "requiredAccuracy", required = false) String requiredAccuracy,
            @RequestBody(required = false) List<Map<String, Object>> candidates) {
        return ResponseEntity.ok(matchingService.findAlternatives(operationType, requiredAccuracy, candidates));
    }

    @PostMapping("/assign")
    public ResponseEntity<Void> assign(
            @RequestParam("projectId") String projectId,
            @RequestParam("equipmentId") String equipmentId,
            @RequestParam(value = "assignedBy", required = false) String assignedBy) {
        matchingService.assign(projectId, equipmentId, assignedBy);
        return ResponseEntity.noContent().build();
    }
}