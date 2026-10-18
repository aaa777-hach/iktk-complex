package ru.iktk.equipment.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/equipment")
public class EquipmentController {

    private static final Logger log = LoggerFactory.getLogger(EquipmentController.class);

    @GetMapping(value = "/is-method-available")
    public boolean isMethodAvailable(
            @RequestParam("projectId") String projectId,
            @RequestParam("method") String method) {

        log.info("isMethodAvailable: projectId={}, method={}", projectId, method);

        if (method == null || method.isBlank()) {
            return false;
        }

        boolean available = switch (method.toUpperCase()) {
            case "SAND_CASTING"       -> true;
            case "INVESTMENT_CASTING" -> true;
            case "DIE_CASTING"        -> true;
            case "GRAVITY_CASTING"    -> true;
            default -> false;
        };

        log.info("isMethodAvailable done: projectId={}, method={}, available={}",
                projectId, method, available);
        return available;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "equipment-service"
        ));
    }
}